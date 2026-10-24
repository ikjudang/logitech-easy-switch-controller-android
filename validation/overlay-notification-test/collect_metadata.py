"""Read only, package-scoped overlay and notification metadata collection.

No screenshots, UI trees, app launches, notification dismissal, setting changes,
or unfiltered notification dumps are saved. Failed reads remain unknown.
"""

import argparse
import datetime as dt
import json
import re
import shlex
import shutil
import subprocess
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

CHANNEL_PREFIX = "com.android.server.wm.AlertWindowNotification - "
DEFAULT_ADB = Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe"


def shell(adb, serial, *args, command=None):
    # adb shell joins arguments into shell source. Quote keys containing |/spaces.
    source = command if command is not None else shlex.join(args)
    result = subprocess.run(
        [str(adb), "-s", serial, "shell", source], capture_output=True,
        text=True, encoding="utf-8", errors="replace", timeout=20,
    )
    if result.returncode:
        raise RuntimeError(f"adb shell exited {result.returncode}")
    if result.stdout.lstrip().startswith(("Error:", "Unknown command", "Can't find service")):
        raise RuntimeError("device command unavailable")
    return result.stdout


def parse_sessions(raw, package):
    blocks = re.split(r"(?m)(?=^[ \t]*Session Session\{)", raw)
    matches = [b for b in blocks if re.search(
        r"mPackageName=" + re.escape(package) + r"(?:\s|$)", b)]
    rows = []
    for block in matches:
        alert = re.search(r"mAlertWindows=\{(.*?)\}[ \t]+mClientDead", block, re.S)
        identity = re.search(r"Session Session\{([^}]+)\}", block)
        if alert is None or identity is None:
            raise ValueError("unrecognized target session format")
        windows = re.findall(r"Window\{([^}]+)\}", alert.group(1))
        rows.append({"session": identity.group(1), "alertWindows": windows,
                     "alertWindowCount": len(windows)})
    if not matches and "WINDOW MANAGER" not in raw and "Session Session{" not in raw:
        raise ValueError("unrecognized sessions dump")
    return rows


def sessions_command(package):
    # Filter complete target session blocks on the device; unrelated window
    # titles and session details must not be returned to the host.
    program = r'''function emit( p,c) {
    marker="mPackageName=" target; p=index(block,marker);
    if(p) {
        c=substr(block,p+length(marker),1);
        if(c == "" || c ~ /[[:space:]]/) printf "%s",block;
    }
}
/WINDOW MANAGER/ { if(!header) { print "WINDOW MANAGER TARGET SESSIONS V1"; header=1 } }
/^[ \t]*Session Session\{/ { emit(); block="" }
{ block=block $0 "\n" }
END { emit(); if(!header) exit 66 }'''
    return ("set -o pipefail || exit 65; dumpsys window sessions | awk -v " +
            shlex.quote("target=" + package) + " " + shlex.quote(program))


def target_keys(raw, package):
    found = []
    for line in raw.splitlines():
        key = line.strip()
        fields = key.split("|")
        if len(fields) < 5:
            if key:
                raise ValueError("unrecognized notification key list")
            continue
        own = fields[1] == package
        system = fields[1] == "android" and fields[3] == CHANNEL_PREFIX + package
        if own or system:
            found.append({"key": key, "owner": "system_overlay" if system else "app"})
    return found


def parse_notification(raw):
    fields = {}
    allowed = {"mImportance", "mCreationTimeMs", "mUpdateTimeMs", "mVisibleSinceMs"}
    for line in raw.splitlines():
        name, separator, value = line.strip().partition("=")
        if separator and name in allowed:
            fields[name] = value
    if not fields:
        raise ValueError("target notification disappeared or format is unknown")
    return fields


def attempt(action):
    started = time.monotonic()
    result = {"at": dt.datetime.now().astimezone().isoformat(timespec="milliseconds")}
    try:
        result.update(ok=True, value=action())
    except (RuntimeError, ValueError, subprocess.TimeoutExpired, OSError) as error:
        result.update(ok=False, state="unknown", error=type(error).__name__ + ": " + str(error))
    result["durationMs"] = round((time.monotonic() - started) * 1000)
    result["completedAt"] = dt.datetime.now().astimezone().isoformat(timespec="milliseconds")
    return result


def channel_command(package):
    # Return only the exact target ID's numeric/boolean settings. In particular,
    # never return names, descriptions, notification text or unrelated channels.
    # A NotificationRecord's mChannel copy is not the current settings record.
    program = r'''BEGIN { print "TARGET_CHANNEL_FIELDS_V1" }
index($0,target) && index($0,"NotificationChannel{") {
    p=index($0,target); c=substr($0,p+length(target),1);
    if(c != sprintf("%c",39) && c != "\"") next;
    prefix=substr($0,1,index($0,"NotificationChannel{")-1);
    gsub(/^[ \t]+/,"",prefix);
    source=(prefix == "" ? "settings_dump" : "active_notification_copy");
    printf "channel source=%s",source;
    n=split($0,a,/[, ]+/);
    for(i=1;i<=n;i++) {
        gsub(/[},]/,"",a[i]);
        if(a[i] ~ /^(mImportance|mUserLockedFields|mDeleted|mOriginalImp|mUserSetImportance)=(-?[0-9]+|true|false)$/)
            printf " %s",a[i];
    }
    print "";
}'''
    # Android's printf may be external: passing a 600 KB command-substitution
    # value exceeded ARG_MAX on the tablet and silently produced no rows in v1.
    # Stream the dump, and propagate either pipeline stage's failure instead.
    return ("set -o pipefail || exit 65; dumpsys notification | awk -v " +
            shlex.quote("target=" + CHANNEL_PREFIX + package) + " " + shlex.quote(program))


