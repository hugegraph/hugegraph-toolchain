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
import gzip
import json
import os
import shutil
import subprocess
import tarfile
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path


def request(method, url, body=None, headers=None, timeout=10):
    data = None
    merged_headers = {"Accept-Encoding": "identity", **(headers or {})}
    if body is not None:
        if isinstance(body, bytes):
            data = body
        else:
            data = json.dumps(body).encode("utf-8")
            merged_headers = {"Content-Type": "application/json",
                              **merged_headers}
    req = urllib.request.Request(url, data=data, headers=merged_headers,
                                 method=method)
    with urllib.request.urlopen(req, timeout=timeout) as response:
        raw_payload = response.read()
        if response.headers.get("Content-Encoding") == "gzip":
            raw_payload = gzip.decompress(raw_payload)
        payload = raw_payload.decode("utf-8")
        content_type = response.headers.get("Content-Type", "")
    if ("application/json" in content_type or "+json" in content_type) and payload:
        return json.loads(payload)
    return payload


def unwrap(response, name):
    if not isinstance(response, dict) or response.get("status") != 200:
        raise RuntimeError(f"{name} failed: {response}")
    return response.get("data")


def server_json(method, server_url, path, body=None, timeout=10):
    return request(method, f"{server_url}{path}", body, timeout=timeout)


def wait_for_health(hubble_url, deadline_seconds):
    deadline = time.time() + deadline_seconds
    last_error = None
    while time.time() < deadline:
        try:
            response = request("GET", f"{hubble_url}/actuator/health", timeout=3)
            if isinstance(response, dict) and response.get("status") == "UP":
                return response
        except Exception as exc:  # noqa: BLE001 - smoke tool should report final error
            last_error = exc
        time.sleep(1)
    raise RuntimeError(f"Hubble health did not become UP: {last_error}")


def extract_tarball(tarball, work_dir):
    with tarfile.open(tarball, "r:gz") as archive:
        archive.extractall(work_dir)
    homes = [path for path in work_dir.iterdir()
             if path.is_dir() and path.name.startswith("apache-hugegraph-hubble-")]
    if not homes:
        raise RuntimeError(f"Unable to find Hubble home under {work_dir}")
    return homes[0]


def configure_hubble_bind_host(hubble_home, bind_host):
    if not bind_host:
        return
    conf = hubble_home / "conf" / "hugegraph-hubble.properties"
    text = conf.read_text(encoding="utf-8")
    lines = []
    replaced = False
    for line in text.splitlines():
        if line.startswith("hubble.host="):
            lines.append(f"hubble.host={bind_host}")
            replaced = True
        else:
            lines.append(line)
    if not replaced:
        lines.append(f"hubble.host={bind_host}")
    conf.write_text("\n".join(lines) + "\n", encoding="utf-8")


def create_connection(hubble_url, server_url, graph_name, connection_name):
    parsed = urllib.parse.urlparse(server_url)
    port = parsed.port or (443 if parsed.scheme == "https" else 80)
    existing = request(
        "GET",
        f"{hubble_url}/api/v1.2/graph-connections?page_no=1&page_size=100"
    )
    records = ((existing.get("data") or {}).get("records") or []
               if isinstance(existing, dict) else [])
    for record in records:
        if (record.get("graph") == graph_name and
                record.get("host") == parsed.hostname and
                record.get("port") == port):
            return record.get("id")
    body = {
        "name": connection_name,
        "graph": graph_name,
        "host": parsed.hostname,
        "port": port,
        "username": "",
        "password": "",
        "protocol": parsed.scheme or "http"
    }
    data = unwrap(request("POST", f"{hubble_url}/api/v1.2/graph-connections",
                          body),
                  "create Hubble connection")
    conn_id = data.get("id")
    if conn_id is None:
        raise RuntimeError(f"Create Hubble connection returned no id: {data}")
    return conn_id


def run_hubble_only_checks(hubble_url):
    checks = []
    health = request("GET", f"{hubble_url}/actuator/health")
    checks.append({"name": "hubble-health", "status": "passed", "detail": health})
    root = request("GET", f"{hubble_url}/")
    if '<div id="root"></div>' not in root:
        raise RuntimeError("Hubble root did not serve React root")
    checks.append({"name": "hubble-ui-root", "status": "passed"})
    for route in (
        "/graph-management",
        "/graph-management/1/metadata-configs",
        "/graph-management/1/data-import/import-manager",
        "/graph-management/1/data-analyze",
        "/graph-management/1/async-tasks",
    ):
        body = request("GET", f"{hubble_url}{route}")
        if '<div id="root"></div>' not in body:
            raise RuntimeError(f"Route did not serve React root: {route}")
        checks.append({"name": f"route:{route}", "status": "passed"})
    return checks


