// Unit test for the chat header connection label (0.4.5).
//
// The header now reads "已连接 · 家里" / "已连接 · 外部", matching the settings row.

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

const make = (e, label) => new Function(
  "e", "currentBaseLabel",
  `${extractFunction(source, "connStateHtml")}\nreturn connStateHtml;`
)(e, () => label);

let failed = 0;
function check(name, pass, detail = "") {
  if (!pass) failed += 1;
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  " + detail : ""}`);
}

check("LAN connected", make({ connected: true }, "家里")().includes("已连接 · 家里"));
check("WAN connected", make({ connected: true }, "外部")().includes("已连接 · 外部"));
check("no label falls back", make({ connected: true }, "")().includes(">已连接<"));
check("disconnected keeps the dot", make({ connected: false }, "家里")().includes("重连中"));
check("connected dot breathes green", make({ connected: true }, "家里")().includes("v3condot ok"));
check("chip is clickable", make({ connected: true }, "家里")().includes('id="connChip"') && make({ connected: true }, "家里")().includes("button"));

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall conn state cases passed");
