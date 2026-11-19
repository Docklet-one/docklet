// Unit test for pagination state in the session cache (0.6.5).
//
// 重进会话后「加载更早」还能用：hasMore / oldestSeq 随缓存落盘并恢复，
// cursor 仍然重置为 seq:0，让下一次快照重新对齐。

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

const store = new Map();
const localStorageStub = {
  getItem: (k) => (store.has(k) ? store.get(k) : null),
  setItem: (k, v) => store.set(k, String(v))
};
const Pt = new Function(
  "localStorage", "Nt", "At",
  `${extractFunction(source, "Pt")}\nreturn Pt;`
)(localStorageStub, (list, n) => list.slice(-n), () => "v3.cache.s1");
const We = new Function(
  "localStorage", "At",
  `${extractFunction(source, "We")}\nreturn We;`
)(localStorageStub, (id) => "v3.cache." + id);

Pt({
  session: { id: "s1", title: "t" },
  messages: [{ id: "m1", text: "x" }],
  prompts: [],
  deliverables: [],
  processes: [],
  activity: [],
  sync: { cursor: "seq:42", oldestSeq: 17, hasMore: true, seen: ["a", "b"] }
});

const raw = JSON.parse(store.get("v3.cache.s1"));
check("cache stores hasMore", raw.sync.hasMore === true);
check("cache stores oldestSeq", raw.sync.oldestSeq === 17);

const restored = We("s1");
check("restore keeps hasMore", restored.sync.hasMore === true);
check("restore keeps oldestSeq", restored.sync.oldestSeq === 17);
check("restore resets the cursor", restored.sync.cursor === "seq:0");
check("restore clears dedupe set", Array.isArray(restored.sync.seen) && restored.sync.seen.length === 0);

store.set("v3.cache.old", JSON.stringify({ messages: [{ id: "m" }] }));
const legacy = We("old");
check("legacy cache defaults to no more", legacy.sync.hasMore === false && legacy.sync.oldestSeq === 0);

check("empty state carries pagination fields", source.includes("oldestSeq: 0, hasMore: !1"));

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall history cases passed");
