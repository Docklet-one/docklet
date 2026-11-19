// Unit test for optimistic message reconciliation (0.6.19).
//
// The SSE state machine knows nothing about the local placeholder, so the
// placeholder is dropped at the mount-level merge once the real user message
// arrives (text compared with whitespace normalized).

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

const reconcile = new Function(
  `${extractFunction(source, "optimisticText")}\n${extractFunction(source, "reconcileOptimistic")}\nreturn reconcileOptimistic;`
)();

let failed = 0;
function check(name, pass, detail = "") {
  if (!pass) failed += 1;
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  " + detail : ""}`);
}

const pending = (text) => ({ id: "pending-1", role: "user", text, optimistic: true });
const real = (id, text) => ({ id, role: "user", text, revision: 10 });

check("matching real message drops the placeholder",
  reconcile([real("m1", "你好"), pending("你好")]).length === 1);
check("whitespace differences still match",
  reconcile([real("m1", "你好  世界"), pending(" 你好 世界 ")]).length === 1);
check("different text keeps the placeholder",
  reconcile([real("m1", "另一个"), pending("你好")]).length === 2);
check("assistant-only merge keeps it",
  reconcile([{ id: "a", role: "assistant", text: "回复" }, pending("你好")]).length === 2);
check("already clean stays clean",
  reconcile([real("m1", "你好")]).length === 1);

check("merge applies reconciliation", source.includes("messages: reconcileOptimistic(n.messages)"));
check("cache window also reconciles", source.includes("reconcileOptimistic(Nt(d, 80))"));

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall optimistic cases passed");
