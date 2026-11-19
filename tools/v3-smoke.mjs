// Headless-Chrome smoke test for the V3 module page.
//
// Covers two things that cannot be checked with a pure unit test:
//   1. landing page (0.3.52): unconfigured -> "连接 DSH", configured -> chat
//   2. watch URL (0.3.53): backgroundMessages.subscribe receives mode=turn
//
// The page is served from apps/dsh-console with the real SDK
// (shell/app/src/main/assets/docklet-sdk.js) in mock mode, and talks to the
// real phone relay when a token is available. In mock mode the SDK does not
// persist anything, so localStorage is seeded by /seed.html before the page
// boots.
//
// Usage:
//   node tools/v3-smoke.mjs
//   DOCKLET_RELAY_TOKEN=... DOCKLET_RELAY_BASE=http://192.168.2.6:8788 node tools/v3-smoke.mjs
//
// Token fallback: %USERPROFILE%\.dsh\phone-relay\token (dev machine).

import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const appDir = path.join(here, "..", "apps", "dsh-console");
const sdkPath = path.join(
  here, "..", "shell", "app", "src", "main", "assets", "docklet-sdk.js"
);
const chrome = process.env.CHROME_BIN
  || "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const httpPort = Number(process.env.SMOKE_PORT || 8792);
const cdpPort = Number(process.env.SMOKE_CDP_PORT || 9333);
const relayBase = (process.env.DOCKLET_RELAY_BASE || "http://192.168.2.6:8788")
  .replace(/\/+$/, "");
const origin = `http://127.0.0.1:${httpPort}`;
const profile = path.join(here, "..", ".tmp", "chrome-v3-smoke");

let token = process.env.DOCKLET_RELAY_TOKEN || "";
if (!token) {
  const file = path.join(process.env.USERPROFILE || "", ".dsh", "phone-relay", "token");
  try { token = fs.readFileSync(file, "utf8").trim(); } catch (_) { token = ""; }
}

const mime = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".css": "text/css; charset=utf-8",
};

const seedPage = `<!doctype html><meta charset="utf-8"><script>
const q = new URLSearchParams(location.search);
if (q.get("clear") === "1") localStorage.clear();
if (q.get("mode")) {
  localStorage.setItem("v3.lan", ${JSON.stringify(relayBase)});
  localStorage.setItem("v3.base", ${JSON.stringify(relayBase)});
  localStorage.setItem("v3.token", ${JSON.stringify(token)});
  localStorage.setItem("v3.session", q.get("session") || "");
  localStorage.setItem("v3.notify", "1");
  localStorage.setItem("v3.notifyMode", q.get("mode"));
}
location.replace(q.get("next") || "/index.html");
</script>`;

const server = http.createServer((req, res) => {
  const url = new URL(req.url || "/", origin);
  if (url.pathname === "/seed.html") {
    res.writeHead(200, { "Content-Type": mime[".html"], "Cache-Control": "no-store" });
    res.end(seedPage);
    return;
  }
  if (url.pathname === "/__docklet/sdk.js") {
    const body = fs.readFileSync(sdkPath);
    res.writeHead(200, { "Content-Type": mime[".js"], "Cache-Control": "no-store" });
    res.end(body);
    return;
  }
  const rel = decodeURIComponent(url.pathname).replace(/^\/+/, "") || "index.html";
  const file = path.join(appDir, rel);
  if (!file.startsWith(appDir) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
    res.writeHead(404, { "Content-Type": "text/plain" });
    res.end("not found");
    return;
  }
  res.writeHead(200, {
    "Content-Type": mime[path.extname(file)] || "application/octet-stream",
    "Cache-Control": "no-store",
  });
  res.end(fs.readFileSync(file));
});

function startServer() {
  return new Promise((resolve) => server.listen(httpPort, "127.0.0.1", resolve));
}

function stopServer() {
  return new Promise((resolve) => server.close(resolve));
}

async function waitFor(fn, timeoutMs, label) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    try {
      const value = await fn();
      if (value) return value;
    } catch (_) {
      /* keep polling */
    }
    await new Promise((resolve) => setTimeout(resolve, 200));
  }
  throw new Error(`timeout: ${label}`);
}

class Cdp {
  constructor(socket) {
    this.socket = socket;
    this.nextId = 1;
    this.pending = new Map();
    socket.addEventListener("message", (event) => {
      const message = JSON.parse(event.data);
      if (message.id && this.pending.has(message.id)) {
        this.pending.get(message.id)(message);
        this.pending.delete(message.id);
      }
    });
  }

  static async connect(wsUrl) {
    const socket = new WebSocket(wsUrl);
    await new Promise((resolve, reject) => {
      socket.addEventListener("open", resolve, { once: true });
      socket.addEventListener("error", reject, { once: true });
    });
    return new Cdp(socket);
  }

  send(method, params = {}) {
    const id = this.nextId++;
    this.socket.send(JSON.stringify({ id, method, params }));
    return new Promise((resolve) => this.pending.set(id, resolve));
  }

  async evaluate(expression, awaitPromise = false) {
    const result = await this.send("Runtime.evaluate", {
      expression,
      awaitPromise,
      returnByValue: true,
    });
    if (result.result?.exceptionDetails) {
      throw new Error(JSON.stringify(result.result.exceptionDetails).slice(0, 400));
    }
    return result.result?.result?.value;
  }
}