def run_server_checks(hubble_url, server_url, graph_name, connection_name):
    checks = []
    versions = request("GET", f"{server_url}/versions")
    checks.append({"name": "server-versions", "status": "passed",
                   "detail_type": type(versions).__name__})
    conn_id = create_connection(hubble_url, server_url, graph_name,
                                connection_name)
    checks.append({"name": "hubble-create-graph-connection", "status": "passed",
                   "conn_id": conn_id})
    for name, path in (
        ("schema-graphview", "schema/graphview"),
        ("job-manager-list", "job-manager?page_no=1&page_size=10"),
        ("async-task-list", "async-tasks?page_no=1&page_size=10"),
    ):
        response = request("GET", f"{hubble_url}/api/v1.2/graph-connections/{conn_id}/{path}")
        if response.get("status") != 200:
            raise RuntimeError(f"{name} failed: {response}")
        checks.append({"name": name, "status": "passed"})
    return checks


def get_json_status(method, url, body=None):
    try:
        response = request(method, url, body)
        if isinstance(response, dict):
            return response.get("status", 200)
        return 200
    except urllib.error.HTTPError as exc:
        try:
            payload = exc.read().decode("utf-8")
            response = json.loads(payload)
            return response.get("status", exc.code)
        except (UnicodeDecodeError, json.JSONDecodeError):
            return exc.code


def run_analysis_boundary_checks(hubble_url, graph_space, graph_name):
    base = f"{hubble_url}/api/v1.3/graphspaces/{graph_space}/graphs/{graph_name}"
    checks = []

    cypher_status = get_json_status(
        "GET",
        f"{base}/cypher?cypher={urllib.parse.quote('MATCH (n) RETURN n LIMIT 1')}"
    )
    checks.append({
        "name": "analysis-cypher-boundary",
        "status": "passed",
        "http_or_business_status": cypher_status,
        "classification": ("hubble-api-available"
                           if cypher_status == 200 else
                           "boundary-or-environment-dependent")
    })

    olap_status = get_json_status("POST", f"{base}/algorithms/olap", {
        "algorithm": "pagerank",
        "worker": 1,
        "params": {}
    })
    checks.append({
        "name": "analysis-olap-boundary",
        "status": "passed",
        "http_or_business_status": olap_status,
        "classification": ("hubble-api-available"
                           if olap_status == 200 else
                           "boundary-or-environment-dependent")
    })

    return checks


def ignore_conflict(call):
    try:
        return call()
    except urllib.error.HTTPError as exc:
        if exc.code == 400:
            return None
        raise


def create_schema(hubble_url, server_url, graph_name, conn_id, prefix):
    base = f"{hubble_url}/api/v1.2/graph-connections/{conn_id}/schema"
    pk_id = f"{prefix}_id"
    pk_name = f"{prefix}_name"
    pk_rank = f"{prefix}_rank"
    vl_person = f"{prefix}_person"
    el_knows = f"{prefix}_knows"

    checks = []
    for name, data_type in ((pk_id, "TEXT"), (pk_name, "TEXT"),
                            (pk_rank, "INT")):
        body = {"name": name, "data_type": data_type, "cardinality": "SINGLE"}
        response = ignore_conflict(
            lambda body=body: request("POST", f"{base}/propertykeys", body)
        )
        if response is not None:
            unwrap(response, f"create property key {name}")
    checks.append({"name": "hubble-schema-propertykeys", "status": "passed"})

    vertex_body = {
        "name": vl_person,
        "id_strategy": "CUSTOMIZE_STRING",
        "properties": [
            {"name": pk_name, "nullable": True},
            {"name": pk_rank, "nullable": True}
        ],
        "primary_keys": [],
        "property_indexes": [],
        "open_label_index": True,
        "style": {"color": "#2B65FF", "icon": "user",
                  "display_fields": []}
    }
    response = ignore_conflict(
        lambda: request("POST", f"{base}/vertexlabels", vertex_body)
    )
    if response is not None:
        unwrap(response, f"create vertex label {vl_person}")
    checks.append({"name": "hubble-schema-vertexlabel", "status": "passed",
                   "label": vl_person})

    edge_body = {
        "name": el_knows,
        "source_label": vl_person,
        "target_label": vl_person,
        "link_multi_times": False,
        "properties": [],
        "sort_keys": [],
        "property_indexes": [],
        "open_label_index": True,
        "style": {"color": "#0EB880", "display_fields": []}
    }
    response = ignore_conflict(
        lambda: request("POST", f"{base}/edgelabels", edge_body)
    )
    if response is not None:
        unwrap(response, f"create edge label {el_knows}")
    checks.append({"name": "hubble-schema-edgelabel", "status": "passed",
                   "label": el_knows})

    direct = server_json("GET", server_url,
                         f"/graphs/{graph_name}/schema/vertexlabels/{vl_person}")
    if direct.get("name") != vl_person:
        raise RuntimeError(f"Direct Server schema check failed: {direct}")
    checks.append({"name": "server-direct-schema-vertexlabel",
                   "status": "passed", "label": vl_person})
    return checks, {
        "pk_id": pk_id,
        "pk_name": pk_name,
        "pk_rank": pk_rank,
        "vl_person": vl_person,
        "el_knows": el_knows
    }


