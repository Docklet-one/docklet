// Unit test for the settings server label (0.4.4).
//
// Shows which base is actually in use: 家里 (LAN) / 外部 (WAN) / 自定义.

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

const make = (e) => new Function("e", `${extractFunction(source, "currentBaseLabel")}\nreturn currentBaseLabel;`)(e);

let failed = 0;
function check(name, pass, detail = "") {
  if (!pass) failed += 1;
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  " + detail : ""}`);
}

check("LAN base", make({ base: "http://192.168.2.6:8788", lanBase: "http://192.168.2.6:8788", wanBase: "http://x.example" })() === "家里");
check("WAN base", make({ base: "http://x.example", lanBase: "http://192.168.2.6:8788", wanBase: "http://x.example" })() === "外部");
check("custom base", make({ base: "http://other:8788", lanBase: "http://a", wanBase: "http://b" })() === "自定义");
check("trailing slash", make({ base: "http://a/", lanBase: "http://a", wanBase: "" })() === "家里");
check("empty base", make({ base: "", lanBase: "http://a" })() === "");
check("settings row shows it", source.includes("} · ${currentBaseLabel()}`"));

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall base label cases passed");