// Wrap background.start as soon as the SDK assigns window.docklet, so the URL
// the module hands to the shell is captured before any native call.
const hook = `(() => {
  window.__errors = [];
  window.addEventListener("error", (event) => window.__errors.push(String(event.message || event.error)));
  window.addEventListener("unhandledrejection", (event) => window.__errors.push("rejection: " + String((event.reason && event.reason.message) || event.reason)));
  window.__frames = [];
  let lastSig = "";
  const sigOf = () => {
    if (document.querySelector(".v3setup")) return "setup";
    if (document.querySelector("#saveServer")) return "settings";
    if (document.querySelector("#connState")) return "chat";
    if (document.querySelector(".v3loading")) return "loading";
    const root = document.getElementById("root");
    return root && root.childElementCount ? "other" : "empty";
  };
  const sample = () => {
    const sig = sigOf();
    if (sig !== lastSig) {
      lastSig = sig;
      window.__frames.push([sig, Math.round(performance.now())]);
    }
  };
  new MutationObserver(sample).observe(document, { childList: true, subtree: true });
  window.addEventListener("DOMContentLoaded", sample);
  let current;
  Object.defineProperty(window, "docklet", {
    configurable: true,
    get() { return current; },
    set(value) {
      current = value;
      try {
        if (value && typeof value.request === "function") {
          const originalRequest = value.request;
          value.request = function (method) {
            (window.__calls = window.__calls || []).push(String(method));
            return originalRequest.apply(this, arguments);
          };
        }
        if (value && value.backgroundMessages && typeof value.backgroundMessages.subscribe === "function") {
          value.backgroundMessages.subscribe = function (options) {
            (window.__bgSubs = window.__bgSubs || []).push(options || {});
            return Promise.resolve({ subscribed: true, mock: true });
          };
        }
      } catch (error) { /* preview only */ }
    }
  });
})();`;

function redact(url) {
  return String(url).replace(/token=[^&]*/, "token=***");
}

const results = [];
function check(name, pass, detail = "") {
  results.push({ name, pass, detail });
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  " + detail : ""}`);
}

async function pickSession() {
  if (!token) return "";
  try {
    const response = await fetch(`${relayBase}/sessions?token=${encodeURIComponent(token)}`);
    if (!response.ok) return "";
    const body = await response.json();
    const sessions = Array.isArray(body.sessions) ? body.sessions : [];
    const idle = sessions.find((item) => item && item.id && item.online && !item.running);
    return String((idle || sessions.find((item) => item && item.id) || {}).id || "");
  } catch (_) {
    return "";
  }
}

async function newPage() {
  const response = await fetch(`http://127.0.0.1:${cdpPort}/json/new?about:blank`, {
    method: "PUT",
  });
  if (!response.ok) throw new Error(`cannot create target: ${response.status}`);
  const target = await response.json();
  const cdp = await Cdp.connect(target.webSocketDebuggerUrl);
  await cdp.send("Page.enable");
  await cdp.send("Runtime.enable");
  await cdp.send("Emulation.setDeviceMetricsOverride", {
    width: 390,
    height: 844,
    deviceScaleFactor: 2,
    mobile: true,
  });
  await cdp.send("Page.addScriptToEvaluateOnNewDocument", { source: hook });
  return { cdp, targetId: target.id };
}

async function shot(cdp, name) {
  const result = await cdp.send("Page.captureScreenshot", { format: "png" });
  const file = path.join(here, "..", "docs", "screenshots", name);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, Buffer.from(result.result.data, "base64"));
  console.log(`      shot: docs/screenshots/${name}`);
}

async function bodyText(cdp) {
  return String(await cdp.evaluate("document.body ? document.body.innerText : ''"));
}

async function closePage(targetId) {
  await fetch(`http://127.0.0.1:${cdpPort}/json/close/${targetId}`).catch(() => {});
}

