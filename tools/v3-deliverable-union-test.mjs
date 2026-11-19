// Unit test for deliverable survival across snapshots (0.4.1).
//
// The snapshot only carries the last few turns of events. deliverable.present
// scrolls out of that window quickly, so the state merge must keep the cards we
// already had instead of replacing them with the snapshot's empty list.

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

const unionDeliverables = new Function(`${extractFunction(source, "unionDeliverables")}\nreturn unionDeliverables;`)();
const Ve = new Function(
  "unionDeliverables", "Nt",
  `${extractFunction(source, "Ve")}\nreturn Ve;`
)(unionDeliverables, (list) => list.slice(-30));

let failed = 0;
function check(name, pass, detail = "") {
  if (!pass) failed += 1;
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  " + detail : ""}`);
}

const a1 = { id: "a:1:0", path: "a.png", seq: 1, index: 0 };
const b1 = { id: "b:2:0", path: "b.png", seq: 2, index: 0 };

const both = unionDeliverables([a1], [b1]);
check("union keeps both", both.length === 2 && both[0].path === "a.png" && both[1].path === "b.png");
check("union updates fields", unionDeliverables([a1], [{ ...a1, description: "x" }])[0].description === "x");
check("union caps at 20",
  unionDeliverables([], Array.from({ length: 25 }, (_, i) => ({ id: "f" + i, path: "f" + i, seq: i, index: 0 }))).length === 20);

const cache = { messages: [{ id: "m1", revision: 1 }], deliverables: [a1] };
const snapshotNoDeliverables = { messages: [{ id: "m1", revision: 1 }], deliverables: [] };
check("snapshot without deliverables keeps the cards",
  Ve(cache, snapshotNoDeliverables).deliverables.length === 1);
check("snapshot with disjoint messages keeps the cards",
  Ve(cache, { messages: [{ id: "m9", revision: 9 }], deliverables: [] }).deliverables.length === 1);
check("cached messages survive an empty snapshot",
  Ve(cache, { messages: [] }).messages.length === 1);

check("union used in state merge", source.includes("unionDeliverables(a && a.deliverables, n && n.deliverables)"));
check("render signature tracks pending/anchor",
  source.includes("(d.pending ? 1 : 0)") && source.includes('(d.afterId || "")'));

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall deliverable union cases passed");
