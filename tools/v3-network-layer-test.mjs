// Unit test for the shell network-state layer (0.5.7).
//
// 第一层：docklet.network.getState/watch 判断“设备有没有网”
// 第二层：/health 判断“插件通不通”
// 没网直接断开；有网立即探插件，不等定时器。

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

function makeApply() {
  const e = { connected: true, client: { stop() { e.stopped = true; } }, route: "settings" };
  let probes = 0;
  let renders = 0;
  const apply = new Function(
    "e", "checkConnectionFreshness", "$",
    `${extractFunction(source, "applyNetworkState")}\nreturn applyNetworkState;`
  )(e, () => { probes += 1; }, () => { renders += 1; });
  return { apply, e, probes: () => probes, renders: () => renders };
}

{
  const t = makeApply();
  t.apply({ online: false, type: "none" });
  check("no network drops the connection", t.e.connected === false && t.e.stopped === true);
  check("no network sets the net reason", t.e.connReason === "net");
  check("no network does not probe", t.probes() === 0);
}
{
  const t = makeApply();
  t.apply({ online: true, type: "wifi", validated: true });
  check("network available probes the plugin", t.probes() === 1);
  check("network available keeps the connection", t.e.connected === true);
}
{
  const t = makeApply();
  t.apply({ online: true, type: "wifi", validated: false });
  check("unvalidated wifi still probes", t.probes() === 1);
}
{
  const t = makeApply();
  t.apply(null);
  check("null state is ignored", t.probes() === 0 && t.e.connected === true);
}

const makeNetworkApi = new Function(
  "n",
  `${extractFunction(source, "networkApi")}\nreturn networkApi;`
);
const withApi = makeNetworkApi({ network: { getState() {} } });
const withoutApi = makeNetworkApi({});
check("detects docklet.network", !!withApi());
check("falls back without the api", withoutApi() === null);

check("mount exposes network", readFileSync(join(here, "..", "apps", "dsh-console", "index.html"), "utf8").includes("network: window.docklet && window.docklet.network"));
check("subscribes on boot", source.includes("e.unwatchNetwork = n.network.watch("));
check("refreshes on visible", source.includes("if (!document.hidden) refreshNetworkState();"));
check("boot keeps the stored lanBase", !source.includes("e.base = p, e.lanBase = p") && source.includes("typed && (e.lanBase = p)"));
check("quick switch skips a redundant /sessions", source.includes("if (!e.session || !qe(e.state))"));

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall network layer cases passed");
