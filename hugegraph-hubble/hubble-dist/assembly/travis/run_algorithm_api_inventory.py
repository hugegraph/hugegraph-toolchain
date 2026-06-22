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


def parse_algorithm_names():
    source = FE_ROOT / "utils" / "constants.js"
    text = source.read_text(encoding="utf-8")
    match = re.search(r"export const ALGORITHM_NAME = \{(?P<body>.*?)\};", text,
                      re.DOTALL)
    if not match:
        raise RuntimeError("Unable to find ALGORITHM_NAME in constants.js")
    pattern = re.compile(r"^\s*(\w+):\s*'([^']+)'", re.MULTILINE)
    return {symbol: name for symbol, name in pattern.findall(match.group("body"))}


def parse_frontend_algorithm_urls(algorithm_names):
    source = FE_ROOT / "utils" / "constants.js"
    text = source.read_text(encoding="utf-8")
    match = re.search(r"export const Algorithm_Url = \{(?P<body>.*?)\};", text,
                      re.DOTALL)
    if not match:
        raise RuntimeError("Unable to find Algorithm_Url in constants.js")
    pattern = re.compile(r"\[ALGORITHM_NAME\.(\w+)\]:\s*'([^']+)'")
    urls = []
    seen = set()
    for symbol, url in pattern.findall(match.group("body")):
        key = (symbol, url)
        if key in seen:
            continue
        seen.add(key)
        urls.append({
            "symbol": symbol,
            "ui_name": algorithm_names.get(symbol, symbol),
            "frontend_url": url,
            "source": str(source.relative_to(REPO_ROOT))
        })
    return urls


def parse_backend_algorithm_endpoints():
    controller = (BE_ROOT / "org" / "apache" / "hugegraph" / "controller" /
                  "algorithm" / "OltpAlgoController.java")
    text = controller.read_text(encoding="utf-8")
    endpoints = []
    for annotation in re.findall(r"@PostMapping\(([^)]*)\)", text):
        endpoints.extend(re.findall(r"\"([^\"]+)\"", annotation))
    return sorted(set(endpoints))


def controller_has_mapping(controller_name, pattern):
    controller = (BE_ROOT / "org" / "apache" / "hugegraph" / "controller" /
                  controller_name)
    text = controller.read_text(encoding="utf-8")
    return re.search(pattern, text, re.DOTALL) is not None


def write_report(path, inventory, boundary):
    lines = [
        "# Hubble Algorithm API Inventory",
        "",
        "Generated from source code. This classifies FE algorithm slugs against "
        "Hubble BE controller routes; it is not live API proof.",
        "",
        "| UI algorithm | Frontend slug | Hubble BE endpoint | Status |",
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
    lines.extend([
        "",
        "## Boundary Routes",
        "",
        "| Area | Hubble route present | Verification scope |",
        "|-|-|-|",
    ])
    for item in boundary:
        lines.append("| {area} | {present} | {scope} |".format(
            area=item["area"],
            present="yes" if item["route_present"] else "no",
            scope=item["verification_scope"]
        ))
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description="Inventory Hubble algorithm API boundaries")
    parser.add_argument("--json-output", type=Path,
                        help="Optional path for machine-readable inventory")
    parser.add_argument("--markdown-output", type=Path,
                        help="Optional path for markdown inventory")
    args = parser.parse_args()

    algorithm_names = parse_algorithm_names()
    frontend_urls = parse_frontend_algorithm_urls(algorithm_names)
    backend_endpoints = parse_backend_algorithm_endpoints()
    backend_endpoint_set = set(backend_endpoints)
    inventory = []

    for frontend in frontend_urls:
        frontend_url = frontend.get("frontend_url")
        backend_endpoint = None
        status = "frontend-listed-without-hubble-be-route"
        if frontend_url in backend_endpoint_set:
            backend_endpoint = frontend_url
            status = "supported-by-hubble-be"
        inventory.append({
            "symbol": frontend["symbol"],
            "ui_name": frontend["ui_name"],
            "frontend_url": frontend_url,
            "frontend_source": frontend.get("source"),
            "backend_endpoint": backend_endpoint,
            "status": status
        })

    frontend_url_set = {item["frontend_url"] for item in frontend_urls}
    backend_only = [
        endpoint for endpoint in backend_endpoints
        if endpoint not in frontend_url_set
    ]
    boundary = [
        {
            "area": "OLTP algorithms",
            "route_present": len(backend_endpoints) > 0,
            "verification_scope": ("source inventory for all routes; "
                                   "live smoke covers shortestPath")
        },
        {
            "area": "OLAP algorithms",
            "route_present": controller_has_mapping(
                "algorithm/OlapAlgoController.java",
                r"algorithms/olap"
            ),
            "verification_scope": ("source route inventory only; live execution "
                                   "depends on computer backend configuration")
        },
        {
            "area": "Cypher",
            "route_present": controller_has_mapping(
                "query/CypherController.java",
                r"/\{graph\}/cypher"
            ),
            "verification_scope": "source route inventory plus optional live smoke"
        },
    ]

    result = {
        "backend_algorithm_endpoints": backend_endpoints,
        "backend_only_endpoints": backend_only,
        "boundary_routes": boundary,
        "inventory": inventory,
        "summary": {
            "frontend_algorithm_count": len(frontend_urls),
            "backend_algorithm_endpoint_count": len(backend_endpoints),
            "supported_by_hubble_be_count": sum(
                1 for item in inventory if item["status"] == "supported-by-hubble-be"
            ),
            "frontend_only_count": sum(
                1 for item in inventory
                if item["status"] == "frontend-listed-without-hubble-be-route"
            ),
            "backend_only_count": len(backend_only)
        }
    }

    if args.json_output:
        args.json_output.parent.mkdir(parents=True, exist_ok=True)
        args.json_output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n",
                                    encoding="utf-8")
    if args.markdown_output:
        args.markdown_output.parent.mkdir(parents=True, exist_ok=True)
        write_report(args.markdown_output, inventory, boundary)

    print(json.dumps(result["summary"], sort_keys=True))
    if result["summary"]["frontend_algorithm_count"] == 0:
        raise SystemExit("No FE algorithm URLs found")
    if result["summary"]["backend_algorithm_endpoint_count"] == 0:
        raise SystemExit("No Hubble BE algorithm endpoints found")
    if result["summary"]["frontend_only_count"] > 0:
        raise SystemExit("Some FE algorithm slugs have no Hubble BE route")
    if not all(item["route_present"] for item in boundary):
        raise SystemExit("Some Hubble analysis boundary routes are missing")


if __name__ == "__main__":
    main()
