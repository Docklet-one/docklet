import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";

const appDir = "D:\\docklet\\apps\\dsh-console";
const sdkPath = "D:\\docklet\\shell\\app\\src\\main\\assets\\docklet-sdk.js";
const chrome = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const httpPort = 8806, cdpPort = 9347;
const relayBase = "http://127.0.0.1:8788";
const token = fs.readFileSync(`${process.env.USERPROFILE}/.dsh/phone-relay/token`, "utf8").trim();
const session = "session-window-probe";
const profile = "D:\\docklet\\.tmp\\chrome-window";
const origin = `http://127.0.0.1:${httpPort}`;
const messages = [];
for (let i = 0; i < 35; i += 1) {
  messages.push({ id: "u" + i, role: "user", text: "q" + i, at: 1700000000000 + i * 1000, seq: i * 2 + 1, revision: i * 2 + 1 });
  messages.push({ id: "a" + i, role: "assistant", text: "a" + i, at: 1700000000000 + i * 1000 + 500, seq: i * 2 + 2, revision: i * 2 + 2 });
}
const cache = {
  session: { id: session, title: "window" },
  messages,
  prompts: [],
  deliverables: [],
  processes: [],
  activity: {},
  sync: { cursor: "seq:0", seen: [], oldestSeq: 1, hasMore: true }
};
const seed = `<!doctype html><meta charset="utf-8"><script>
localStorage.clear();
localStorage.setItem("v3.lan", ${JSON.stringify(relayBase)});
localStorage.setItem("v3.base", ${JSON.stringify(relayBase)});
localStorage.setItem("v3.token", ${JSON.stringify(token)});
localStorage.setItem("v3.session", ${JSON.stringify(session)});
localStorage.setItem(${JSON.stringify("v3.cache." + session)}, ${JSON.stringify(JSON.stringify(cache))});
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
  for (let i = 0; i < 120; i += 1) { if (await ev("!!document.querySelector('#msgs')").catch(() => false)) break; await sleep(300); }
  await sleep(2500);

  const entry = JSON.parse(await ev("JSON.stringify({ rows: document.querySelectorAll('#msgs .v3row[data-mid]').length, info: window.__dockletViewInfo && window.__dockletViewInfo() })"));
  const entryOk = entry.rows > 0 && entry.rows <= 22;
  if (!entryOk) failed += 1;
  console.log(entryOk ? "PASS" : "FAIL", "entry renders the latest 5 turns (auto-fills if the viewport is short)", JSON.stringify(entry));

  await ev("(() => { const l = document.querySelector('#list'); l.dispatchEvent(new Event('wheel', { bubbles: true })); l.scrollTop = 0; l.dispatchEvent(new Event('scroll')); })()");
  await sleep(400);
  await ev("document.querySelector('#list').scrollTop = 0");
  await sleep(2500);
  const expanded = JSON.parse(await ev("JSON.stringify({ info: window.__dockletViewInfo && window.__dockletViewInfo(), rows: document.querySelectorAll('#msgs .v3row[data-mid]').length })"));
  const expandOk = expanded.rows > entry.rows;
  if (!expandOk) failed += 1;
  console.log(expandOk ? "PASS" : "FAIL", "scrolling up widens the window by 5 turns", JSON.stringify({ entry: entry.rows, expanded }));

  await ev("new Promise((resolve) => { const l = document.querySelector('#list'); l.scrollTop = l.scrollHeight; window.setTimeout(() => { l.dispatchEvent(new Event('scroll')); resolve(true); }, 150); })");
  await sleep(2500);
  const trimmed = JSON.parse(await ev("JSON.stringify({ rows: document.querySelectorAll('#msgs .v3row[data-mid]').length, info: window.__dockletViewInfo && window.__dockletViewInfo() })"));
  const trimOk = trimmed.rows <= 22;
  if (!trimOk) failed += 1;
  console.log(trimOk ? "PASS" : "FAIL", "returning to the bottom trims back to 5 turns", JSON.stringify({ expanded: expanded.rows, trimmed: trimmed.rows, info: trimmed.info }));
} finally {
  child.kill();
  server.close();
}
process.exit(failed ? 1 : 0);
