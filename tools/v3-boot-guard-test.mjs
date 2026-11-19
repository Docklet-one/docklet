// Unit test for boot/back guards (0.5.9).
//
// 1. 未配置时，$() 会把路由固定在设置页；返回键必须交回壳，不能原地打转。
// 2. 启动/保存时地址统一规范化，避免 new URL 抛错把用户踢回配置页。
// 3. 启动失败不再强制回到配置页，只要令牌/会话/地址还在就留在对话页显示错误。

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

const normalizeBase = new Function(
  `${extractFunction(source, "normalizeBase")}\nreturn normalizeBase;`
)();
check("adds the scheme", normalizeBase("192.168.2.6:8788") === "http://192.168.2.6:8788");
check("keeps https", normalizeBase("https://x.example/") === "https://x.example");
check("empty stays empty", normalizeBase("  ") === "");

const makeTt = (configured, viewerVisible) => {
  const calls = [];
  const t = { querySelector: () => (viewerVisible ? { hidden: false } : null) };
  const tt = new Function(
    "t", "e", "isConfigured", "H",
    `${extractFunction(source, "tt")}\nreturn tt;`
  )(t, { route: "settings" }, () => configured, (route) => calls.push(route));
  return { tt, calls };
};
check("unconfigured back goes to the shell", makeTt(false, false).tt() === false);
check("viewer back still handled", makeTt(false, true).tt() === true);
const configured = makeTt(true, false);
check("configured settings back goes to chat", configured.tt() === true && configured.calls[0] === "chat");

check("boot failure keeps the config", source.includes("e.forceSetup = !(e.token && e.session && e.base)"));
check("settings save normalises addresses", source.includes('normalizeBase(t.querySelector("#setLan").value)'));
check("settings save skips the extra /sessions", source.includes('if (!e.session || !qe(e.state))'));

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall boot guard cases passed");
