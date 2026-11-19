import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";

const appDir = "D:\\docklet\\apps\\dsh-console";
const sdkPath = "D:\\docklet\\shell\\app\\src\\main\\assets\\docklet-sdk.js";
const chrome = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const httpPort = 8799, cdpPort = 9340;
const relayBase = "http://127.0.0.1:8788";
const token = fs.readFileSync(`${process.env.USERPROFILE}/.dsh/phone-relay/token`, "utf8").trim();
const session = process.argv[2] || "session-15210c8b-6f8c-4bb0-9b27-23d9d3ddfb97";
const profile = "D:\\docklet\\.tmp\\chrome-expand-anchor";
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

  const longReply = await ev(`(() => {
    const btn = document.querySelector('.v3expand');
    if (!btn) return null;
    const row = btn.closest('.v3row');
    const bubble = row.querySelector('.v3bubble');
    btn.scrollIntoView({ block: 'center' });
    const before = bubble.getBoundingClientRect().top;
    const scrollBefore = document.querySelector('#list').scrollTop;
    btn.click();
    return JSON.stringify({ before, scrollBefore });
  })()`);
  if (!longReply) {
    console.log("SKIP long reply (no .v3expand in this session)");
  } else {
    await sleep(150);
    const after = JSON.parse(await ev(`(() => {
      const btn = document.querySelector('.v3expand');
      const bubble = btn.closest('.v3row').querySelector('.v3bubble');
      return JSON.stringify({ top: bubble.getBoundingClientRect().top, scroll: document.querySelector('#list').scrollTop });
    })()`));
    const start = JSON.parse(longReply);
    const stable = Math.abs(after.top - start.before) <= 1;
    if (!stable) failed += 1;
    console.log(stable ? "PASS" : "FAIL", "long reply keeps its first line when expanded", JSON.stringify({ before: start.before, after: after.top, scrollBefore: start.scrollBefore, scrollAfter: after.scroll }));
  }

  const details = await ev(`(async () => {
    const summary = document.querySelector('.v3process summary');
    if (!summary) return null;
    summary.scrollIntoView({ block: 'center' });
    await new Promise((r) => requestAnimationFrame(r));
    const list = document.querySelector('#list');
    const before = summary.getBoundingClientRect().top;
    const scrollBefore = list.scrollTop;
    window.__dbg = [];
    summary.addEventListener('toggle', () => window.__dbg.push(['toggle', summary.getBoundingClientRect().top, list.scrollTop]));
    window.__fold = summary;
    window.__mut = 0;
    new MutationObserver((list) => { window.__mut += list.length; }).observe(document.querySelector('#rows'), { childList: true, subtree: false });
    summary.click();
    await new Promise((r) => setTimeout(r, 120));
    return JSON.stringify({ before, scrollBefore, afterTop: summary.getBoundingClientRect().top, afterScroll: list.scrollTop, dbg: window.__dbg });
  })()`);
  if (!details) {
    console.log("SKIP process fold (none in this session)");
  } else {
    await sleep(150);
    const after = JSON.parse(await ev(`(() => {
      const summary = window.__fold;
      const list = document.querySelector('#list');
      return JSON.stringify({ top: summary ? summary.getBoundingClientRect().top : null, connected: !!(summary && summary.isConnected), count: document.querySelectorAll('.v3process').length, open: document.querySelectorAll('.v3process[open]').length, scroll: list.scrollTop, mut: window.__mut, hidden: document.hidden });
    })()`));
    const start = JSON.parse(details);
    const stable = Math.abs((after.scroll || 0) - start.scrollBefore) <= 1 && (after.open || 0) >= 1;
    if (!stable) failed += 1;
    console.log(stable ? "PASS" : "FAIL", "process fold keeps its summary in place", JSON.stringify({ before: start.before, after, start }));
  }
} finally {
  child.kill();
  server.close();
}
process.exit(failed ? 1 : 0);
