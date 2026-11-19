import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";

const appDir = "D:\\docklet\\apps\\dsh-console";
const sdkPath = "D:\\docklet\\shell\\app\\src\\main\\assets\\docklet-sdk.js";
const chrome = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const httpPort = 8805, cdpPort = 9346;
const relayBase = "http://127.0.0.1:8788";
const token = fs.readFileSync(`${process.env.USERPROFILE}/.dsh/phone-relay/token`, "utf8").trim();
const session = process.argv[2] || "session-cf63f7bb-ca89-449a-b094-4b1ac0c59175";
const profile = "D:\\docklet\\.tmp\\chrome-fastrender";
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
  await send("Page.enable");
  await send("Page.navigate", { url: `${origin}/seed.html` });
  for (let i = 0; i < 120; i += 1) { if (await ev("!!document.querySelector('#connState')").catch(() => false)) break; await sleep(300); }
  await sleep(2500);

  const before = JSON.parse(await ev("(() => { window.__textNode = document.querySelector('#text'); window.__textNode.focus(); const s = window.__v3Stats || { fast: 0, full: 0 }; return JSON.stringify({ fast: s.fast, full: s.full, active: document.activeElement === window.__textNode }); })()"));
  await ev("window.__dockletForceRender()");
  await sleep(400);
  const after = JSON.parse(await ev("(() => { const s = window.__v3Stats || { fast: 0, full: 0 }; const text = document.querySelector('#text'); return JSON.stringify({ fast: s.fast, full: s.full, sameNode: text === window.__textNode, active: document.activeElement === text, msgs: document.querySelectorAll('#msgs').length }); })()"));
  const ok = before.active && after.sameNode && after.active && after.fast === before.fast + 1 && after.full === before.full && after.msgs === 1;
  const attachInfo = JSON.parse(await ev("(() => { const input = document.querySelector('#attachmentFile'); const dt = new DataTransfer(); dt.items.add(new File(['probe'], 'probe.txt', { type: 'text/plain' })); input.files = dt.files; input.dispatchEvent(new Event('change', { bubbles: true })); return JSON.stringify({ started: true }); })()"));
  await sleep(2500);
  const chips = JSON.parse(await ev("JSON.stringify({ chips: document.querySelectorAll('.v3attach').length, disabled: document.querySelector('#pickAttachment') ? document.querySelector('#pickAttachment').disabled : null })"));
  const attachOk = chips.chips >= 1 && chips.disabled === false;
  if (!attachOk) failed += 1;
  console.log(attachOk ? "PASS" : "FAIL", "attachment chip appears and the plus button stays usable", JSON.stringify({ attachInfo, chips }));
  if (!ok) failed += 1;
  console.log(ok ? "PASS" : "FAIL", "chat updates keep the composer node and skip full render", JSON.stringify({ before, after }));
} finally {
  child.kill();
  server.close();
}
process.exit(failed ? 1 : 0);
