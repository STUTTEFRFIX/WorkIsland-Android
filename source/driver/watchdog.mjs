// 工作岛 driver 的看门狗：跑在网关进程树之外（setsid），网关重启不会带走它。
// 每 15 秒检查 driver 的 PID 文件；进程不在了就重新拉一个。
// 应用里的「保活」开关写在 Android/media/.../keepalive.conf：0 停、1 保。
import fs from "node:fs";
import { spawn, execFile } from "node:child_process";

const HOME = "/data/user/0/cc.codroid/files/home";
const DIR = HOME + "/workspace/task-island";
const PID = HOME + "/.work-island.pid";
const SELF = HOME + "/.work-island-watchdog.pid";
const KEEP = "/storage/emulated/0/Android/media/com.dafeiyu.workisland/keepalive.conf";
const PKG = "com.dafeiyu.workisland";
const SVC = PKG + "/.IslandService";
const AM = "/system/bin/am";
const PRIV = "/data/user/0/cc.codroid/files/app/bin/priv";
const log = (...a) => console.log(new Date().toISOString(), ...a);

// 只允许一个看门狗
try {
  const old = Number(fs.readFileSync(SELF, "utf8").trim());
  if (old && old !== process.pid && fs.existsSync("/proc/" + old)) {
    const cmd = fs.readFileSync("/proc/" + old + "/cmdline", "utf8");
    if (cmd.includes("watchdog.mjs")) {
      log("another watchdog already running:", old);
      process.exit(0);
    }
  }
  fs.writeFileSync(SELF, String(process.pid));
} catch {}

function driverPid() {
  try {
    const pid = Number(fs.readFileSync(PID, "utf8").trim());
    if (!pid) return 0;
    return fs.readFileSync("/proc/" + pid + "/cmdline", "utf8").includes("driver.mjs") ? pid : 0;
  } catch {
    return 0;
  }
}

function ensure() {
  if (driverPid()) return;
  try {
    const out = fs.openSync(HOME + "/.work-island.log", "a");
    const p = spawn("setsid", ["node", "driver.mjs"], {
      cwd: DIR,
      detached: true,
      stdio: ["ignore", out, out],
    });
    p.unref();
    log("respawned driver", p.pid);
  } catch (e) {
    log("respawn failed", e.message);
  }
}

function stopDriver(idle) {
  const pid = driverPid();
  if (pid) {
    try { process.kill(pid); log("stopped driver", pid); } catch {}
  }
  if (idle) {
    // 用 CANCEL 而不是 IDLE：CANCEL 会撤掉通知并停掉服务，岛上干净地收起
    const args = ["start-foreground-service", "--user", "0", "-n", SVC, "-a", PKG + ".CANCEL"];
    execFile(AM, args, { timeout: 8000 }, (e) => {
      if (e) execFile(PRIV, ["--", AM, ...args], { timeout: 12000 }, () => {});
    });
  }
}

let lastOps = 0;
function keepAliveOps() {
  const now = Date.now();
  if (now - lastOps < 5 * 60_000) return;
  lastOps = now;
  const run = (bin, ...args) => execFile(PRIV, ["--", bin, ...args], { timeout: 12000 }, () => {});
  run("/system/bin/cmd", "deviceidle", "whitelist", "+" + PKG);
  run("/system/bin/am", "set-standby-bucket", PKG, "active");
  run("/system/bin/appops", "set", PKG, "RUN_ANY_IN_BACKGROUND", "allow");
  run("/system/bin/appops", "set", PKG, "WAKE_LOCK", "allow");
}

function keepWanted() {
  try { return fs.readFileSync(KEEP, "utf8").trim() !== "0"; } catch { return true; }
}

let lastKeep = null;
function tick() {
  const keep = keepWanted();
  if (keep !== lastKeep) {
    lastKeep = keep;
    log("keep-alive", keep ? "on" : "off");
    if (!keep) stopDriver(true);
  }
  if (!keep) return;
  ensure();
  keepAliveOps();
}

tick();
setInterval(tick, 15000);   // 不要 unref：否则事件循环里没有活句柄，看门狗会自己退出
log("watchdog starting, pid", process.pid);
