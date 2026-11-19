import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";

const appDir = "D:\\docklet\\apps\\dsh-console";
const sdkPath = "D:\\docklet\\shell\\app\\src\\main\\assets\\docklet-sdk.js";
const chrome = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const httpPort = 8803, cdpPort = 9344;
const relayBase = "http://127.0.0.1:8788";
const token = fs.readFileSync(`${process.env.USERPROFILE}/.dsh/phone-relay/token`, "utf8").trim();
const session = process.argv[2] || "session-5a3a510a-7b9b-4970-9d63-62a4bb9a9667";
const profile = "D:\\docklet\\.tmp\\chrome-pull";
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
  await sleep(3500);
  const info = JSON.parse(await ev(`JSON.stringify({
    limit: !!document.querySelector('.v3limit'),
    ptr: !!document.querySelector('#ptr'),
    canPullFromState: (() => { return true; })()
  })`));
  // The limit notice is only rendered once 30 turns are loaded; on such a page
  // the pull indicator must be gone.
  const ok = !info.limit || !info.ptr;
  if (!ok) failed += 1;
  console.log(ok ? "PASS" : "FAIL", "no pull gesture once the 30-turn limit is shown", JSON.stringify(info));
} finally {
  child.kill();
  server.close();
}
process.exit(failed ? 1 : 0);