def encode_multipart(fields, file_field, file_name, content):
    boundary = "----hubble694" + uuid.uuid4().hex
    parts = []
    for name, value in fields.items():
        parts.append(
            f"--{boundary}\r\n"
            f"Content-Disposition: form-data; name=\"{name}\"\r\n\r\n"
            f"{value}\r\n".encode("utf-8")
        )
    parts.append(
        f"--{boundary}\r\n"
        f"Content-Disposition: form-data; name=\"{file_field}\"; "
        f"filename=\"{file_name}\"\r\n"
        "Content-Type: text/csv\r\n\r\n".encode("utf-8")
    )
    parts.append(content)
    parts.append(f"\r\n--{boundary}--\r\n".encode("utf-8"))
    return b"".join(parts), f"multipart/form-data; boundary={boundary}"


def upload_csv(hubble_url, conn_id, prefix, schema):
    job = unwrap(request("POST",
                         f"{hubble_url}/api/v1.2/graph-connections/{conn_id}/job-manager",
                         {"job_name": f"{prefix}_job",
                          "job_remarks": "issue_694_live_loader_smoke"}),
                 "create loader job")
    job_id = job["id"]
    file_name = f"{prefix}_edges.csv"
    csv_text = (
        f"source,target,{schema['pk_name']},{schema['pk_rank']}\n"
        f"{prefix}_alice,{prefix}_bob,Alice,1\n"
        f"{prefix}_bob,{prefix}_carol,Bob,2\n"
    )
    token_map = unwrap(request(
        "GET",
        f"{hubble_url}/api/v1.2/graph-connections/{conn_id}/job-manager/"
        f"{job_id}/upload-file/token?names={urllib.parse.quote(file_name)}"
    ), "create upload token")
    token = token_map[file_name]
    multipart_body, content_type = encode_multipart(
        {
            "name": file_name,
            "size": str(len(csv_text.encode("utf-8"))),
            "token": token,
            "total": "1",
            "index": "0"
        },
        "file",
        file_name,
        csv_text.encode("utf-8")
    )
    upload = unwrap(request(
        "POST",
        f"{hubble_url}/api/v1.2/graph-connections/{conn_id}/job-manager/"
        f"{job_id}/upload-file",
        multipart_body,
        {"Content-Type": content_type}
    ), "upload loader csv")
    file_id = upload["id"]
    unwrap(request(
        "PUT",
        f"{hubble_url}/api/v1.2/graph-connections/{conn_id}/job-manager/"
        f"{job_id}/upload-file/next-step"
    ), "advance upload step")
    return job_id, file_id


