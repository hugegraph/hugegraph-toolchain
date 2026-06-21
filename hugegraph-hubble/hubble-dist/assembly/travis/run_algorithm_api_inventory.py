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
import json
import re
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[4]
FE_ROOT = REPO_ROOT / "hugegraph-hubble" / "hubble-fe" / "src"
BE_ROOT = REPO_ROOT / "hugegraph-hubble" / "hubble-be" / "src" / "main" / "java"


def parse_algorithm_enum():
    source = FE_ROOT / "stores" / "factory" / "dataAnalyzeStore" / "algorithmStore.ts"
    text = source.read_text(encoding="utf-8")
    pattern = re.compile(r"^\s*(\w+)\s*=\s*'([^']+)'", re.MULTILINE)
    return [{"symbol": symbol, "ui_name": value} for symbol, value in pattern.findall(text)]


def parse_frontend_algorithm_urls():
    root = FE_ROOT / "components" / "graph-management" / "data-analyze" / "algorithm"
    urls = {}
    pattern = re.compile(r"url:\s*'([^']+)'.*?type:\s*Algorithm\.(\w+)", re.DOTALL)
    for path in sorted(root.glob("*.tsx")):
        text = path.read_text(encoding="utf-8")
        for url, symbol in pattern.findall(text):
            urls[symbol] = {
                "frontend_url": url,
                "source": str(path.relative_to(REPO_ROOT))
            }
    return urls


def parse_backend_algorithm_endpoints():
    controller = (BE_ROOT / "org" / "apache" / "hugegraph" / "controller" /
                  "algorithm" / "OltpAlgoController.java")
    text = controller.read_text(encoding="utf-8")
    endpoints = []
    for annotation in re.findall(r"@PostMapping\(([^)]*)\)", text):
        endpoints.extend(re.findall(r"\"([^\"]+)\"", annotation))
    return sorted(set(endpoints))


def write_report(path, inventory):
    lines = [
        "# Hubble Algorithm API Inventory",
        "",
        "Generated from source code. This is a boundary report, not live API proof.",
        "",
        "| UI algorithm | Frontend URL | Hubble BE endpoint | Status |",
        "|-|-|-|-|",
    ]
    for item in inventory:
        endpoint = item["backend_endpoint"] or ""
        lines.append("| {ui_name} | {frontend_url} | {endpoint} | {status} |".format(
            ui_name=item["ui_name"],
            frontend_url=item["frontend_url"] or "",
            endpoint=endpoint,
            status=item["status"]
        ))
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description="Inventory Hubble algorithm API boundaries")
    parser.add_argument("--json-output", type=Path,
                        help="Optional path for machine-readable inventory")
    parser.add_argument("--markdown-output", type=Path,
                        help="Optional path for markdown inventory")
    args = parser.parse_args()

    algorithms = parse_algorithm_enum()
    frontend_urls = parse_frontend_algorithm_urls()
    backend_endpoints = parse_backend_algorithm_endpoints()
    backend_endpoint_set = set(backend_endpoints)
    inventory = []

    for algorithm in algorithms:
        symbol = algorithm["symbol"]
        frontend = frontend_urls.get(symbol, {})
        frontend_url = frontend.get("frontend_url")
        backend_endpoint = None
        status = "frontend-listed-without-hubble-be-route"
        if frontend_url in backend_endpoint_set:
            backend_endpoint = frontend_url
            status = "supported-by-hubble-be"
        inventory.append({
            "symbol": symbol,
            "ui_name": algorithm["ui_name"],
            "frontend_url": frontend_url,
            "frontend_source": frontend.get("source"),
            "backend_endpoint": backend_endpoint,
            "status": status
        })

    result = {
        "backend_algorithm_endpoints": backend_endpoints,
        "inventory": inventory,
        "summary": {
            "frontend_algorithm_count": len(algorithms),
            "backend_algorithm_endpoint_count": len(backend_endpoints),
            "supported_by_hubble_be_count": sum(
                1 for item in inventory if item["status"] == "supported-by-hubble-be"
            ),
            "frontend_only_count": sum(
                1 for item in inventory
                if item["status"] == "frontend-listed-without-hubble-be-route"
            )
        }
    }

    if args.json_output:
        args.json_output.parent.mkdir(parents=True, exist_ok=True)
        args.json_output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n",
                                    encoding="utf-8")
    if args.markdown_output:
        args.markdown_output.parent.mkdir(parents=True, exist_ok=True)
        write_report(args.markdown_output, inventory)

    print(json.dumps(result["summary"], sort_keys=True))
    if result["summary"]["supported_by_hubble_be_count"] != 1:
        raise SystemExit("Expected exactly one Hubble BE algorithm endpoint")
    if sorted(backend_endpoints) != ["shortestPath", "shortpath"]:
        raise SystemExit("Unexpected Hubble BE algorithm endpoints: " +
                         ",".join(backend_endpoints))


if __name__ == "__main__":
    main()
