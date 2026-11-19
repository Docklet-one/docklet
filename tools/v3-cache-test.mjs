// Unit test for the cache quota fallback (0.6.3).
//
// localStorage 写满时，消息逐级减少（30/20/12/6），交付、过程、提示始终保留，
// 而不是整份缓存被放弃。

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

let failed = 0;
function check(name, pass, detail = "") {
  if (!pass) failed += 1;
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  " + detail : ""}`);
}

const state = {
  session: { id: "s1", title: "t" },
  messages: [...Array.from({ length: 80 }, (_, i) => ({ id: "m" + i, text: "x".repeat(10) })), { id: "pending-1", role: "user", text: "hi", optimistic: true }],
  prompts: [{ id: "p" }],
  deliverables: [{ id: "d" }],
  processes: [{ startSeq: 1 }],
  activity: { status: "idle" }
};

function run(maxChars) {
  const attempts = [];
  const store = new Map();
  const localStorageStub = {
    setItem(key, value) {
      attempts.push(value.length);
      if (value.length > maxChars) throw new Error("QuotaExceededError");
      store.set(key, value);
    }
  };
  const Pt = new Function(
    "localStorage", "Nt", "At",
    `${extractFunction(source, "Pt")}\nreturn Pt;`
  )(localStorageStub, (list, n) => list.slice(-n), () => "v3.cache.s1");
  Pt(state);
  const saved = JSON.parse(store.get("v3.cache.s1") || "{}");
  return { saved, attempts };
}

{
  const { saved, attempts } = run(1e9);
  check("writes once when it fits", attempts.length === 1);
  check("keeps 35 messages", saved.messages.length === 35);
  check("keeps deliverables and processes", saved.deliverables.length === 1 && saved.processes.length === 1);
}

{
  const { saved, attempts } = run(1000);
  check("retries with fewer messages", attempts.length > 1, String(attempts.length));
  check("falls back below 35 messages", saved.messages.length < 35 && saved.messages.length >= 8, String(saved.messages.length));
  check("keeps the newest messages", saved.messages[saved.messages.length - 1].id === "m79");
  check("optimistic messages are not cached", !saved.messages.some((m) => m && m.optimistic));
check("still keeps deliverables", saved.deliverables.length === 1 && saved.processes.length === 1 && saved.prompts.length === 1);
}

check("gives up after four attempts", (source.match(/\[35, 25, 15, 8\]/) || []).length === 1);

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall cache cases passed");
