// Unit test for V3's event gate (Rt): which relay events are accepted.
//
// The gate used to require protocolVersion === "1.0" exactly, so a relay that
// bumped its protocol would make V3 silently drop every event. It now only
// requires a session match, an event id and a type.
//
// The test evaluates the shipped `Rt` source from apps/dsh-console/v3.js.

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
    const ch = text[i];
    if (ch === "{") {
      depth += 1;
      body = true;
    } else if (ch === "}") {
      depth -= 1;
      if (body && depth === 0) return text.slice(start, i + 1);
    }
  }
  throw new Error(`function ${name} is not balanced`);
}

const rt = new Function(`${extractFunction(source, "Rt")}\nreturn Rt;`)();
const base = { sessionId: "s1", eventId: "s1@1", type: "message.assistant", protocolVersion: "1.0" };

const cases = [
  ["accepts protocol 1.0", { ...base }, "s1", true],
  ["accepts a future protocol 1.1", { ...base, protocolVersion: "1.1" }, "s1", true],
  ["accepts a missing protocol", { ...base, protocolVersion: undefined }, "s1", true],
  ["rejects another session", { ...base, sessionId: "s2" }, "s1", false],
  ["rejects a missing event id", { ...base, eventId: "" }, "s1", false],
  ["rejects a missing type", { ...base, type: "" }, "s1", false],
  ["rejects null", null, "s1", false],
  ["accepts without a session filter", { ...base }, "", true],
];

let failed = 0;
for (const [name, event, session, expected] of cases) {
  const got = !!rt(event, session);
  const pass = got === expected;
  if (!pass) failed += 1;
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}  (got ${got})`);
}

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall event gate cases passed");
