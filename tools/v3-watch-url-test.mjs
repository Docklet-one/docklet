// Unit test for the V3 module's background subscription URL builder.
//
// The module hands the shell a /sessions endpoint. `mode` is reserved for a
// later release: the shell currently treats every finished turn as one
// reminder, and V3 always asks for turn.
//
// This test evaluates the shipped `startWatchService` source (extracted from
// apps/dsh-console/index.html) instead of a copy, so a drift between the
// test and the real page fails the test.

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const htmlPath = join(here, "..", "apps", "dsh-console", "index.html");
const html = readFileSync(htmlPath, "utf8");

function extractFunction(source, name) {
  const start = source.indexOf(`async function ${name}(`);
  if (start < 0) throw new Error(`function ${name} not found`);
  let depth = 0;
  let body = false;
  for (let i = start; i < source.length; i += 1) {
    const ch = source[i];
    if (ch === "{") {
      depth += 1;
      body = true;
    } else if (ch === "}") {
      depth -= 1;
      if (body && depth === 0) return source.slice(start, i + 1);
    }
  }
  throw new Error(`function ${name} is not balanced`);
}

const source = extractFunction(html, "startWatchService");
const factory = new Function("window", `${source}\nreturn startWatchService;`);

function run(policy) {
  const calls = [];
  const window = {
    docklet: {
      backgroundMessages: {
        subscribe: async (options) => {
          calls.push(options);
          return { subscribed: true };
        },
      },
    },
  };
  const fn = factory(window);
  return fn(policy).then((ok) => ({ ok, calls }));
}

const cases = [
  {
    name: "legacy change flag maps to turn",
    policy: { endpoint: "http://10.0.2.2:8788/health", token: "tok", mode: "change" },
    expect: "http://10.0.2.2:8788/sessions?token=tok&mode=turn",
  },
  {
    name: "message mode is forwarded",
    policy: { endpoint: "http://10.0.2.2:8788/health", token: "tok", mode: "message" },
    expect: "http://10.0.2.2:8788/sessions?token=tok&mode=message",
  },
  {
    name: "turn mode (mode absent)",
    policy: { endpoint: "http://10.0.2.2:8788/health", token: "tok" },
    expect: "http://10.0.2.2:8788/sessions?token=tok&mode=turn",
  },
  {
    name: "no token still sends mode",
    policy: { endpoint: "http://192.168.2.6:8788/health" },
    expect: "http://192.168.2.6:8788/sessions?mode=turn",
  },
  {
    name: "trailing slash on /health",
    policy: { endpoint: "http://192.168.2.6:8788/health/", token: "t" },
    expect: "http://192.168.2.6:8788/sessions?token=t&mode=turn",
  },
];

let failed = 0;
for (const item of cases) {
  const { ok, calls } = await run(item.policy);
  const url = calls.length ? calls[0].endpoint : "";
  const pass = ok === true && calls.length === 1 && url === item.expect;
  if (!pass) failed += 1;
  console.log(
    `${pass ? "PASS" : "FAIL"}  ${item.name}\n      got: ${url || "<no call>"}`
  );
}

const missing = await run({ endpoint: "" });
const missingPass = missing.ok === false && missing.calls.length === 0;
if (!missingPass) failed += 1;
console.log(`${missingPass ? "PASS" : "FAIL"}  endpoint missing -> no subscribe call`);

if (failed > 0) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall watch URL cases passed");
