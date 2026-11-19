// Unit test for the deliverable model (0.6.0).
//
// 交付属于「回合」：turn.end 时把该轮 present 的文件钉在 turn.end 的 sequence 上，
// 渲染时插到本轮内容之后。消息缓存裁剪不影响卡片位置；窗口外的回合进「更早的交付」。

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

const mergeDeliverables = new Function(
  `${extractFunction(source, "mergeDeliverables")}\nreturn mergeDeliverables;`
)();
const groupDeliverables = new Function(
  `${extractFunction(source, "groupDeliverables")}\nreturn groupDeliverables;`
)();

let failed = 0;
function check(name, pass, detail = "") {
  if (!pass) failed += 1;
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  " + detail : ""}`);
}

const one = mergeDeliverables([], [{ path: "a.png", name: "a.png", description: "图" }], 5, 3);
check("keeps path/name/description", one.length === 1 && one[0].path === "a.png" && one[0].name === "a.png" && one[0].description === "图");
check("records event seq and turn", one[0].seq === 5 && one[0].turn === 3);
check("stable id", one[0].id === "a.png:5:0", one[0].id);
check("stays pending until turn end", one[0].pending === true && one[0].endSeq === 0);

const dup = mergeDeliverables(one, [{ path: "a.png", name: "a.png" }], 5, 3);
check("dedupes same present event", dup.length === 1);

const twoTurns = mergeDeliverables(one, [{ path: "b.png" }], 9, 4);
check("accumulates across turns", twoTurns.length === 2);
check("sorted by seq", twoTurns[0].seq === 5 && twoTurns[1].seq === 9);
check("defaults name to path", twoTurns[1].name === "b.png");

const many = [];
for (let i = 0; i < 50; i += 1) many.push({ path: "f" + i + ".png" });
const capped = mergeDeliverables([], many, 20, 5);
check("caps at 40", capped.length === 40, String(capped.length));
check("keeps the last ones", capped[0].path === "f10.png" && capped[capped.length - 1].path === "f49.png",
  `${capped[0].path}..${capped[capped.length - 1].path}`);

check("turn.end closes the turn", source.includes("const closingTurn = t.type === \"turn.end\"") && source.includes("endSeq, pending: false"));
check("turn.end dedupes by path", source.includes("byPath.set(key, d)"));
check("messages carry seq", source.includes("seq: Number(t.sequence) || 0"));
check("placement uses turn end sequence", source.includes("(row.seq || 0) > 0 && (row.seq || 0) <= endSeq"));
check("outside window goes to orphans", source.includes("if (!inWindow)") && source.includes("orphanGroups.push(g)"));
check("pending hidden until anchored", source.includes(".filter((d) => d && !d.pending)"));

// grouping: one group per turn end
{
  const groups = groupDeliverables([
    { id: "a", path: "a.png", endSeq: 100, pending: false },
    { id: "b", path: "b.png", endSeq: 100, pending: false },
    { id: "c", path: "c.png", endSeq: 200, pending: false }
  ]);
  check("groups by turn end", groups.length === 2, String(groups.length));
  check("keeps group order and size", groups[0].endSeq === 100 && groups[0].items.length === 2);
  check("single file stays its own group", groups[1].items.length === 1);
}

// Real markup: 3 files flat, 8 files show 4 + fold, same-name dedupe
{
  const factory = new Function(
    "e", "la", "$t", "b",
    `${extractFunction(source, "deliverableCard")}\n${extractFunction(source, "deliverableGroupHtml")}\nreturn deliverableGroupHtml;`
  )(
    { client: { presentedUrl: () => "/file/x" } },
    () => false,
    () => "📄",
    (v) => String(v == null ? "" : v)
  );
  const files = (n) => Array.from({ length: n }, (_, i) => ({ path: "f" + i + ".png", name: "f" + i + ".png", seq: 5, index: i }));
  const three = factory({ endSeq: 5, items: files(3) });
  check("three files render flat", !three.includes("<details") && (three.match(/class="v3deliverable"/g) || []).length === 3);
  const eight = factory({ endSeq: 5, items: files(8) });
  const visible = (eight.match(/class="v3deliverable"/g) || []).length;
  check("eight files show four plus a fold", eight.includes("<details") && visible === 8 && eight.includes("展开其余 4 个文件"));
  const dup2 = factory({ endSeq: 5, items: [
    { path: "same.apk", name: "same.apk", seq: 5, index: 0 },
    { path: "same.apk", name: "same.apk", seq: 5, index: 1 }
  ] });
  check("same-name files collapse to one card", (dup2.match(/class="v3deliverable"/g) || []).length === 1);
  check("card shows extension hint", dup2.includes("APK · 打开预览"));

  const orphanFactory = new Function(
    "deliverableCard",
    `${extractFunction(source, "orphanDeliverablesHtml")}\nreturn orphanDeliverablesHtml;`
  )((item) => `<a class="v3deliverable">${item.name}</a>`);
  const orphanGroups = Array.from({ length: 8 }, (_, i) => ({
    endSeq: 0,
    turn: i,
    items: [{ name: "docklet-shell-0.1.0-debug.apk", seq: 900 + i }]
  }));
  const orphans = orphanFactory(orphanGroups);
  check("orphan deliveries collapse to one block", orphans.includes("v3dgroup-old") && (orphans.match(/class="v3deliverable"/g) || []).length === 1);
  check("orphan block is deduped and labelled", orphans.includes("更早的交付 · 1 个文件"));
}

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall deliverable cases passed");
