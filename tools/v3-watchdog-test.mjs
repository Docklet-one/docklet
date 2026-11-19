import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";

// Reproduce the watchdog path: a turn is sent, /send succeeds, but no SSE
// turn events arrive. The local placeholder must stay, then after the
// no-first-event threshold the watchdog must clear it and toast.
const here = path.dirname(fileURLToPath(import.meta.url));
const appDir = path.resolve(here, "..", "apps", "dsh-console");
const sdkPath = path.resolve(here, "..", "shell", "app", "src", "main", "assets", "docklet-sdk.js");
const chrome = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const httpPort = 8826, cdpPort = 9366;
const token = fs.readFileSync(`${process.env.USERPROFILE}/.dsh/phone-relay/token`, "utf8").trim();
const relay = "http://127.0.0.1:8788";
const origin = `http://127.0.0.1:${httpPort}`;
const session = "session-15210c8b-6f8c-4bb0-9b27-23d9d3ddfb97";
const seed = `<!doctype html><meta charset="utf-8"><script>localStorage.clear();localStorage.setItem("v3.lan","${relay}");localStorage.setItem("v3.base","${relay}");localStorage.setItem("v3.token","${token}");localStorage.setItem("v3.session","${session}");location.replace("/index.html");</script>`;
const server = http.createServer((req, res) => {
  const url = new URL(req.url, origin);
  if (url.pathname === "/seed.html") { res.writeHead(200, { "Content-Type": "text/html" }); res.end(seed); return; }
  if (url.pathname === "/__docklet/sdk.js") { res.writeHead(200, { "Content-Type": "text/javascript" }); res.end(fs.readFileSync(sdkPath)); return; }
  const rel = decodeURIComponent(url.pathname).replace(/^\/+/, "") || "index.html";
  const file = path.join(appDir, rel);
  if (!file.startsWith(appDir) || !fs.existsSync(file)) { res.writeHead(404); res.end("not found"); return; }
  res.writeHead(200, { "Content-Type": rel.endsWith(".js") ? "text/javascript" : rel.endsWith(".css") ? "text/css" : "text/html" }); res.end(fs.readFileSync(file));
});
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const child = spawn(chrome, ["--headless=new", `--remote-debugging-port=${cdpPort}`, "--user-data-dir=D:\\docklet\\.tmp\\chrome-watchdog", "--no-first-run", "--disable-gpu", "--hide-scrollbars", "about:blank"], { stdio: "ignore" });
let failed = 0;
const check = (name, pass, detail = "") => { if (!pass) failed += 1; console.log(pass ? "PASS" : "FAIL", name, detail); };
try {
  await new Promise((r) => server.listen(httpPort, "127.0.0.1", r));
  for (let i = 0; i < 80; i += 1) { try { const r = await fetch(`http://127.0.0.1:${cdpPort}/json/list`); if (r.ok) break; } catch {} await sleep(200); }
  const target = (await (await fetch(`http://127.0.0.1:${cdpPort}/json/list`)).json()).find((t) => t.type === "page");
  const ws = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((res, rej) => { ws.addEventListener("open", res, { once: true }); ws.addEventListener("error", rej, { once: true }); });
  let id = 0; const pending = new Map();
  ws.addEventListener("message", (e) => { const m = JSON.parse(e.data); if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); } });
  const send = (method, params = {}) => new Promise((res) => { const n = ++id; pending.set(n, res); ws.send(JSON.stringify({ id: n, method, params })); });
  const ev = async (expr) => { const r = await send("Runtime.evaluate", { expression: expr, returnByValue: true, awaitPromise: true }); return r.result?.result?.value; };
  await send("Page.enable");
  await send("Page.addScriptToEvaluateOnNewDocument", { source: "window.EventSource = class { constructor() { this.readyState = 0; } close() { this.readyState = 2; } addEventListener() {} removeEventListener() {} };" });
  await send("Page.navigate", { url: `${origin}/seed.html` });
  for (let i = 0; i < 100; i += 1) { if (await ev("!!document.querySelector('#text')")) break; await sleep(200); }
  await sleep(1000);

  const started = Date.now();
  const early = JSON.parse(await ev(`(() => {
    const box = document.querySelector('#text');
    box.value = 'watchdog repro';
    box.dispatchEvent(new Event('input', { bubbles: true }));
    const origFetch = window.fetch;
    window.fetch = (url, opts) => String(url).includes('/send')
      ? Promise.resolve(new Response('{"ok":true}', { status: 200, headers: { 'Content-Type': 'application/json' } }))
      : origFetch(url, opts);
    document.querySelector('#send').click();
    return JSON.stringify({ work: document.querySelectorAll('.v3work').length });
  })()`));
  check("placeholder appears before any event", early.work === 1, JSON.stringify(early));

  await sleep(5000);
  const five = JSON.parse(await ev(`JSON.stringify({ work: document.querySelectorAll('.v3work').length, wd: (window.__v3wd||[]).filter(x=>x.kind==='trigger').length })`));
  check("no watchdog before 25s", five.work === 1 && five.wd === 0, JSON.stringify(five));

  await sleep(23000);
  const elapsed = Math.round((Date.now() - started) / 1000);
  const late = JSON.parse(await ev(`JSON.stringify({
    work: document.querySelectorAll('.v3work').length,
    toast: document.querySelector('#toast') ? document.querySelector('#toast').textContent : '',
    triggers: (window.__v3wd||[]).filter(x=>x.kind==='trigger')
  })`));
  check("watchdog clears placeholder after threshold", late.work === 0, `elapsed=${elapsed}s ${JSON.stringify(late)}`);
  check("toast says still waiting", late.toast.includes("仍在等待电脑端响应"), late.toast);
  check("trigger reason recorded", late.triggers.some((x) => x.reason === "no-first-event"), JSON.stringify(late.triggers));
  ws.close();
} finally {
  child.kill();
  server.close();
}
console.log(failed ? `${failed} failed` : "all watchdog cases passed");
process.exit(failed ? 1 : 0);