def configure_mapping(hubble_url, conn_id, job_id, file_id, schema):
    base = (f"{hubble_url}/api/v1.2/graph-connections/{conn_id}/job-manager/"
            f"{job_id}/file-mappings")
    mapping = unwrap(request("POST", f"{base}/{file_id}/file-setting", {
        "has_header": True,
        "format": "CSV",
        "delimiter": ",",
        "charset": "UTF-8",
        "date_format": "yyyy-MM-dd HH:mm:ss",
        "time_zone": "GMT+8",
        "skipped_line": "(^#|^//).*|"
    }), "configure file setting")
    if "source" not in mapping["file_setting"]["column_names"]:
        raise RuntimeError(f"File setting did not read CSV columns: {mapping}")

    null_values = {"checked": ["", "NULL", "null"], "customized": []}
    unwrap(request("POST", f"{base}/{file_id}/vertex-mappings", {
        "label": schema["vl_person"],
        "id_fields": ["source"],
        "field_mapping": [
            {"column_name": schema["pk_name"], "mapped_name": schema["pk_name"]},
            {"column_name": schema["pk_rank"], "mapped_name": schema["pk_rank"]}
        ],
        "value_mapping": [],
        "null_values": null_values
    }), "add source vertex mapping")
    unwrap(request("POST", f"{base}/{file_id}/vertex-mappings", {
        "label": schema["vl_person"],
        "id_fields": ["target"],
        "field_mapping": [],
        "value_mapping": [],
        "null_values": null_values
    }), "add target vertex mapping")
    unwrap(request("POST", f"{base}/{file_id}/edge-mappings", {
        "label": schema["el_knows"],
        "source_fields": ["source"],
        "target_fields": ["target"],
        "field_mapping": [],
        "value_mapping": [],
        "null_values": null_values
    }), "add edge mapping")
    unwrap(request("PUT", f"{base}/next-step"), "advance mapping step")
    unwrap(request("POST", f"{base}/load-parameter", {
        "check_vertex": False,
        "insert_timeout": 60,
        "max_parse_errors": 1,
        "max_insert_errors": 1,
        "retry_times": 1,
        "retry_interval": 1
    }), "configure load parameters")


def wait_for_load_task(hubble_url, conn_id, job_id, file_id):
    base = (f"{hubble_url}/api/v1.2/graph-connections/{conn_id}/job-manager/"
            f"{job_id}/load-tasks")
    tasks = unwrap(request("POST", f"{base}/start?file_mapping_ids={file_id}",
                           {}),
                   "start load task")
    if not tasks:
        raise RuntimeError("Load task start returned no tasks")
    task_id = tasks[0]["id"]
    deadline = time.time() + 90
    last_task = tasks[0]
    while time.time() < deadline:
        last_task = unwrap(request("GET", f"{base}/{task_id}"),
                           "poll load task")
        status = last_task.get("status")
        if status in ("SUCCEED", "FAILED", "STOPPED", "PAUSED"):
            break
        time.sleep(1)
    if last_task.get("status") != "SUCCEED":
        raise RuntimeError(f"Load task did not succeed: {last_task}")
    job = unwrap(request(
        "GET",
        f"{hubble_url}/api/v1.2/graph-connections/{conn_id}/job-manager/{job_id}"
    ), "poll loader job")
    return task_id, last_task, job


def first_table_value(gremlin_result):
    table = gremlin_result.get("table_view") or gremlin_result.get("tableView")
    if not table:
        raise RuntimeError(f"No table view in gremlin result: {gremlin_result}")
    rows = table.get("data") or table.get("rows") or []
    if not rows:
        raise RuntimeError(f"No rows in gremlin result: {gremlin_result}")
    row = rows[0]
    if isinstance(row, dict):
        return next(iter(row.values()))
    if isinstance(row, list):
        return row[0]
    return row


def run_hubble_gremlin(hubble_url, conn_id, content):
    return unwrap(request(
        "POST",
        f"{hubble_url}/api/v1.2/graph-connections/{conn_id}/gremlin-query",
        {"content": content}
    ), f"Hubble Gremlin {content}")


def run_server_gremlin_count(server_url, content):
    response = server_json("POST", server_url, "/gremlin", {"gremlin": content})
    try:
        return int(response["result"]["data"][0])
    except (KeyError, IndexError, TypeError, ValueError) as exc:
        raise RuntimeError(f"Unexpected Server Gremlin response: {response}") from exc


