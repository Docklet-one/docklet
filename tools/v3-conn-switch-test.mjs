// Unit test for the quick network switcher (0.4.9).

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

const store = new Map();
const localStorageStub = {
  setItem: (key, value) => store.set(key, String(value)),
  getItem: (key) => (store.has(key) ? store.get(key) : null)
};
const e = { connectMode: "auto", base: "http://lan", lanBase: "http://lan", wanBase: "http://wan", connected: true };
let reconnected = 0;
const switchConnectMode = new Function(
  "e", "localStorage", "pickSession", "Ge", "$",
  `${extractFunction(source, "switchConnectMode")}\nreturn switchConnectMode;`
)(e, localStorageStub, async () => {}, () => { reconnected += 1; }, () => {});

let failed = 0;
function check(name, pass, detail = "") {
  if (!pass) failed += 1;
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  " + detail : ""}`);
}

await switchConnectMode("wan");
check("switches to the WAN base", e.base === "http://wan" && e.connectMode === "wan");
check("persists the choice", store.get("v3.mode") === "wan" && store.get("v3.base") === "http://wan");
check("reconnects", reconnected === 1);
check("closes the sheet", e.connSheet === false || e.connSheet === null || e.connSheet === undefined);

e.wanBase = "";
store.clear();
e.connSheet = true;
await switchConnectMode("wan");
check("missing WAN base is refused", Boolean(e.error) && e.connectMode === "wan");
check("shows an error", /外部地址/.test(e.error));

check("header has the quick switcher", source.includes('id="connChip"') && source.includes('data-conn-pick'));
check("old status block removed", !source.includes("v3dot ${f}"));
check("green breathing dot", source.includes(".v3condot.ok") && source.includes("v3breath"));
check("session swap keeps the connected state",
  source.includes("switchingUntil = Date.now() + 6e3") && !source.includes("e.lastPromptCount = 0, e.connected = !1"));
check("transient connecting is suppressed",
  source.includes("e.switchingUntil > Date.now()") && source.includes("attempt === 0"));

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall conn switch cases passed");
