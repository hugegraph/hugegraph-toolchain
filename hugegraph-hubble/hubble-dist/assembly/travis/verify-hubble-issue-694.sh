#!/bin/bash
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
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
    echo "Usage: $0 <apache-hugegraph-hubble-*.tar.gz> [server-url]" >&2
    exit 1
fi

tarball=$1
server_url=${2:-http://127.0.0.1:8080}
server_url=${server_url%/}
hubble_url=${HUBBLE_URL:-http://127.0.0.1:8088}
hubble_url=${hubble_url%/}
graph_name=${HUGEGRAPH_GRAPH:-hugegraph}
work_dir=${HUBBLE_694_WORK_DIR:-$(mktemp -d)}
cleanup_work_dir=${HUBBLE_694_KEEP_WORK_DIR:-false}
hubble_home=""

server_protocol=${server_url%%://*}
server_address=${server_url#*://}
if [[ "${server_protocol}" == "${server_url}" ]]; then
    server_protocol=http
    server_address=${server_url}
fi
server_address=${server_address%%/*}
server_host=${server_address%%:*}
server_port=${server_address##*:}
if [[ "${server_port}" == "${server_address}" ]]; then
    if [[ "${server_protocol}" == "https" ]]; then
        server_port=443
    else
        server_port=80
    fi
fi

cleanup() {
    if [[ -n "${hubble_home}" && -x "${hubble_home}/bin/stop-hubble.sh" ]]; then
        "${hubble_home}/bin/stop-hubble.sh" >/dev/null 2>&1 || true
    fi
    if [[ "${cleanup_work_dir}" != "true" ]]; then
        rm -rf "${work_dir}"
    fi
}
trap cleanup EXIT

if [[ ! -f "${tarball}" ]]; then
    echo "Hubble tarball not found: ${tarball}" >&2
    exit 1
fi

mkdir -p "${work_dir}"
tar -xzf "${tarball}" -C "${work_dir}"
hubble_home=$(find "${work_dir}" -maxdepth 1 -type d -name 'apache-hugegraph-hubble-*' | head -n 1)
if [[ -z "${hubble_home}" ]]; then
    echo "Unable to find unpacked Hubble home in ${work_dir}" >&2
    exit 1
fi

echo "Starting Hubble candidate: ${hubble_home}"
"${hubble_home}/bin/start-hubble.sh"

for _ in $(seq 1 60); do
    if curl -fsS "${hubble_url}/actuator/health" >/dev/null; then
        break
    fi
    sleep 1
done

curl -fsS "${hubble_url}/actuator/health" | grep -q '"UP"'
curl -fsS "${hubble_url}/" | grep -q '<div id="root"></div>'

for route in \
    /graph-management \
    /graph-management/1/metadata-configs \
    /graph-management/1/data-import/import-manager \
    /graph-management/1/data-analyze \
    /graph-management/1/async-tasks
do
    curl -fsS "${hubble_url}${route}" | grep -q '<div id="root"></div>'
done

curl -fsS "${server_url}/versions" >/dev/null

connection_body=$(cat <<JSON
{"name":"issue_694_local","graph":"${graph_name}","host":"${server_host}","port":${server_port},"username":"","password":"","protocol":"${server_protocol}"}
JSON
)

connection_response=$(curl -fsS \
    -H 'Content-Type: application/json' \
    -d "${connection_body}" \
    "${hubble_url}/api/v1.2/graph-connections")
if ! echo "${connection_response}" | grep -q '"status":200'; then
    echo "Failed to create Hubble graph connection" >&2
    echo "${connection_response}" >&2
    exit 1
fi

conn_id=$(printf '%s' "${connection_response}" |
          sed -n 's/.*"id":\([0-9][0-9]*\).*/\1/p')

if [[ -z "${conn_id}" ]]; then
    echo "Unable to resolve Hubble graph connection id" >&2
    echo "${connection_response}" >&2
    exit 1
fi

schema_response=$(curl -fsS \
    "${hubble_url}/api/v1.2/graph-connections/${conn_id}/schema/graphview")
if ! echo "${schema_response}" | grep -q '"status":200'; then
    echo "Hubble schema graphview API failed" >&2
    echo "${schema_response}" >&2
    exit 1
fi

job_response=$(curl -fsS \
    "${hubble_url}/api/v1.2/graph-connections/${conn_id}/job-manager?page_no=1&page_size=10")
if ! echo "${job_response}" | grep -q '"status":200'; then
    echo "Hubble job-manager API failed" >&2
    echo "${job_response}" >&2
    exit 1
fi

task_response=$(curl -fsS \
    "${hubble_url}/api/v1.2/graph-connections/${conn_id}/async-tasks?page_no=1&page_size=10")
if ! echo "${task_response}" | grep -q '"status":200'; then
    echo "Hubble async-tasks API failed" >&2
    echo "${task_response}" >&2
    exit 1
fi

echo "Hubble issue #694 smoke passed with connection id ${conn_id}"