def parse_channels(raw):
    lines = raw.splitlines()
    if not lines or lines[0].strip() != "TARGET_CHANNEL_FIELDS_V1":
        raise ValueError("unrecognized filtered channel output")
    rows = []
    for line in lines[1:]:
        if not line.strip():
            continue
        if not line.startswith("channel source="):
            raise ValueError("unexpected filtered channel row")
        fields = dict(token.split("=", 1) for token in line.split()[1:])
        if fields.get("source") != "settings_dump":
            continue
        if not {"mImportance", "mDeleted", "mUserLockedFields"}.issubset(fields):
            raise ValueError("target channel settings are incomplete")
        rows.append(fields)
    if not rows:
        # Empty output does not distinguish absent/deleted/hidden/unsupported
        # settings. Never report it as an observed missing channel.
        raise ValueError("no complete target channel settings row; absence is unconfirmed")
    if len(rows) != 1:
        raise ValueError("multiple target settings rows; user/context is ambiguous")
    return rows


def collect(adb, serial, package, label, include_device=False, mode="fast"):
    started = time.monotonic()
    read = lambda *args, **kwargs: shell(adb, serial, *args, **kwargs)
    result = {"at": dt.datetime.now().astimezone().isoformat(timespec="milliseconds"),
              "serial": serial, "package": package, "label": label,
              "screenRead": False, "mutations": False, "mode": mode,
              "atomicSnapshot": False}
    if include_device and mode in ("slow", "full"):
        properties = ["ro.product.model", "ro.build.version.release", "ro.build.version.sdk",
                      "ro.build.version.security_patch", "ro.build.fingerprint"]
        result["device"] = {p: attempt(lambda p=p: read("getprop", p).strip()) for p in properties}
        result["installation"] = attempt(lambda: [s.strip() for s in
            read("dumpsys", "package", package).splitlines()
            if re.match(r"\s*(versionCode=|versionName=|firstInstallTime=|lastUpdateTime=|userId=)", s)])
    if mode in ("fast", "full"):
        # Independent read-only queries run concurrently. Each field retains its
        # own read interval; their values must not be treated as simultaneous.
        with ThreadPoolExecutor(max_workers=2) as pool:
            sessions = pool.submit(attempt, lambda: parse_sessions(read(command=sessions_command(package)), package))
            keys_read = pool.submit(attempt, lambda: target_keys(read("cmd", "notification", "list"), package))
            result["sessions"] = sessions.result()
            keys = result["keys"] = keys_read.result()
            result["notifications"] = []
            result["notificationsState"] = "observed" if keys["ok"] else "unknown"
            if keys["ok"]:
                pending = [(key, pool.submit(attempt, lambda key=key:
                    parse_notification(read("cmd", "notification", "get", key["key"]))))
                    for key in keys["value"]]
                for key, pending_read in pending:
                    result["notifications"].append({**key, "metadata": pending_read.result()})
                if any(not row["metadata"]["ok"] for row in result["notifications"]):
                    result["notificationsState"] = "partial_unknown"
    if mode in ("slow", "full"):
        result["deviceTime"] = attempt(lambda: read("date", "+%Y-%m-%dT%H:%M:%S%z").strip())
        result["channelRows"] = attempt(lambda: parse_channels(read(command=channel_command(package))))
    result["durationMs"] = round((time.monotonic() - started) * 1000)
    # Reads are sequential, not an atomic OS snapshot. Preserve the interval.
    result["completedAt"] = dt.datetime.now().astimezone().isoformat(timespec="milliseconds")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--package", default="com.easyswitchcontroller")
    parser.add_argument("--label", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--adb", type=Path, default=DEFAULT_ADB)
    parser.add_argument("--seconds", type=float, default=0)
    parser.add_argument("--interval", type=float, default=0.5)
    parser.add_argument("--mode", choices=("fast", "slow", "full"), default="fast",
                        help="fast: windows/notifications; slow: channel/device metadata; full: both")
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_.]+", args.package):
        parser.error("invalid package identifier")
    if args.interval < 0.5 or args.seconds < 0:
        parser.error("interval must be >= 0.5 and seconds >= 0")
    adb = args.adb if args.adb.exists() else shutil.which("adb")
    if not adb:
        parser.error("adb not found")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    deadline = time.monotonic() + args.seconds
    count = 0
    while True:
        iteration = time.monotonic()
        sample = collect(adb, args.serial, args.package, args.label, count == 0, args.mode)
        sample["requestedIntervalMs"] = round(args.interval * 1000)
        sample["intervalOverrunMs"] = max(0, sample["durationMs"] - sample["requestedIntervalMs"])
        sample["startGapMs"] = None if count == 0 else round((iteration - previous_iteration) * 1000)
        previous_iteration = iteration
        with args.output.open("a", encoding="utf-8") as stream:
            stream.write(json.dumps(sample, ensure_ascii=False) + "\n")
        count += 1
        if time.monotonic() >= deadline:
            break
        time.sleep(max(0, min(deadline - time.monotonic(), args.interval - (time.monotonic() - iteration))))
    print(json.dumps({"samples": count, "output": str(args.output), "mutations": False}))


if __name__ == "__main__":
    main()
