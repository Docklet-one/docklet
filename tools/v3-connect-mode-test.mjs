// Unit test for connect-mode locking (0.5.3).
//
// Forcing 家里/外部 must not be undone by auto rediscovery: pickBase only probes
// the chosen side, and the periodic/boot LAN re-probe runs in auto mode only.

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

const make = (e, reachable) => {
  const probed = [];
  const pickBase = new Function(
    "e", "Qe",
    `${extractFunction(source, "pickBase")}\nreturn pickBase;`
  )(e, async (url) => {
    probed.push(url);
    return reachable.includes(url);
  });
  return { pickBase, probed };
};

let failed = 0;
function check(name, pass, detail = "") {
  if (!pass) failed += 1;
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  " + detail : ""}`);
}

const e = { connectMode: "lan", lanBase: "http://lan", wanBase: "http://wan" };

{
  const { pickBase, probed } = make({ ...e }, ["http://lan", "http://wan"]);
  check("lan mode picks lan", (await pickBase()) === "http://lan");
  check("lan mode never probes wan", !probed.includes("http://wan"), JSON.stringify(probed));
}
{
  const { pickBase, probed } = make({ ...e, connectMode: "wan" }, ["http://lan", "http://wan"]);
  check("wan mode picks wan", (await pickBase()) === "http://wan");
  check("wan mode never probes lan", !probed.includes("http://lan"), JSON.stringify(probed));
}
{
  const { pickBase } = make({ ...e, connectMode: "wan" }, ["http://lan"]);
  check("wan mode does not fall back to lan", (await pickBase()) === "");
}
{
  const { pickBase } = make({ ...e, connectMode: "lan" }, ["http://wan"]);
  check("lan mode does not fall back to wan", (await pickBase()) === "");
}
{
  const { pickBase } = make({ ...e, connectMode: "auto" }, ["http://lan", "http://wan"]);
  check("auto prefers lan", (await pickBase()) === "http://lan");
}
{
  const { pickBase } = make({ ...e, connectMode: "auto" }, ["http://wan"]);
  check("auto falls back to wan", (await pickBase()) === "http://wan");
}

check("periodic LAN re-probe is auto only", source.includes('e.connectMode === "auto" && e.lanBase && e.base !== e.lanBase'));
check("boot re-probe is auto only", source.includes('e.connectMode === "auto" && e.lanBase && e.base !== e.lanBase && rediscover("boot")'));

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall connect mode cases passed");