def run_live_function_flow(hubble_url, server_url, graph_name, conn_id, prefix):
    checks = []
    schema_checks, schema = create_schema(hubble_url, server_url, graph_name,
                                          conn_id, prefix)
    checks.extend(schema_checks)
    job_id, file_id = upload_csv(hubble_url, conn_id, prefix, schema)
    checks.append({"name": "hubble-upload-csv", "status": "passed",
                   "job_id": job_id, "file_mapping_id": file_id})
    configure_mapping(hubble_url, conn_id, job_id, file_id, schema)
    checks.append({"name": "hubble-file-mapping", "status": "passed",
                   "file_mapping_id": file_id})
    task_id, task, job = wait_for_load_task(hubble_url, conn_id, job_id, file_id)
    checks.append({"name": "hubble-loader-task", "status": "passed",
                   "task_id": task_id, "task_status": task.get("status"),
                   "job_status": job.get("job_status")})

    vertex_gremlin = (f"g.V().hasLabel('{schema['vl_person']}')."
                      f"hasId(within('{prefix}_alice','{prefix}_bob',"
                      f"'{prefix}_carol')).count()")
    edge_gremlin = (f"g.E().hasLabel('{schema['el_knows']}').count()")
    h_vertices = int(first_table_value(run_hubble_gremlin(hubble_url, conn_id,
                                                          vertex_gremlin)))
    h_edges = int(first_table_value(run_hubble_gremlin(hubble_url, conn_id,
                                                       edge_gremlin)))
    server_vertex_gremlin = (
        f"hugegraph.traversal().V().hasLabel('{schema['vl_person']}')."
        f"hasId(within('{prefix}_alice','{prefix}_bob',"
        f"'{prefix}_carol')).count()"
    )
    server_edge_gremlin = (
        f"hugegraph.traversal().E().hasLabel('{schema['el_knows']}').count()"
    )
    d_vertices = run_server_gremlin_count(server_url, server_vertex_gremlin)
    d_edges = run_server_gremlin_count(server_url, server_edge_gremlin)
    if (h_vertices, h_edges) != (d_vertices, d_edges):
        raise RuntimeError("Hubble and Server Gremlin counts differ: "
                           f"{(h_vertices, h_edges)} != {(d_vertices, d_edges)}")
    checks.append({"name": "hubble-server-gremlin-count-compare",
                   "status": "passed", "vertices": h_vertices,
                   "edges": h_edges})

    shortest_body = {
        "source": f"{prefix}_alice",
        "target": f"{prefix}_carol",
        "direction": "OUT",
        "label": schema["el_knows"],
        "max_depth": 3,
        "max_degree": 1000,
        "skip_degree": 0,
        "capacity": 10000
    }
    hubble_shortest = unwrap(request(
        "POST",
        f"{hubble_url}/api/v1.2/graph-connections/{conn_id}/algorithms/shortpath",
        shortest_body
    ), "Hubble shortestPath")
    direct_shortest = server_json(
        "GET", server_url,
        f"/graphs/{graph_name}/traversers/shortestpath"
        f"?source={urllib.parse.quote(json.dumps(prefix + '_alice'))}"
        f"&target={urllib.parse.quote(json.dumps(prefix + '_carol'))}"
        f"&direction=OUT&label={urllib.parse.quote(schema['el_knows'])}"
        f"&max_depth=3&max_degree=1000&skip_degree=0&capacity=10000"
    )
    graph_view = (hubble_shortest.get("graph_view") or
                  hubble_shortest.get("graphView") or {})
    vertices = graph_view.get("vertices") or []
    edges = graph_view.get("edges") or []
    if len(vertices) < 3 or len(edges) < 2:
        raise RuntimeError(f"Hubble shortestPath graph view incomplete: "
                           f"{hubble_shortest}")
    direct_vertices = direct_shortest.get("vertices") or []
    direct_edges = direct_shortest.get("edges") or []
    direct_path = direct_shortest.get("path") or []
    if len(direct_vertices) < 3 or len(direct_edges) < 2 or len(direct_path) < 3:
        raise RuntimeError(f"Server direct shortestPath returned no path: "
                           f"{direct_shortest}")
    checks.append({"name": "hubble-server-shortestpath-compare",
                   "status": "passed", "hubble_vertices": len(vertices),
                   "hubble_edges": len(edges),
                   "server_vertices": len(direct_vertices),
                   "server_edges": len(direct_edges),
                   "server_path_objects": len(direct_path)})
    return checks


