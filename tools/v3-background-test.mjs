// Unit test for the backgroundMessages integration (0.10.38).
//
// V3 no longer owns the background connection: index.html subscribes through
// docklet.backgroundMessages (B mode /sessions), sets activeSession on
// route/session/visibility changes, and has no legacy fallback.

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const dir = join(here, "..", "apps", "dsh-console");
const html = readFileSync(join(dir, "index.html"), "utf8");
const js = readFileSync(join(dir, "v3.js"), "utf8");

let failed = 0;
function check(name, pass, detail = "") {
  if (!pass) failed += 1;
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  " + detail : ""}`);
}

check("detects backgroundMessages", html.includes("window.docklet.backgroundMessages"));
check("subscribes with /sessions endpoint", html.includes("base + '/sessions?'"));
check("uses notify foreground", html.includes("foreground: 'notify'"));
check("filters turn.end", html.includes("eventTypes: ['turn.end']"));
check("sends auth header", html.includes("Authorization: 'Bearer ' + policy.token"));
check("dedupes by eventId", html.includes("dedupeBy: 'eventId'"));
check("persists the subscription", html.includes("persist: true"));
check("has no legacy background.start fallback", !html.includes("window.docklet.background.start"));
check("unsubscribes on disable", html.includes("backgroundMessages.unsubscribe()"));
check("exposes setActiveSession to V3", html.includes("setActiveSession: (id) => setActiveSession(id)"));
check("null clears the active session", html.includes("bg.setActiveSession(sessionId || null)"));

check("V3 syncs active session", js.includes("function syncActiveSession()"));
check("sync on route change", /function H\(s\) \{\n    e\.route = s, syncActiveSession\(\)/.test(js));
check("sync on session switch", js.includes('e.route = "chat", syncActiveSession()'));
check("sync on visibility change", js.includes("syncActiveSession();\n    if (document.visibilityState"));
check("V3 uses the mount option", js.includes("n.setActiveSession(id)"));

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall background integrations passed");
