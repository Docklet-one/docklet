// Unit tests for round 8: ordered message insert + date chips + skeleton.

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

const pt = new Function(`${extractFunction(source, "pt")}\nreturn pt;`)();

let failed = 0;
function check(name, pass, detail = "") {
  if (!pass) failed += 1;
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  " + detail : ""}`);
}

const row = (id, rev) => ({ id, revision: rev, text: id });
let list = [row("a", 10), row("b", 20)];
list = pt(list, row("c", 15));
check("late event inserts in order", list.map((m) => m.id).join(",") === "a,c,b");
list = pt(list, row("b", 30));
check("newer revision replaces in place", list.find((m) => m.id === "b").revision === 30);
list = pt(list, row("b", 5));
check("older revision is ignored", list.find((m) => m.id === "b").revision === 30);
list = pt(list, row("d", 40));
check("append keeps order", list.map((m) => m.id).join(",") === "a,c,b,d");

{
  const optimistic = [{ id: "pending-1", role: "user", text: "hello", optimistic: true, revision: Number.MAX_SAFE_INTEGER }];
  const real = pt(optimistic, { id: "m-real", role: "user", text: "hello", revision: 42 });
  check("real message replaces the optimistic bubble", real.length === 1 && real[0].id === "m-real" && real[0].revision === 42);
  const other = pt(optimistic, { id: "m-2", role: "user", text: "different", revision: 43 });
  check("different text keeps both", other.length === 2);
}

check("send shows a pending bubble", source.includes('id: pendingId') || source.includes('pendingId'));
check("pending bubble expires", source.includes("15000"));
check("date divider is centered markup", source.includes('<div class="v3date"><span>'));
check("date chip css", source.includes(".v3date span{padding:2px 10px"));
check("session skeleton markup", source.includes("v3skel-row"));
check("loading uses skeleton", source.includes("length: 6"), "six placeholder rows");

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall round 8 cases passed");
