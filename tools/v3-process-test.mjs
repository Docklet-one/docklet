// Unit test for the folded process summary (0.6.1).
//
// 一轮的工具调用收成一行「过程 · N 步」，默认收起；点开看工具明细。
// 位置：该轮用户消息之后、最终回复之前。

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

const b = (value) => String(value ?? "").replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
const processHtml = new Function("b", `${extractFunction(source, "processHtml")}\nreturn processHtml;`)(b);

let failed = 0;
function check(name, pass, detail = "") {
  if (!pass) failed += 1;
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  " + detail : ""}`);
}

const three = processHtml({ startSeq: 10, endSeq: 20, tools: [
  { name: "read", detail: "a.txt", status: "done" },
  { name: "write", detail: "b.txt", status: "done" },
  { name: "bash", detail: "npm test", status: "error" }
] });
check("summary shows the step count", three.includes("过程 · 3 步"));
check("summary flags failures", three.includes("1 个失败"));
check("tool names render", three.includes("read") && three.includes("write") && three.includes("bash"));
check("collapsed by default", three.includes('class="v3process"') && !/class="v3process"[^>]*\bopen\b/.test(three));
check("tool status labels", three.includes("失败") && three.includes("完成"));

check("empty process renders nothing", processHtml({ tools: [] }) === "");
check("escapes tool text", processHtml({ tools: [{ name: "<x>", detail: "&", status: "done" }] }).includes("&lt;x&gt;"));

check("user message starts the process", source.includes("turnStartSeq: t.type === \"message.user\""));
check("turn.end closes it", source.includes("startSeq: Number(r.turnStartSeq) || 0") && source.includes("endSeq,"));
check("no standalone process cards in timeline", !source.includes("anns.push({ afterId: \"\", seq: Number(process.startSeq)"));
check("live tool line inside working bubble", source.includes("v3work-live") && source.includes("data-work-live-text"));
check("processes persist in cache", source.includes("processes: a.processes"));
check("processes merge across snapshots", source.includes("unionProcesses(a && a.processes, n && n.processes)"));

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall process cases passed");
