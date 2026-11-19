import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";

// Regression test: the optimistic assistant bubble must appear in the same
// click task, before /send resolves. We mock /send with a delay, so a slow
// relay cannot hide a missing local placeholder.
const here = path.dirname(fileURLToPath(import.meta.url));
const appDir = path.resolve(here, "..", "apps", "dsh-console");
const sdkPath = path.resolve(here, "..", "shell", "app", "src", "main", "assets", "docklet-sdk.js");
const chrome = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const httpPort = 8825, cdpPort = 9365;
const profile = "D:\\docklet\\.tmp\\chrome-pending-test";
const token = fs.readFileSync(`${process.env.USERPROFILE}/.dsh/phone-relay/token`, "utf8").trim();
const relay = "http://127.0.0.1:8788";
const origin = `http://127.0.0.1:${httpPort}`;
let session = "session-15210c8b-6f8c-4bb0-9b27-23d9d3ddfb97";
try {
  const sessionsRes = await fetch(relay + "/sessions", { headers: { Authorization: "Bearer " + token } });
  const sessionsData = await sessionsRes.json();
  const idle = (sessionsData.sessions || []).find((item) => item && item.id && !item.running);
  if (idle) session = idle.id;
} catch (_) {}
const seed = `<!doctype html><meta charset="utf-8"><script>localStorage.clear();localStorage.setItem("v3.lan","${relay}");localStorage.setItem("v3.base","${relay}");localStorage.setItem("v3.token","${token}");localStorage.setItem("v3.session","${session}");location.replace("/index.html");</script>`;
const mime = (rel) => rel.endsWith(".js") ? "text/javascript" : rel.endsWith(".css") ? "text/css" : "text/html; charset=utf-8";
const server = http.createServer((req, res) => {
  const url = new URL(req.url, origin);
  if (url.pathname === "/seed.html") { res.writeHead(200, { "Content-Type": "text/html", "Cache-Control": "no-store" }); res.end(seed); return; }
  if (url.pathname === "/__docklet/sdk.js") { res.writeHead(200, { "Content-Type": "text/javascript" }); res.end(fs.readFileSync(sdkPath)); return; }
  const rel = decodeURIComponent(url.pathname).replace(/^\/+/, "") || "index.html";
  const file = path.join(appDir, rel);
  if (!file.startsWith(appDir) || !fs.existsSync(file)) { res.writeHead(404); res.end("not found"); return; }
  res.writeHead(200, { "Content-Type": mime(rel), "Cache-Control": "no-store" }); res.end(fs.readFileSync(file));
});
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
try { fs.rmSync(profile, { recursive: true, force: true }); } catch (_) {}
const child = spawn(chrome, ["--headless=new", `--remote-debugging-port=${cdpPort}`, `--user-data-dir=${profile}`, "--no-first-run", "--no-default-browser-check", "--disable-gpu", "--hide-scrollbars", "about:blank"], { stdio: "ignore" });
let failed = 0;
let timedOut = false;
function check(name, pass, detail = "") {
  if (!pass) failed += 1;
  console.log(pass ? "PASS" : "FAIL", name, detail);
}
const hardStop = setTimeout(() => {
  timedOut = true;
  console.error("pending test hard timeout");
  try { child.kill(); } catch (_) {}
  try { server.closeAllConnections?.(); server.close(); } catch (_) {}
  process.exit(2);
}, 45000);
try {
  await new Promise((r) => server.listen(httpPort, "127.0.0.1", r));
  let ready = false;
  for (let i = 0; i < 80; i += 1) {
    if (child.exitCode != null) break;
    try { const r = await fetch(`http://127.0.0.1:${cdpPort}/json/list`); if (r.ok) { ready = true; break; } } catch {}
    await sleep(200);
  }
  check("chrome started", ready);
  if (!ready) throw new Error("chrome not ready");
  const target = (await (await fetch(`http://127.0.0.1:${cdpPort}/json/list`)).json()).find((t) => t.type === "page");
  const ws = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((res, rej) => { ws.addEventListener("open", res, { once: true }); ws.addEventListener("error", rej, { once: true }); });
  let id = 0; const pending = new Map();
  ws.addEventListener("message", (e) => { const m = JSON.parse(e.data); if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); } });
  const send = (method, params = {}) => new Promise((res) => { const n = ++id; pending.set(n, res); ws.send(JSON.stringify({ id: n, method, params })); });
  const ev = async (expr) => { const r = await send("Runtime.evaluate", { expression: expr, returnByValue: true, awaitPromise: true }); return r.result?.result?.value; };
  await send("Page.enable");
  await send("Page.addScriptToEvaluateOnNewDocument", {
    source: "window.EventSource = class { constructor() { this.readyState = 0; } close() { this.readyState = 2; } addEventListener() {} removeEventListener() {} };"
  });
  await send("Page.navigate", { url: `${origin}/seed.html` });
  for (let i = 0; i < 100; i += 1) { if (await ev("!!document.querySelector('#text')")) break; await sleep(200); }
  await sleep(1200);

  const result = JSON.parse(await ev(`(async () => {
    const box = document.querySelector('#text');
    box.value = 'pending bubble test';
    box.dispatchEvent(new Event('input', { bubbles: true }));
    const origFetch = window.fetch;
    window.fetch = (url, opts) => String(url).includes('/send')
      ? new Promise((resolve) => setTimeout(() => resolve(new Response('{"ok":true}', { status: 200, headers: { 'Content-Type': 'application/json' } })), 1500))
      : origFetch(url, opts);
    const t0 = performance.now();
    document.querySelector('#send').click();
    const elapsed = Math.round(performance.now() - t0);
    const work = document.querySelectorAll('.v3work');
    const bubble = work[0] && work[0].querySelector('.v3bubble');
    const dots = work[0] ? work[0].querySelectorAll('.v3thinking i').length : 0;
    const dotsHidden = work[0] && work[0].querySelector('.v3thinking') ? work[0].querySelector('.v3thinking').hidden : null;
    const label = work[0] && work[0].querySelector('[data-work-label]');
    const time = work[0] && work[0].querySelector('[data-work-time]');
    return JSON.stringify({
      clickMs: elapsed,
      work: work.length,
      bubble: !!bubble,
      dots,
      dotsHidden,
      label: label && label.textContent,
      time: time ? time.textContent : '',
      pendingBubble: document.querySelectorAll('.v3work').length
    });
  })()`));
  check("bubble exists in the same click task", result.work === 1 && result.bubble === true, JSON.stringify(result));
  check("three breathing dots are present", result.dots === 3 && result.dotsHidden === false, JSON.stringify({ dots: result.dots, hidden: result.dotsHidden }));
  check("label is thinking and timer exists", result.label === "正在思考" && /\d+s/.test(result.time || ""), JSON.stringify({ label: result.label, time: result.time }));
  check("click handler stays under 50ms", result.clickMs <= 50, `${result.clickMs}ms`);

  await sleep(400);
  const holding = JSON.parse(await ev(`JSON.stringify({ work: document.querySelectorAll('.v3work').length, dots: document.querySelectorAll('.v3work .v3thinking i').length })`));
  check("placeholder holds while /send is pending", holding.work === 1 && holding.dots === 3, JSON.stringify(holding));

  await sleep(1400);
  const after = JSON.parse(await ev(`JSON.stringify({ work: document.querySelectorAll('.v3work').length, users: document.querySelectorAll('.v3row.me[data-mid]').length })`));
  check("no duplicate working bubble after send resolves", after.work <= 1, JSON.stringify(after));

  ws.close();
} catch (error) {
  failed += 1;
  console.error("pending test error:", error && error.message || error);
} finally {
  clearTimeout(hardStop);
  try { child.kill(); } catch (_) {}
  try { server.closeAllConnections?.(); server.close(); } catch (_) {}
}
if (timedOut) process.exit(2);
console.log(failed ? `${failed} failed` : "all pending-bubble cases passed");
process.exit(failed ? 1 : 0);