async function main() {
  if (!fs.existsSync(chrome)) throw new Error(`chrome not found: ${chrome}`);
  fs.rmSync(profile, { recursive: true, force: true });
  fs.mkdirSync(profile, { recursive: true });
  await startServer();
  const child = spawn(
    chrome,
    [
      "--headless=new",
      `--remote-debugging-port=${cdpPort}`,
      `--user-data-dir=${profile}`,
      "--no-first-run",
      "--no-default-browser-check",
      "--disable-gpu",
      "--disable-extensions",
      "--window-size=420,900",
      "about:blank",
    ],
    { stdio: "ignore", detached: false }
  );

  try {
    await waitFor(
      () => fetch(`http://127.0.0.1:${cdpPort}/json/version`).then((r) => r.ok),
      20000,
      "chrome devtools"
    );

    const sessionId = await pickSession();
    console.log(
      `relay: ${relayBase}  token: ${token ? "yes" : "no"}  session: ${sessionId || "-"}`
    );

    // 1. unconfigured -> settings page (no separate connect card)
    {
      const { cdp, targetId } = await newPage();
      try {
        await cdp.send("Page.navigate", {
          url: `${origin}/seed.html?clear=1&next=/index.html`,
        });
        await waitFor(
          () => cdp.evaluate("document.readyState === 'complete' && !!document.querySelector('#saveServer')"),
          15000,
          "settings page render"
        ).catch(async (error) => {
          console.log("      diagnostics: " + await cdp.evaluate(`JSON.stringify({
            errors: window.__errors || [],
            body: (document.body.innerText || '').slice(0, 160),
            html: document.body.innerHTML.slice(0, 200)
          })`));
          throw error;
        });
        const text = await bodyText(cdp);
        check("unconfigured lands on 设置", text.includes("设置") && text.includes("服务器"));
        check("connect card is gone", !text.includes("连接 DSH"));
        check("settings has no back button", !(await cdp.evaluate("!!document.querySelector('#backChat')")));
        const frames = JSON.parse(await cdp.evaluate("JSON.stringify(window.__frames || [])"));
        const sigs = frames.map((frame) => frame[0]);
        check("unconfigured never paints 连接 DSH", !sigs.includes("setup"), sigs.join(" -> "));
        check("unconfigured never paints chat", !sigs.includes("chat"), sigs.join(" -> "));
        await shot(cdp, "v3-landing-settings.png");
      } finally {
        await closePage(targetId);
      }
    }

    if (!token || !sessionId) {
      console.log("SKIP  connect/mode cases (no relay token or session)");
    } else {
      // 2. first connect from settings: fill address + token, save, land on chat
      {
        const { cdp, targetId } = await newPage();
        try {
          await cdp.send("Page.navigate", {
            url: `${origin}/seed.html?clear=1&next=/index.html`,
          });
          await waitFor(
            () => cdp.evaluate("!!document.querySelector('#saveServer')"),
            15000,
            "settings page render (first connect)"
          );
          await cdp.evaluate(`(() => {
            document.querySelector('#setLan').value = ${JSON.stringify(relayBase)};
            document.querySelector('#setToken').value = ${JSON.stringify(token)};
            document.querySelector('#saveServer').click();
          })()`);
          await waitFor(
            () => cdp.evaluate("document.readyState === 'complete' && !!document.querySelector('#connState')"),
            20000,
            "chat page after save"
          ).catch(async (error) => {
            console.log("      diagnostics: " + await cdp.evaluate(`JSON.stringify({
              errors: window.__errors || [],
              conn: (document.querySelector('#connState') || {}).textContent || '',
              text: (document.body.innerText || '').slice(0, 120)
            })`));
            throw error;
          });
          const text = await bodyText(cdp);
          check("first connect lands on chat", text.includes("重连中") || text.includes("已连接"));
          check("first connect leaves 设置", !(await cdp.evaluate("!!document.querySelector('#saveServer')")));
        } finally {
          await closePage(targetId);
        }
      }

      // 3. configured + message mode -> chat page and mode=message
      {
        const { cdp, targetId } = await newPage();
        try {
          await cdp.send("Page.navigate", {
            url: `${origin}/seed.html?mode=message&session=${encodeURIComponent(sessionId)}&next=/index.html`,
          });
          await waitFor(
            () => cdp.evaluate("document.readyState === 'complete' && !!document.querySelector('.v3head')"),
            15000,
            "chat page render"
          );
          const text = await bodyText(cdp);
          check("configured lands on chat page", text.includes("重连中") || text.includes("已连接"));
          check("configured does not show 连接 DSH", !text.includes("连接 DSH"));
          const frames = JSON.parse(await cdp.evaluate("JSON.stringify(window.__frames || [])"));
          const sigs = frames.map((frame) => frame[0]);
          check("configured never flashes 设置/连接页", !sigs.includes("settings") && !sigs.includes("setup"),
            sigs.join(" -> "));
          const firstShown = frames.find((frame) => frame[0] !== "empty");
          check("configured first frame is chat or loading",
            !!firstShown && (firstShown[0] === "chat" || firstShown[0] === "loading"),
            firstShown ? `${firstShown[0]} @${firstShown[1]}ms` : "no frame");
          await shot(cdp, "v3-landing-chat.png");

          // Global back gesture: shell calls window.__dockletBack() (sdk onBack).
          check("sdk exposes __dockletBack", await cdp.evaluate("typeof window.__dockletBack === 'function'"));
          await cdp.evaluate("document.querySelector('#settings').click()");
          await waitFor(() => cdp.evaluate("!!document.querySelector('#saveServer')"), 8000, "settings opened");
          const handled = await cdp.evaluate("window.__dockletBack() === true");
          const backOnChat = await cdp.evaluate("window.__dockletBack() === true");
          check("back inside settings is handled", handled && !(await cdp.evaluate("!!document.querySelector('#saveServer')")));
          check("back on chat is unhandled (shell then closes)", backOnChat === false);

          // Model chip opens the model/reasoning/permission sheet.
          await waitFor(() => cdp.evaluate("!!document.querySelector('#modelChip')"), 8000, "model chip");
          await cdp.evaluate("document.querySelector('#modelChip').click()");
          await waitFor(() => cdp.evaluate("!!document.querySelector('#modelSheet')"), 8000, "model sheet");
          const sheetInfo = JSON.parse(await cdp.evaluate(`(() => {
            const sheet = document.querySelector('#modelSheet');
            return JSON.stringify({
              title: sheet.querySelector('.v3modal-title').textContent,
              models: sheet.querySelectorAll('[data-ms-model]').length,
              efforts: sheet.querySelectorAll('[data-ms-effort]').length,
              perms: sheet.querySelectorAll('[data-ms-perm]').length
            });
          })()`));
          check("model sheet opens from the chip", sheetInfo.title === "模型与推理", JSON.stringify(sheetInfo));
          check("model sheet has models/efforts/permissions",
            sheetInfo.models >= 2 && sheetInfo.efforts === 4 && sheetInfo.perms === 3, JSON.stringify(sheetInfo));
          const sheetZh = JSON.parse(await cdp.evaluate(`(() => {
            const sheet = document.querySelector('#modelSheet');
            return JSON.stringify({
              effort: (sheet.querySelector('[data-ms-effort]') || {}).textContent || '',
              perm: (sheet.querySelector('[data-ms-perm]') || {}).textContent || '',
              provider: (sheet.querySelector('.v3ms-provider') || {}).textContent || ''
            });
          })()`));
          check("model sheet uses raw labels and 模型 header",
            sheetZh.effort === "Off" && sheetZh.perm === "仅可查看" && sheetZh.provider === "模型",
            JSON.stringify(sheetZh));
          await cdp.evaluate("document.querySelector('#modelCancel').click()");
          check("model sheet closes", await cdp.evaluate("!document.querySelector('#modelSheet')"));

          // Connection chip: breathing dot, no status block, quick network switch.
          await waitFor(() => cdp.evaluate("!!document.querySelector('#connChip')"), 8000, "conn chip");
          check("header drops the activity status", await cdp.evaluate("document.querySelectorAll('.v3sub .v3dot').length === 0"));
          check("connected dot is breathing green",
            await cdp.evaluate("!!document.querySelector('#connChip .v3condot.ok')"));
          await cdp.evaluate("document.querySelector('#connChip').click()");
          await waitFor(() => cdp.evaluate("!!document.querySelector('#connSheet')"), 8000, "conn sheet");
          const connInfo = JSON.parse(await cdp.evaluate(`(() => {
            const sheet = document.querySelector('#connSheet');
            return JSON.stringify({
              modes: sheet.querySelectorAll('[data-conn-pick]').length,
              current: (sheet.querySelector('.v3ms-hint') || {}).textContent || '',
              autoOn: !!(sheet.querySelector('[data-conn-pick="auto"]') || {}).classList?.contains('on')
            });
          })()`));
          check("conn sheet offers auto/lan/wan", connInfo.modes === 3, JSON.stringify(connInfo));
          check("conn sheet shows the current network", connInfo.current.includes("当前连接方式："), JSON.stringify(connInfo));
          check("conn sheet has save and speed test",
            await cdp.evaluate("!!document.querySelector('#connSave') && !!document.querySelector('#connSpeed')"));
          check("conn modes are spread out",
            await cdp.evaluate("(() => { const row = document.querySelector('.v3conn-row'); return row ? row.querySelectorAll('[data-conn-pick]').length === 3 : false; })()"));
          const connAlign = JSON.parse(await cdp.evaluate(`(() => {
            const row = document.querySelector('.v3conn-row');
            const speed = row && row.querySelector('#connSpeed');
            const chip = row && row.querySelector('[data-conn-pick]');
            const card = row && row.closest('.v3modal-card');
            const rect = (el) => el ? el.getBoundingClientRect() : null;
            const s = rect(speed), c = rect(chip), k = rect(card);
            return JSON.stringify({
              sameRow: !!(s && c && Math.abs(s.top - c.top) < 8),
              speedRightGap: s && k ? Math.round(k.right - s.right) : -1,
              chipLeftGap: c && k ? Math.round(c.left - k.left) : -1
            });
          })()`));
          check("speed test sits right on the mode row",
            connAlign.sameRow && connAlign.speedRightGap >= 10 && connAlign.speedRightGap <= 24 && connAlign.chipLeftGap >= 10 && connAlign.chipLeftGap <= 24,
            JSON.stringify(connAlign));
          await cdp.evaluate("document.querySelector('#connClose').click()");
          check("conn sheet closes", await cdp.evaluate("!document.querySelector('#connSheet')"));

          const subs = await waitFor(
            async () => {
              const list = JSON.parse(await cdp.evaluate("JSON.stringify(window.__bgSubs || [])"));
              return list.length ? list : null;
            },
            20000,
            "backgroundMessages.subscribe"
          ).catch(async (error) => {
            const state = await cdp.evaluate(`JSON.stringify({
              conn: (document.querySelector('#connState') || {}).textContent || '',
              notify: localStorage.getItem('v3.notify'),
              mode: localStorage.getItem('v3.notifyMode'),
              token: (localStorage.getItem('v3.token') || '').length,
              base: localStorage.getItem('v3.base'),
              hasBg: !!(window.docklet && window.docklet.backgroundMessages),
              subs: window.__bgSubs || []
            })`);
            console.log(`      diagnostics: ${state}`);
            throw error;
          });
          const sub = subs[subs.length - 1] || {};
          const subUrl = String(sub.endpoint || "");
          check("background subscription points at /sessions", subUrl.includes("/sessions?"), redact(subUrl));
          check("background subscription forces turn mode", subUrl.includes("mode=turn"), redact(subUrl));
          check("background subscription uses auth header",
            String((sub.headers && sub.headers.Authorization) || "").startsWith("Bearer "),
            String((sub.headers && sub.headers.Authorization) || "").slice(0, 12));
          check("background subscription listens to turn.end",
            Array.isArray(sub.eventTypes) && sub.eventTypes.includes("turn.end"),
            JSON.stringify(sub.eventTypes || null));
          check("background subscription notifies in foreground", sub.foreground === "notify", String(sub.foreground));

          const info = JSON.parse(await cdp.evaluate(`(() => {
            const list = document.querySelector('#list');
            const rows = [...document.querySelectorAll('.v3row[data-mid]:not(.me)')];
            const longs = rows.filter((row) => {
              const bubble = row.querySelector('.v3bubble');
              return bubble && bubble.offsetHeight > list.clientHeight * 1.2;
            });
            const buttons = [...document.querySelectorAll('.v3toTop')];
            const inside = buttons.every((btn) => {
              const bubble = btn.closest('.v3bubble');
              if (!bubble) return false;
              const style = getComputedStyle(btn);
              const alpha = /rgba\([^)]*,\s*([0-9.]+)\)/.exec(style.backgroundColor);
              return style.position === 'absolute'
                && btn.offsetParent === bubble
                && (!alpha || Number(alpha[1]) < 1);
            });
            return JSON.stringify({ rows: rows.length, long: longs.length, buttons: buttons.length, inside,
              listH: list.clientHeight, heights: rows.map((row) => (row.querySelector('.v3bubble') || {}).offsetHeight || 0) });
          })()`));
          if (info.long > 0) {
            check("to-top button only on long replies", info.buttons === info.long && info.inside, JSON.stringify(info));
            await shot(cdp, "v3-to-top.png");
            const before = Number(await cdp.evaluate("document.querySelector('#list').scrollTop"));
            await cdp.evaluate(`(() => { document.querySelector('.v3toTop').click(); })()`);
            let last = -1;
            let stable = 0;
            for (let i = 0; i < 40; i += 1) {
              await new Promise((resolve) => setTimeout(resolve, 100));
              const now = Number(await cdp.evaluate("document.querySelector('#list').scrollTop"));
              if (now === last) {
                stable += 1;
                if (stable >= 3) break;
              } else {
                stable = 0;
              }
              last = now;
            }
            const after = Number(await cdp.evaluate("document.querySelector('#list').scrollTop"));
            const rowTop = Number(await cdp.evaluate(`(() => {
              const row = document.querySelector('.v3toTop').closest('.v3row');
              const list = document.querySelector('#list');
              return row.getBoundingClientRect().top - list.getBoundingClientRect().top;
            })()`));
            check("to-top click scrolls up", after < before, `${before} -> ${after}`);
            check("to-top lands on the avatar", Math.abs(rowTop - 8) <= 24, `rowTop=${rowTop.toFixed(1)}`);
          } else {
            console.log("SKIP  to-top button (no reply taller than 1.2 screens in this session)");
          }

          // Multi-image layout (v1 gallery spec): 1 capped, 2 side by side, 5 -> 3 columns.
          const layout = JSON.parse(await cdp.evaluate(`(() => {
            const list = document.querySelector('#list');
            const host = document.createElement('div');
            host.innerHTML = '<div class="v3bubble"><div class="chat-md-gallery is-one"><img></div>'
              + '<div class="chat-md-gallery is-two"><img><img></div>'
              + '<div class="chat-md-gallery is-many"><img><img><img><img><img></div></div>';
            list.appendChild(host);
            const box = (el) => el.getBoundingClientRect();
            const one = box(host.querySelector('.is-one img'));
            const two = [...host.querySelectorAll('.is-two img')].map(box);
            const many = [...host.querySelectorAll('.is-many img')].map(box);
            host.remove();
            return JSON.stringify({
              oneW: Math.round(one.width),
              twoTop: two.map((r) => Math.round(r.top)),
              manyTop: many.map((r) => Math.round(r.top)),
            });
          })()`));
          check("single gallery image is capped", layout.oneW > 0 && layout.oneW <= 340, `${layout.oneW}px`);
          check("two images sit side by side", layout.twoTop[0] === layout.twoTop[1], JSON.stringify(layout.twoTop));
          check("five images use three columns",
            new Set(layout.manyTop.slice(0, 3)).size === 1 && layout.manyTop[3] > layout.manyTop[0],
            JSON.stringify(layout.manyTop));

          // Viewer must page through all images of one bubble (v1 behaviour).
          const viewer = JSON.parse(await cdp.evaluate(`(() => {
            const list = document.querySelector('#list');
            const px = 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7';
            const host = document.createElement('div');
            host.innerHTML = '<div class="v3bubble"><div class="chat-md-gallery is-two">'
              + '<img class="v3img" src="' + px + '"><img class="v3img" src="' + px + '"></div></div>';
            list.appendChild(host);
            host.querySelectorAll('img')[0].click();
            const view = document.querySelector('#viewer');
            const track = document.querySelector('#viewerTrack');
            const count = document.querySelector('#viewerCount');
            const save = document.querySelector('#saveViewer');
            const close = document.querySelector('#closeViewer');
            const opened = !view.hidden && track.querySelectorAll('.v3viewer-page').length === 2;
            const snapStop = getComputedStyle(track.querySelector('.v3viewer-page')).scrollSnapStop;
            const first = count.textContent;
            track.scrollLeft = track.clientWidth;
            track.dispatchEvent(new Event('scroll'));
            const second = count.textContent;
            const saveLeftOfClose = save.getBoundingClientRect().right <= close.getBoundingClientRect().left + 2;
            const saveIconOnly = !save.textContent.trim() && !!save.querySelector("svg");
            save.click();
            document.querySelector('#closeViewer').click();
            const closed = view.hidden;
            host.remove();
            return JSON.stringify({ opened, first, second, closed, snapStop, saveLeftOfClose, saveIconOnly });
          })()`));
          check("viewer opens with all bubble images", viewer.opened === true);
          check("viewer stops on every page", viewer.snapStop === "always", viewer.snapStop);
          check("viewer counter follows the swipe", viewer.first === "1 / 2" && viewer.second === "2 / 2",
            `${viewer.first} -> ${viewer.second}`);
          check("viewer closes and clears", viewer.closed === true);
          check("viewer save sits left of close", viewer.saveLeftOfClose === true);
          check("viewer save is an icon", viewer.saveIconOnly === true);
          const toast = await waitFor(
            async () => {
              const text = String(await cdp.evaluate("(document.querySelector('#toast') || {}).textContent || ''"));
              const shown = await cdp.evaluate("(() => { const el = document.querySelector('#toast'); return !!(el && !el.hidden); })()");
              return text && shown ? text : "";
            },
            6000,
            "save toast"
          ).catch(async (error) => {
            const diag = await cdp.evaluate(`JSON.stringify({
              errors: window.__errors || [],
              files: typeof (window.docklet && window.docklet.files && window.docklet.files.write),
              download: typeof (window.docklet && window.docklet.download),
            })`);
            console.log("      diagnostics: " + diag);
            throw error;
          });
          check("viewer save reports success", String(toast).includes("已保存"), String(toast));

          // Images split into separate galleries must still be swipeable together.
          const loneViewer = JSON.parse(await cdp.evaluate(`(() => {
            const list = document.querySelector('#list');
            const px = 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7';
            const host = document.createElement('div');
            host.innerHTML = '<div class="v3bubble">'
              + '<div class="chat-md-gallery is-one"><img class="v3img" src="' + px + '"></div>'
              + '<div class="chat-md-gallery is-one"><img class="v3img" src="' + px + '"></div>'
              + '</div>';
            list.appendChild(host);
            host.querySelectorAll('img')[0].click();
            const view = document.querySelector('#viewer');
            const track = document.querySelector('#viewerTrack');
            const count = document.querySelector('#viewerCount');
            const opened = !view.hidden;
            const pages = track.querySelectorAll('.v3viewer-page').length;
            const first = count.textContent;
            track.scrollLeft = track.clientWidth;
            track.dispatchEvent(new Event('scroll'));
            const second = count.textContent;
            const bar = document.querySelector('.v3viewer-bar');
            const stage = document.querySelector('#viewerStage');
            const save = document.querySelector('#saveViewer');
            const close = document.querySelector('#closeViewer');
            const br = bar.getBoundingClientRect(), sr = stage.getBoundingClientRect();
            const cr = count.getBoundingClientRect(), sv = save.getBoundingClientRect(), xr = close.getBoundingClientRect();
            const layout = {
              barRight: Math.round(innerWidth - br.right),
              barBottom: Math.round(innerHeight - br.bottom),
              stageBelowBar: Math.round(sr.bottom) <= Math.round(br.top),
              order: cr.right <= sv.left + 1 && sv.right <= xr.left + 1,
              closeRight: Math.round(innerWidth - xr.right),
              counterCenter: Math.round((cr.left + cr.right) / 2 - innerWidth / 2),
              buttonGap: Math.round(xr.left - sv.right)
            };
            document.querySelector('#closeViewer').click();
            host.remove();
            return JSON.stringify({ opened, pages, first, second, layout });
          })()`));
          check("viewer spans separate lone images", loneViewer.opened === true && loneViewer.pages === 2, JSON.stringify(loneViewer));
          check("viewer controls sit bottom-right",
            loneViewer.layout.barRight <= 20 && loneViewer.layout.barBottom <= 60 && loneViewer.layout.closeRight <= 24,
            JSON.stringify(loneViewer.layout));
          check("viewer controls order count/save/close", loneViewer.layout.order === true);
          check("viewer counter is centered", Math.abs(loneViewer.layout.counterCenter) <= 3, String(loneViewer.layout.counterCenter));
          check("viewer buttons have breathing room", loneViewer.layout.buttonGap >= 12, String(loneViewer.layout.buttonGap));
          check("viewer stage never overlaps the bar", loneViewer.layout.stageBelowBar === true, JSON.stringify(loneViewer.layout));
          check("viewer swipes between lone images", loneViewer.first === "1 / 2" && loneViewer.second === "2 / 2",
            `${loneViewer.first} -> ${loneViewer.second}`);

          await cdp.evaluate(`(() => {
            const list = document.querySelector('#list');
            const svg = "data:image/svg+xml;utf8," + encodeURIComponent("<svg xmlns='http://www.w3.org/2000/svg' width='600' height='900'><rect width='600' height='900' fill='#f4a261'/><circle cx='300' cy='320' r='120' fill='#e76f51'/></svg>");
            const host = document.createElement('div');
            host.id = "viewerProbe";
            host.innerHTML = '<div class="v3bubble"><div class="chat-md-gallery is-one"><img class="v3img" src="' + svg + '"></div></div>';
            list.appendChild(host);
            host.querySelector('img').click();
            return 'ok';
          })()`);
          await new Promise((resolve) => setTimeout(resolve, 350));
          await cdp.evaluate("(() => { const t = document.querySelector('#toast'); if (t) t.hidden = true; })()");
          await shot(cdp, "v3-viewer.png");
          await cdp.evaluate("(() => { const t = document.querySelector('#toast'); if (t) { t.hidden = true; t.textContent = ''; } const svg = document.querySelector('#saveViewer svg'); if (svg) svg.dispatchEvent(new MouseEvent('click', { bubbles: true })); })()");
          const iconToast = await waitFor(
            async () => {
              const text = String(await cdp.evaluate("(document.querySelector('#toast') || {}).textContent || ''"));
              const shown = await cdp.evaluate("(() => { const el = document.querySelector('#toast'); return !!(el && !el.hidden); })()");
              return text && shown ? text : "";
            },
            6000,
            "icon save toast"
          );
          check("save works when tapping the icon", String(iconToast).includes("已保存"), String(iconToast));
          await cdp.evaluate("document.querySelector('#closeViewer').click(); document.querySelector('#viewerProbe')?.remove();");

          // Stop state must be the same 46x46 button as send, only recoloured.
          const morph = JSON.parse(await cdp.evaluate(`(() => {
            const btn = document.querySelector("#send");
            if (!btn) return JSON.stringify({ missing: true });
            const before = btn.getBoundingClientRect();
            const beforeLabel = btn.textContent;
            const duration = getComputedStyle(btn).transitionDuration;
            btn.classList.add("is-stop");
            btn.textContent = "停止";
            const after = btn.getBoundingClientRect();
            const radius = getComputedStyle(btn).borderRadius;
            btn.classList.remove("is-stop");
            btn.textContent = beforeLabel;
            return JSON.stringify({
              sendW: before.width, sendH: before.height,
              stopW: after.width, stopH: after.height, radius, duration
            });
          })()`));
          check("stop button keeps the send size", morph.stopW === morph.sendW && morph.stopH === morph.sendH,
            `${morph.sendW}x${morph.sendH} -> ${morph.stopW}x${morph.stopH}`);
          check("stop morph animates 0.25s", String(morph.duration).includes("0.25"), String(morph.duration));

          // A stopped turn must render as a normal assistant row with a tinted bubble.
          const sid = String(await cdp.evaluate("localStorage.getItem('v3.session') || ''"));
          const anchorMid = String(await cdp.evaluate("(document.querySelector('#list .v3row[data-mid]') || {}).dataset?.mid || ''"));
          await cdp.evaluate(`localStorage.setItem("v3.stops.${sid}", JSON.stringify([{ id: "stop-probe", turn: 9, at: Date.now(), by: "pc", afterId: ${JSON.stringify(anchorMid)} }]))`);
          await cdp.send("Page.reload");
          await waitFor(() => cdp.evaluate("document.readyState === 'complete' && !!document.querySelector('#connState')"), 15000, "chat reload");
                    const stopRow = JSON.parse(await cdp.evaluate(`(() => {
            const row = document.querySelector('.v3stoprow');
            if (!row) return JSON.stringify({ missing: true });
            const bubble = row.querySelector('.v3bubble');
            return JSON.stringify({
              text: bubble ? bubble.textContent.trim() : '',
              copy: !!row.querySelector('[data-copy-message]'),
              avatar: !!row.querySelector('.v3av'),
              bg: bubble ? getComputedStyle(bubble).backgroundColor : ''
            });
          })()`));
          check("stopped turn renders as a message row",
            stopRow.avatar === true && stopRow.copy === true && String(stopRow.text).includes("电脑端"),
            JSON.stringify(stopRow));
          check("stopped turn bubble is tinted", stopRow.bg !== "rgb(255, 255, 255)" && !!stopRow.bg, String(stopRow.bg));
          await shot(cdp, "v3-stop-note.png");
          await cdp.evaluate(`localStorage.removeItem("v3.stops.${sid}")`);
          await cdp.send("Page.reload");
          await waitFor(() => cdp.evaluate("document.readyState === 'complete' && !!document.querySelector('#connState')"), 15000, "chat reload 2");

          // Lone images in a multi-image message are capped as thumbnails.
          const thumbs = JSON.parse(await cdp.evaluate(`(async () => {
            const list = document.querySelector('#list');
            const svg = "data:image/svg+xml;utf8," + encodeURIComponent("<svg xmlns='http://www.w3.org/2000/svg' width='400' height='300'></svg>");
            const host = document.createElement('div');
            host.innerHTML = '<div class="v3bubble">'
              + '<div class="chat-md-gallery is-one is-thumb" data-count="1"><img src="' + svg + '"></div>'
              + '<div class="chat-md-gallery is-one" data-count="1"><img src="' + svg + '"></div>'
              + '</div>';
            list.appendChild(host);
            const imgs = [...host.querySelectorAll('img')];
            await Promise.all(imgs.map((img) => img.decode().catch(() => {})));
            const w = imgs.map((img) => Math.round(img.getBoundingClientRect().width));
            const h = imgs.map((img) => Math.round(img.getBoundingClientRect().height));
            host.remove();
            return JSON.stringify({ thumbW: w[0], thumbH: h[0], normalW: w[1] });
          })()`, true));
          check("lone image is capped as a thumbnail", thumbs.thumbW > 0 && thumbs.thumbW <= 160, `${thumbs.thumbW}x${thumbs.thumbH}`);
          check("single image keeps the large layout", thumbs.normalW > thumbs.thumbW, `${thumbs.normalW} vs ${thumbs.thumbW}`);

          // Session list cache: first open fills the cache, reload paints from it.
          await cdp.evaluate("document.querySelector('#sessionList').click()");
          await waitFor(() => cdp.evaluate("!!document.querySelector('.v3session')"), 8000, "sessions page");
          await shot(cdp, "v3-sessions.png");
          await cdp.evaluate("document.querySelector('#archivedToggle').click()");
          await waitFor(() => cdp.evaluate("!!document.querySelector('#archivedList')"), 8000, "archived page");
          await shot(cdp, "v3-archived.png");
          await cdp.evaluate("document.querySelector('#backArchived').click()");
          await waitFor(() => cdp.evaluate("!!document.querySelector('#sessionListBody')"), 8000, "back to sessions");
          const cached = Number(await cdp.evaluate(`(() => { try { const raw = JSON.parse(localStorage.getItem('v3.cache.sessions') || 'null'); return raw && raw.items ? raw.items.length : 0; } catch (e) { return 0; } })()`));
          check("session list is cached", cached > 0, `items=${cached}`);
          await cdp.send("Page.navigate", { url: `${origin}/index.html` });
          await waitFor(() => cdp.evaluate("!!document.querySelector('#connState')"), 15000, "chat after reload");
          const tCache = Date.now();
          await cdp.evaluate("document.querySelector('#sessionList').click()");
          await waitFor(() => cdp.evaluate("!!document.querySelector('.v3session')"), 3000, "sessions from cache");
          check("sessions render from cache after reload", true, `${Date.now() - tCache}ms`);

          // Offline: uncached session must open with a hint, cached one must still render.
          await cdp.send("Network.enable");
          await cdp.send("Network.emulateNetworkConditions", {
            offline: true, latency: 0, downloadThroughput: 0, uploadThroughput: 0,
          });
          await new Promise((resolve) => setTimeout(resolve, 700));
          const currentSession = await cdp.evaluate("localStorage.getItem('v3.session') || ''");
          const ids = JSON.parse(await cdp.evaluate(
            "JSON.stringify([...document.querySelectorAll('.v3session')].map((x) => x.dataset.session))"
          ));
          const uncached = ids.find((id) => id && id !== currentSession);
          if (uncached) {
            const wantTitle = String(await cdp.evaluate(`(() => {
              try { const raw = JSON.parse(localStorage.getItem('v3.cache.sessions') || 'null');
                const item = raw && (raw.items || []).find((x) => x && x.id === ${JSON.stringify(uncached)});
                return item ? item.title || '' : ''; } catch (e) { return ''; }
            })()`));
            await cdp.evaluate(`document.querySelector('.v3session[data-session="${uncached}"]').click()`);
            await waitFor(() => cdp.evaluate("!!document.querySelector('.v3empty-off')"), 6000, "offline empty state");
            const emptyText = String(await cdp.evaluate(
              "document.querySelector('.v3empty-off') ? document.querySelector('.v3empty-off').innerText : ''"
            ));
            check("offline uncached session opens with hint",
              emptyText.includes("连接已断开") && emptyText.includes("也没最近缓存"), emptyText.replace(/\s+/g, " "));
            await shot(cdp, "v3-offline-empty.png");
            const shownTitle = String(await cdp.evaluate("(document.querySelector('.v3title') || {}).textContent || ''"));
            check("offline session keeps its cached title", !!wantTitle && shownTitle === wantTitle,
              `"${shownTitle}" vs "${wantTitle}"`);
            const barHidden = await cdp.evaluate("(() => { const bar = document.querySelector('#netBar'); return !bar || bar.hidden; })()");
            check("disconnect does not show the loud bar", barHidden === true);
            const offlineUi = JSON.parse(await cdp.evaluate(`(() => {
              const input = document.querySelector('#text');
              const hint = document.querySelector('.v3connhint');
              const dot = document.querySelector('#connChip .v3condot');
              const text = document.querySelector('.v3conntext');
              const center = (el) => { if (!el) return -1; const r = el.getBoundingClientRect(); return r.top + r.height / 2; };
              return JSON.stringify({
                disabled: !!(input && input.disabled),
                hintText: hint ? hint.textContent : '',
                hintAnim: hint ? getComputedStyle(hint).animationName : '',
                dot: !!dot,
                textAnim: text ? getComputedStyle(text).animationName : '',
                dotCenter: center(dot),
                dotAnim: dot ? getComputedStyle(dot).animationName : '',
              });
            })()`));
            check("offline disables the composer", offlineUi.disabled && offlineUi.hintText.includes("连接已断开"),
              offlineUi.hintText);
            check("connector hint flashes", String(offlineUi.hintAnim).includes("v3phflash"), offlineUi.hintAnim);
            check("reconnect text flashes", String(offlineUi.textAnim).includes("v3phflash"), offlineUi.textAnim);
            check("header shows a breathing dot", offlineUi.dot === true);
            check("reconnect dot pulses", String(offlineUi.dotAnim).includes("v3pulse"), offlineUi.dotAnim);
          } else {
            console.log("SKIP  offline uncached session (only one session in the list)");
          }
          await cdp.evaluate("document.querySelector('#sessionList').click()");
          await waitFor(() => cdp.evaluate("!!document.querySelector('.v3session')"), 8000, "sessions while offline");
          const cachedTitle = String(await cdp.evaluate(`(() => {
            try { const raw = JSON.parse(localStorage.getItem('v3.cache.sessions') || 'null');
              const item = raw && (raw.items || []).find((x) => x && x.id === ${JSON.stringify(currentSession)});
              return item ? item.title || '' : ''; } catch (e) { return ''; }
          })()`));
          await cdp.evaluate(`document.querySelector('.v3session[data-session="${currentSession}"]').click()`);
          await waitFor(() => cdp.evaluate("document.querySelectorAll('.v3row[data-mid]').length > 0"), 6000,
            "cached chat while offline");
          check("offline cached session still renders messages", true);
          const cachedShownTitle = String(await cdp.evaluate("(document.querySelector('.v3title') || {}).textContent || ''"));
          check("offline cached session keeps its title", !!cachedTitle && cachedShownTitle === cachedTitle,
            `"${cachedShownTitle}" vs "${cachedTitle}"`);

          await cdp.send("Network.emulateNetworkConditions", {
            offline: false, latency: 0, downloadThroughput: -1, uploadThroughput: -1,
          });
          await waitFor(async () => String(
            await cdp.evaluate("(document.querySelector('#connState') || {}).textContent || ''")
          ).includes("已连接"), 15000, "reconnect after offline");
          check("reconnects when network returns", true);
          const composerBack = await cdp.evaluate("(() => { const input = document.querySelector('#text'); return !!(input && !input.disabled); })()");
          check("composer re-enables after reconnect", composerBack === true);

          // Cold start with no network: title and cache must still come from storage.
          {
            const offlinePage = await newPage();
            try {
              await offlinePage.cdp.send("Page.addScriptToEvaluateOnNewDocument", {
                source: `window.fetch = () => Promise.reject(new Error("offline"));
                  window.EventSource = function () { return { close() {}, addEventListener() {} }; };
                  try { Object.defineProperty(navigator, "onLine", { get: () => false }); } catch (e) {}`,
              });
              await offlinePage.cdp.send("Page.navigate", { url: `${origin}/index.html` });
              await waitFor(() => offlinePage.cdp.evaluate("!!document.querySelector('.v3title')"), 12000,
                "cold start title");
              const coldTitle = String(await offlinePage.cdp.evaluate(
                "(document.querySelector('.v3title') || {}).textContent || ''"
              ));
              check("cold start offline keeps the title", coldTitle === cachedTitle,
                `"${coldTitle}" vs "${cachedTitle}"`);
              await shot(offlinePage.cdp, "v3-offline-coldstart.png");
            } finally {
              await closePage(offlinePage.targetId);
            }
          }
        } finally {
          await closePage(targetId);
        }
      }

      // 3. turn mode -> mode=turn
      {
        const { cdp, targetId } = await newPage();
        try {
          await cdp.send("Page.navigate", {
            url: `${origin}/seed.html?mode=turn&session=${encodeURIComponent(sessionId)}&next=/index.html`,
          });
          await waitFor(
            () => cdp.evaluate("document.readyState === 'complete' && !!document.querySelector('.v3head')"),
            15000,
            "chat page render (turn)"
          );
          const subs2 = await waitFor(
            async () => {
              const list = JSON.parse(await cdp.evaluate("JSON.stringify(window.__bgSubs || [])"));
              return list.length ? list : null;
            },
            20000,
            "backgroundMessages.subscribe (turn)"
          );
          const sub2 = String((subs2[subs2.length - 1] || {}).endpoint || "");
          check("background subscription re-subscribes on mode change", sub2.includes("mode=turn"), redact(sub2));
        } finally {
          await closePage(targetId);
        }
      }
    }
  } finally {
    child.kill();
    await stopServer().catch(() => {});
  }

  const failed = results.filter((item) => !item.pass);
  console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
  if (failed.length) process.exit(1);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
