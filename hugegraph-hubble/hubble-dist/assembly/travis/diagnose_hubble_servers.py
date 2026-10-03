#!/usr/bin/env python3
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import argparse
import datetime
import json
import os
import re
import subprocess
import urllib.error
import urllib.request
from pathlib import Path


# Prefer dropping a sensitive log line to guessing where a value ends. Never
# collect configuration, process arguments or environment in the first place.
SENSITIVE = re.compile(
    r"password|passwd|secret|token|authorization|cookie|private[_ -]?key|"
    r"auth[._]admin_pa|://[^/\s]+@|JAVA_OPTS|JAVA_OPTIONS|JAVA_TOOL_OPTIONS|"
    r"JDK_JAVA_OPTIONS|CLASSPATH|sun[.]java[.]command|"
    r"^[A-Za-z0-9+/]{48,}={0,2}$",
    re.IGNORECASE,
)


def redact(text):
    lines = []
    private_key = False
    continuation = False
    for line in text.splitlines():
        if re.search(r"-----BEGIN .*PRIVATE KEY-----", line):
            private_key = True
        if private_key:
            if re.search(r"-----END .*PRIVATE KEY-----", line):
                private_key = False
            lines.append("[REDACTED]")
        elif continuation or SENSITIVE.search(line):
            continuation = bool(re.search(r"[:=]\s*$", line))
            lines.append("[REDACTED]")
        else:
            lines.append(line)
    return "\n".join(lines) + "\n"


def command(args):
    try:
        result = subprocess.run(args, capture_output=True, text=True,
                                timeout=5, check=False)
        return {"exit": result.returncode, "stdout": result.stdout[-16384:]}
    except (OSError, subprocess.TimeoutExpired) as error:
        return {"unavailable": type(error).__name__}


def tail(path):
    try:
        with path.open("rb") as stream:
            offset = max(0, path.stat().st_size - 65536)
            stream.seek(offset)
            content = stream.read(65536).decode("utf-8", errors="replace")
        if offset:
            # The first fragment may lack a credential key outside the bound.
            content = content.partition("\n")[2]
        return "\n".join(redact(content).splitlines()[-200:]) + "\n"
    except OSError as error:
        return "unavailable: " + type(error).__name__ + "\n"


def memory_events(pid=None):
    root = Path("/sys/fs/cgroup")
    locations = {root}
    if pid:
        try:
            for line in Path(f"/proc/{pid}/cgroup").read_text().splitlines():
                hierarchy, controllers, relative = line.split(":", 2)
                if hierarchy == "0" and not controllers:
                    location = (root / relative.lstrip("/")).resolve()
                    if location == root or root in location.parents:
                        locations.add(location)
        except (OSError, ValueError):
            pass
    data = {}
    for location in locations:
        for name in ("memory.current", "memory.max", "memory.events"):
            try:
                data[str((location / name).relative_to(root))] = (
                    location / name).read_text()[:4096]
            except OSError:
                pass
    return data


def snapshot(root, stage, output):
    output.mkdir(parents=True, exist_ok=True)
    data = {"stage": stage, "utc": datetime.datetime.now(
        datetime.timezone.utc).isoformat(), "servers": [],
        "memory": memory_events()}
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    for number, port in ((1, 8080), (2, 8081)):
        server = {"instance": number, "port": port}
        homes = list((root / f"hugegraph-server{number}").glob("apache-hugegraph-*"))
        for home in homes:
            try:
                pid_text = (home / "bin/pid").read_text().strip()
                if pid_text.isdigit() and int(pid_text) > 0:
                    pid = int(pid_text)
                    server["pid"] = pid
                    try:
                        os.kill(pid, 0)
                        server["pid_exists"] = True
                    except ProcessLookupError:
                        server["pid_exists"] = False
                    except PermissionError:
                        server["pid_exists"] = "permission denied"
                    server["process"] = command([
                        "ps", "-p", str(pid), "-o", "pid,ppid,stat,rss,vsz,etime,comm"])
                    server["memory"] = memory_events(pid)
            except OSError:
                server["pid_file"] = "unavailable"
            for name in ("hugegraph-server.log", "hugegraph-server-stdout.log"):
                (output / f"{stage}-server{number}-{name}").write_text(
                    tail(home / "logs" / name), encoding="utf-8")
        try:
            request = urllib.request.Request(
                f"http://127.0.0.1:{port}/graphs", method="HEAD")
            with opener.open(request, timeout=3) as response:
                server["http_status"] = response.status
        except urllib.error.HTTPError as error:
            server["http_status"] = error.code
        except (OSError, urllib.error.URLError) as error:
            server["http_error"] = type(error).__name__
            reason = getattr(error, "reason", error)
            server["connection_error"] = type(reason).__name__
        data["servers"].append(server)
    sockets = command(["ss", "-ltn"])
    if "stdout" in sockets:
        sockets["stdout"] = "\n".join(
            line for line in sockets["stdout"].splitlines()
            if re.search(r":(?:8080|8081|8182|8183)\b", line))
    data["listeners"] = sockets
    (output / f"{stage}.json").write_text(
        json.dumps(data, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("stage", choices=("after-startup", "after-audit", "after-unit", "final"))
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--output", type=Path,
                        default=Path("hugegraph-hubble/target/hubble-live-acceptance/server-diagnostics"))
    args = parser.parse_args()
    snapshot(args.root, args.stage, args.output)