def main():
    parser = argparse.ArgumentParser(description="Run live Hubble issue #694 smoke")
    parser.add_argument("tarball", type=Path)
    parser.add_argument("--server-url", default="http://127.0.0.1:8080")
    parser.add_argument("--hubble-url", default=os.environ.get("HUBBLE_URL",
                                                              "http://127.0.0.1:8088"))
    parser.add_argument("--graph", default=os.environ.get("HUGEGRAPH_GRAPH", "hugegraph"))
    parser.add_argument("--work-dir", type=Path)
    parser.add_argument("--keep-work-dir", action="store_true")
    parser.add_argument("--skip-start", action="store_true")
    parser.add_argument("--foreground-start", action="store_true")
    parser.add_argument("--bind-host")
    parser.add_argument("--loader-flow", action="store_true")
    parser.add_argument("--analysis-boundary", action="store_true")
    parser.add_argument("--graphspace", default=os.environ.get("HUGEGRAPH_GRAPHSPACE",
                                                              "DEFAULT"))
    parser.add_argument("--connection-name")
    parser.add_argument("--data-prefix")
    parser.add_argument("--json-output", type=Path)
    args = parser.parse_args()

    hubble_url = args.hubble_url.rstrip("/")
    server_url = args.server_url.rstrip("/")
    work_dir = args.work_dir or Path(tempfile.mkdtemp(prefix="hubble-694-live-"))
    created_work_dir = args.work_dir is None
    hubble_home = None
    process = None
    log_handle = None
    hubble_log = None
    report = {
        "tarball": str(args.tarball),
        "hubble_url": hubble_url,
        "server_url": server_url,
        "graph": args.graph,
        "checks": [],
        "status": "failed"
    }
    prefix = args.data_prefix or f"issue_694_{int(time.time())}"
    connection_name = args.connection_name or f"{prefix}_conn"
    report["data_prefix"] = prefix
    report["connection_name"] = connection_name

    try:
        if not args.skip_start:
            work_dir.mkdir(parents=True, exist_ok=True)
            hubble_home = extract_tarball(args.tarball, work_dir)
            configure_hubble_bind_host(hubble_home, args.bind_host)
            hubble_log = work_dir / "hubble-live-smoke.log"
            log_handle = hubble_log.open("w", encoding="utf-8")
            if args.foreground_start:
                process = subprocess.Popen(
                    [str(hubble_home / "bin" / "start-hubble.sh"), "-f"],
                    cwd=str(hubble_home),
                    stdout=log_handle,
                    stderr=subprocess.STDOUT,
                    text=True
                )
            else:
                result = subprocess.run(
                    [str(hubble_home / "bin" / "start-hubble.sh")],
                    cwd=str(hubble_home),
                    stdout=log_handle,
                    stderr=subprocess.STDOUT,
                    text=True,
                    check=False
                )
                if result.returncode != 0:
                    raise RuntimeError("Hubble start script failed with "
                                       f"exit code {result.returncode}")
            wait_for_health(hubble_url, 90)
        else:
            wait_for_health(hubble_url, 10)

        report["checks"].extend(run_hubble_only_checks(hubble_url))
        report["checks"].extend(run_server_checks(hubble_url, server_url,
                                                  args.graph, connection_name))

        if args.loader_flow:
            conn_id = next(check["conn_id"] for check in report["checks"]
                           if check["name"] == "hubble-create-graph-connection")
            report["checks"].extend(run_live_function_flow(hubble_url,
                                                           server_url,
                                                           args.graph,
                                                           conn_id,
                                                           prefix))
        if args.analysis_boundary:
            report["checks"].extend(run_analysis_boundary_checks(hubble_url,
                                                                 args.graphspace,
                                                                 args.graph))

        report["status"] = "passed"
    except (urllib.error.URLError, RuntimeError) as exc:
        report["error"] = str(exc)
        if hubble_log is not None and hubble_log.exists():
            report["hubble_log_tail"] = "\n".join(
                hubble_log.read_text(encoding="utf-8", errors="replace")
                          .splitlines()[-80:]
            )
        raise SystemExit(json.dumps(report, indent=2, sort_keys=True))
    finally:
        if process is not None:
            process.terminate()
            try:
                process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                process.kill()
        if log_handle is not None:
            log_handle.close()
        if hubble_home is not None:
            subprocess.run([str(hubble_home / "bin" / "stop-hubble.sh")],
                           stdout=subprocess.DEVNULL,
                           stderr=subprocess.DEVNULL,
                           check=False)
        if args.json_output:
            args.json_output.parent.mkdir(parents=True, exist_ok=True)
            args.json_output.write_text(json.dumps(report, indent=2,
                                                   sort_keys=True) + "\n",
                                        encoding="utf-8")
        if created_work_dir and not args.keep_work_dir:
            shutil.rmtree(work_dir, ignore_errors=True)

    print(json.dumps(report, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
