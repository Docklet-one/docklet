// Unit test for the V3 message-image blob cache pruner.
//
// Blob URLs are never freed without this: a long conversation keeps every
// decoded image alive. The pruner must drop the oldest entries, but it must
// not revoke a blob that an <img> is still displaying, otherwise a visible
// picture turns blank.
//
// The test evaluates the shipped `pruneImageCache` source (extracted from
// apps/dsh-console/v3.js) so a drift between test and bundle fails here.

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const sourcePath = join(here, "..", "apps", "dsh-console", "v3.js");
const source = readFileSync(sourcePath, "utf8");

function extractFunction(text, name) {
  const start = text.search(new RegExp(`(?:async )?function ${name}\\(`));
  if (start < 0) throw new Error(`function ${name} not found`);
  let depth = 0;
  let body = false;
  for (let i = start; i < text.length; i += 1) {
    const ch = text[i];
    if (ch === "{") {
      depth += 1;
      body = true;
    } else if (ch === "}") {
      depth -= 1;
      if (body && depth === 0) return text.slice(start, i + 1);
    }
  }
  throw new Error(`function ${name} is not balanced`);
}

const code = extractFunction(source, "pruneImageCache");
const make = (url) => new Function("URL", `${code}\nreturn pruneImageCache;`)(url);

function build(count) {
  const cache = new Map();
  for (let i = 0; i < count; i += 1) cache.set(`k${i}`, `blob:u${i}`);
  return cache;
}

let failed = 0;
function check(name, pass, detail = "") {
  if (!pass) failed += 1;
  console.log(`${pass ? "PASS" : "FAIL"}  ${name}${detail ? "  " + detail : ""}`);
}

// 1. trims the oldest entries down to the limit
{
  const revoked = [];
  const run = make({ revokeObjectURL: (url) => revoked.push(url) });
  const cache = build(30);
  run(cache, 24, () => false);
  check("keeps newest 24 of 30", cache.size === 24, `size=${cache.size}`);
  check("revokes the 6 oldest", revoked.length === 6 && revoked[0] === "blob:u0" && revoked[5] === "blob:u5",
    revoked.join(","));
  check("keeps newest keys", cache.has("k29") && !cache.has("k5"));
}

// 2. never revokes a blob that is still on screen
{
  const revoked = [];
  const run = make({ revokeObjectURL: (url) => revoked.push(url) });
  const cache = build(30);
  run(cache, 24, (url) => url === "blob:u0");
  check("skips the on-screen blob", cache.has("k0") && !revoked.includes("blob:u0"));
  check("revokes the next oldest instead", revoked.length === 6 && revoked[0] === "blob:u1",
    revoked.join(","));
}

// 3. everything live -> nothing is dropped
{
  const revoked = [];
  const run = make({ revokeObjectURL: (url) => revoked.push(url) });
  const cache = build(30);
  run(cache, 24, () => true);
  check("all-live leaves the cache alone", cache.size === 30 && revoked.length === 0,
    `size=${cache.size} revoked=${revoked.length}`);
}

// 4. already under the limit -> no-op
{
  const revoked = [];
  const run = make({ revokeObjectURL: (url) => revoked.push(url) });
  const cache = build(10);
  run(cache, 24, () => false);
  check("under limit is untouched", cache.size === 10 && revoked.length === 0);
}

// 5. a throwing revoke must not stop the pruning
{
  const run = make({ revokeObjectURL: () => { throw new Error("already revoked"); } });
  const cache = build(30);
  let error = null;
  try {
    run(cache, 24, () => false);
  } catch (cause) {
    error = cause;
  }
  check("survives revoke errors", !error && cache.size === 24, error ? String(error) : `size=${cache.size}`);
}

// 6. missing limit -> no-op (guard against a bad caller)
{
  const revoked = [];
  const run = make({ revokeObjectURL: (url) => revoked.push(url) });
  const cache = build(30);
  run(cache, 0, () => false);
  check("zero limit is a no-op", cache.size === 30 && revoked.length === 0);
}

if (failed) {
  console.error(`\n${failed} failed`);
  process.exit(1);
}
console.log("\nall image cache cases passed");
