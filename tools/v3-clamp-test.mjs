// Unit test for long-message clamping (0.6.7).

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

const shouldClamp = new Function(
  `${extractFunction(source, "shouldClampMessage")}\nreturn shouldClampMessage;`
)();

let failed = 0;
function check(name, pass, detail = "") {
  if (!pass) failed += 1;
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  " + detail : ""}`);
}

check("long assistant reply clamps", shouldClamp("assistant", "x".repeat(1201)) === true);
check("short assistant reply stays open", shouldClamp("assistant", "x".repeat(1200)) === false);
check("user messages never clamp", shouldClamp("user", "x".repeat(5000)) === false);
check("empty text is ignored", shouldClamp("assistant", "") === false);

check("markup adds the clamp class", source.includes('clamp && !expanded ? " is-clamped"'));
check("markup adds the expand button", source.includes('data-expand-message="${b(v.id)}"'));
check("expanded state is remembered", source.includes("e.expandedMessages = { ...(e.expandedMessages || {}), [id]: !clamped }"));
check("clamp has a fade", source.includes(".v3bubble.is-clamped::after"));
check("toggle is direct DOM (no re-render)", source.includes("bubble.classList.toggle(\"is-clamped\")"));

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall clamp cases passed");
