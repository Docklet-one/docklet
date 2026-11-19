// Unit test for the SSE freshness watchdog (0.5.5).
//
// The browser cannot see a dead TCP connection, so a session that receives no
// events for 15s triggers a /health probe; a failed probe drops the connection
// and lets rediscover take over.

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const source = readFileSync(join(here, "..", "apps", "dsh-console", "v3.js"), "utf8");

function extractFunction(text, name) {
  const start = text.search(new RegExp(`(?:async )?function ${name}\\(`));
  if (start < 0) throw new Error(`function ${name} not found`);
  let depth = 0;
  let body = false;
  for (let i = start; i < text.length; i += 1) {
    if (text[i] === "{") { depth += 1; body = true; }
    else if (text[i] === "}") { depth -= 1; if (body && depth === 0) return text.slice(start, i + 1); }
  }
  throw new Error(`function ${name} is not balanced`);
}

const make = (e, hidden = false) => new Function(
  "e", "document",
  `${extractFunction(source, "connectionLooksStale")}\nreturn connectionLooksStale;`
)(e, { hidden });

let failed = 0;
function check(name, pass, detail = "") {
  if (!pass) failed += 1;
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  " + detail : ""}`);
}

const fresh = { connected: true, client: {}, lastBeatAt: Date.now() - 5e3, switchingUntil: 0 };
const stale = { connected: true, client: {}, lastBeatAt: Date.now() - 25e3, switchingUntil: 0 };

check("fresh stream is fine", make(fresh)() === false);
check("exactly 20s is not stale yet", make({ ...fresh, lastBeatAt: Date.now() - 19e3 })() === false);
check("stale stream probes", make(stale)() === true);
check("hidden page does not probe", make(stale, true)() === false);
check("disconnected does not probe", make({ ...stale, connected: false })() === false);
check("no client does not probe", make({ ...stale, client: null })() === false);
check("session swap is exempt", make({ ...stale, switchingUntil: Date.now() + 5e3 })() === false);

check("watchdog runs every 5s", source.includes("checkConnectionFreshness();\n  }, 5e3);"));
check("plugin probe decides", source.includes("Qe(e.base, 2500)"));
check("hint distinguishes network vs plugin", source.includes("网络已断开，请检查网络") && source.includes("连不到电脑，请检查 relay 或网络"));
check("offline event probes instead of assuming", source.includes("checkConnectionFreshness(!0)"));
check("probe failure drops and rediscovers",
  source.includes('Qe(e.base, 2500)') && source.includes('rediscover("watchdog")'));

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall freshness cases passed");
