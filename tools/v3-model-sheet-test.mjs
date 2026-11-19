// Unit test for the model & reasoning sheet (0.4.6).
//
// The chat header shows "deepseek-flash · high" in a chip; tapping it opens a
// sheet listing models, reasoning efforts and permission, and applying posts to
// /model (and /permission when it changed).

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

const saStub = (m) => (typeof m === "string" ? m : String((m && (m.name || m.id || m.model)) || ""));
const make = (state) => new Function(
  "e", "qe", "sa",
  `${extractFunction(source, "modelChipLabel")}\nreturn modelChipLabel;`
)({ state }, (st) => st && st.session, saStub);

let failed = 0;
function check(name, pass, detail = "") {
  if (!pass) failed += 1;
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  " + detail : ""}`);
}

check("object model with effort",
  make({ session: { model: { provider: "deepseek-account", model: "deepseek-flash", reasoningEffort: "high" } } })() === "deepseek-flash · high");
check("object model without effort",
  make({ session: { model: { provider: "p", model: "m1" } } })() === "m1");
check("string model",
  make({ session: { model: "deepseek-flash" } })() === "deepseek-flash");
check("missing model falls back",
  make({ session: {} })() === "模型");

check("transport posts /model", source.includes('n + "/model"'));
check("transport posts /permission", source.includes('n + "/permission"'));
check("client exposes selectModel", source.includes("selectModel: (l) => n.selectModel(l)"));
check("client exposes setPermission", source.includes("setPermission: (l) => n.setPermission(l)"));
check("sheet title and controls", source.includes("模型与推理") && source.includes('id="modelApply"') && source.includes('data-ms-effort'));
check("chip opens the sheet", source.includes('closest("#modelChip")') && source.includes("openModelSheet()"));
check("header rebuild keeps the chip", source.includes('class="v3modelchip" id="modelChip"'));
check("provider row says 模型", source.includes('<div class="v3ms-provider">模型</div>'));
const sheetStart = source.indexOf("function modelSheetHtml()");
const sheetBlock = source.slice(sheetStart, source.indexOf("\n  function ", sheetStart + 10));
check("only the apply hint remains", sheetBlock.includes("保存后下次生效") && !sheetBlock.includes("v3ms-desc") && !sheetBlock.includes("默认平衡") && !sheetBlock.includes("只能改工作区"));
check("efforts stay raw", source.includes("item.name || item.id") && !source.includes("effortLabel"));

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall model sheet cases passed");
