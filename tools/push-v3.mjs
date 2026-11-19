// Push the local V3 files into the installed module on the phone for instant
// visual iteration (no packaging / install round trip).
//
// Usage: node tools/push-v3.mjs [package]
// Default package: com.docklet.shell.debug

import { execFileSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";

const ADB = "D:\\docklet\\tools\\platform-tools\\adb.exe";
const SERIAL = process.env.ADB_SERIAL || "192.168.2.3:42293";
const PKG = process.argv[2] || "com.docklet.shell.debug";
const MODULE = "dsh-console";
const SRC = "D:\\docklet\\apps\\dsh-console";
const FILES = ["index.html", "v3.js", "style.css"];

const adb = (args, opts = {}) =>
  execFileSync(ADB, ["-s", SERIAL, ...args], { encoding: "utf8", ...opts }).trim();

const current = adb(["shell", `run-as ${PKG} cat files/modules/${MODULE}/current`]);
console.log("installed current:", current);

const remoteTmp = "/data/local/tmp/v3-push";
adb(["shell", `rm -rf ${remoteTmp} && mkdir -p ${remoteTmp}`]);
for (const file of FILES) {
  adb(["push", path.join(SRC, file), `${remoteTmp}/${file}`]);
}
const target = `files/modules/${MODULE}/${current}`;
const copy = FILES.map((file) => `cp ${remoteTmp}/${file} ${target}/`).join(" && ");
adb(["shell", `run-as ${PKG} sh -c '${copy}'`]);
adb(["shell", `rm -rf ${remoteTmp}`]);
console.log("pushed:", FILES.join(", "), "->", target);
