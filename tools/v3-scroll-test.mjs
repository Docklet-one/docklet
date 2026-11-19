// Unit test for per-session scroll restoration (0.6.6).

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

const target = new Function(
  `${extractFunction(source, "scrollRestoreTarget")}\nreturn scrollRestoreTarget;`
)();

let failed = 0;
function check(name, pass, detail = "") {
  if (!pass) failed += 1;
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  " + detail : ""}`);
}

check("no memory stays at bottom", target(null) === null);
check("near bottom stays at bottom", target({ top: 400, nearBottom: true }) === null);
check("zero offset stays at bottom", target({ top: 0, nearBottom: false }) === null);
check("scrolled position is restored", target({ top: 420, nearBottom: false }) === 420);
check("numeric strings work", target({ top: "120.7", nearBottom: false }) === 120.7);
check("negative position clamps to bottom", target({ top: -20, nearBottom: false }) === null);

check("session switch uses the helper", source.includes("e.restoreScroll = scrollRestoreTarget((e.scrollMemory || {})[o])"));
check("scroll saves per session", source.includes("e.scrollMemory = { ...(e.scrollMemory || {}), [sid]: { top: T.scrollTop, nearBottom: A } }"));
check("render restores before anchoring", source.includes("if (e.restoreScroll !== null)"));
check("state starts with null restore", source.includes("restoreScroll: null,"));

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall scroll cases passed");
