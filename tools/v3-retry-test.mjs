// Unit test for the turn-error retry action (0.6.14).

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

const makeLast = (messages) => new Function(
  "e", "gt",
  `${extractFunction(source, "lastUserMessage")}\nreturn lastUserMessage;`
)({}, () => messages);

let failed = 0;
function check(name, pass, detail = "") {
  if (!pass) failed += 1;
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  " + detail : ""}`);
}

const msgs = [
  { id: "a", role: "user", text: "第一句" },
  { id: "b", role: "assistant", text: "回复" },
  { id: "c", role: "user", text: "第二句" },
  { id: "d", role: "assistant", text: "又回复" }
];
check("finds the last user message", makeLast(msgs)().text === "第二句");
check("assistant-only has none", makeLast([msgs[1]])() === null);
check("empty has none", makeLast([])() === null);

check("error card renders retry", source.includes('data-retry-turn>重试</button>'));
check("retry only with a user message", source.includes("${lastUserMessage() ? '<button"));
check("retry refills and sends", source.includes('box.value = last.text') && source.includes("Ze();"));
check("retry button styled", source.includes(".v3turnerror-retry{"));

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall retry cases passed");
