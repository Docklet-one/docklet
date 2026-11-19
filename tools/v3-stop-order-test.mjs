import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";

const appDir = "D:\\docklet\\apps\\dsh-console";
const sdkPath = "D:\\docklet\\shell\\app\\src\\main\\assets\\docklet-sdk.js";
const chrome = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const httpPort = 8802, cdpPort = 9343;
const relayBase = "http://127.0.0.1:8788";
const token = fs.readFileSync(`${process.env.USERPROFILE}/.dsh/phone-relay/token`, "utf8").trim();
const session = process.argv[2] || "session-15210c8b-6f8c-4bb0-9b27-23d9d3ddfb97";
const stopKey = "v3.stops." + session;
const profile = "D:\\docklet\\.tmp\\chrome-stoporder";
const origin = `http://127.0.0.1:${httpPort}`;
const seed = `<!doctype html><meta charset="utf-8"><script>
localStorage.clear();
localStorage.setItem("v3.lan", ${JSON.stringify(relayBase)});
localStorage.setItem("v3.base", ${JSON.stringify(relayBase)});
localStorage.setItem("v3.token", ${JSON.stringify(token)});
localStorage.setItem("v3.session", ${JSON.stringify(session)});
location.replace("/index.html");
</script>`;
const mime = (rel) => rel.endsWith(".js") ? "text/javascript" : rel.endsWith(".json") ? "application/json" : "text/html; charset=utf-8";
const server = http.createServer((req, res) => {
  const url = new URL(req.url, origin);
  if (url.pathname === "/seed.html") { res.writeHead(200, { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store" }); res.end(seed); return; }
  if (url.pathname === "/__docklet/sdk.js") { res.writeHead(200, { "Content-Type": "text/javascript", "Cache-Control": "no-store" }); res.end(fs.readFileSync(sdkPath)); return; }
  const rel = decodeURIComponent(url.pathname).replace(/^\/+/, "") || "index.html";
  const file = path.join(appDir, rel);
  if (!file.startsWith(appDir) || !fs.existsSync(file)) { res.writeHead(404); res.end("no"); return; }
  res.writeHead(200, { "Content-Type": mime(rel), "Cache-Control": "no-store" });
  res.end(fs.readFileSync(file));
});
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
fs.rmSync(profile, { recursive: true, force: true });
fs.mkdirSync(profile, { recursive: true });
await new Promise((r) => server.listen(httpPort, "127.0.0.1", r));
const child = spawn(chrome, ["--headless=new", `--remote-debugging-port=${cdpPort}`, `--user-data-dir=${profile}`, "--no-first-run", "--no-default-browser-check", "--disable-gpu", "--window-size=420,900", "about:blank"], { stdio: "ignore" });
let failed = 0;
try {
  for (let i = 0; i < 100; i += 1) { try { const r = await fetch(`http://127.0.0.1:${cdpPort}/json/version`); if (r.ok) break; } catch {} await sleep(200); }
  const page = (await (await fetch(`http://127.0.0.1:${cdpPort}/json/list`)).json()).find((t) => t.type === "page");
  const ws = new WebSocket(page.webSocketDebuggerUrl);
  await new Promise((res, rej) => { ws.addEventListener("open", res, { once: true }); ws.addEventListener("error", rej, { once: true }); });
  let id = 0; const pending = new Map();
  ws.addEventListener("message", (e) => { const m = JSON.parse(e.data); if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); } });
  const send = (method, params = {}) => new Promise((res) => { const n = ++id; pending.set(n, res); ws.send(JSON.stringify({ id: n, method, params })); });
  const ev = async (expr) => { const r = await send("Runtime.evaluate", { expression: expr, returnByValue: true, awaitPromise: true }); return r.result?.result?.value; };
  const rowExpr = "JSON.stringify([...document.querySelectorAll('#rows > .v3row')].map((n) => ({ seq: Number(n.dataset.seq) || 0, stop: n.classList.contains('v3stoprow') })))";

  await send("Page.enable");
  await send("Page.navigate", { url: `${origin}/seed.html` });
  for (let i = 0; i < 120; i += 1) { if (await ev("!!document.querySelector('#connState')").catch(() => false)) break; await sleep(300); }
  await sleep(3000);

  const rows = JSON.parse(await ev(rowExpr));
  const seqs = rows.filter((r) => r.seq > 0).map((r) => r.seq);
  const midSeq = seqs.length >= 3 ? seqs[2] : (seqs[0] || 1);
  const stopJson = JSON.stringify([{ id: "probe-stop", turn: 1, seq: midSeq, at: Date.now(), by: "pc" }]);
  await ev("localStorage.setItem(" + JSON.stringify(stopKey) + ", " + JSON.stringify(stopJson) + ")");
  await ev("window.__dockletSwitchSession(" + JSON.stringify(session) + ")");
  await sleep(2500);

  const after = JSON.parse(await ev(rowExpr));
  const index = after.findIndex((r) => r.stop);
  const beforeSeq = index > 0 ? after.slice(0, index).filter((r) => r.seq > 0).pop()?.seq ?? 0 : 0;
  const afterSeq = index >= 0 ? after.slice(index + 1).find((r) => r.seq > 0)?.seq ?? 0 : 0;
  const ordered = index >= 0 && beforeSeq <= midSeq && (afterSeq === 0 || afterSeq > midSeq);
  if (!ordered) failed += 1;
  console.log(ordered ? "PASS" : "FAIL", "stop notice sits where it happened", JSON.stringify({ midSeq, index, beforeSeq, afterSeq, total: after.length }));
} finally {
  child.kill();
  server.close();
}
process.exit(failed ? 1 : 0);
