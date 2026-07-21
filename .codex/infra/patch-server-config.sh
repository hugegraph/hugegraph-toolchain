#!/usr/bin/env bash

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
unset CDPATH

CONF_DIR=${HG_SERVER_CONF_DIR:-./conf}
REST_CONF="${CONF_DIR}/rest-server.properties"
GREMLIN_CONF="${CONF_DIR}/gremlin-server.yaml"

set_property() {
    local file=$1 key=$2 value=$3 escaped
    escaped=${key//./\.}
    if grep -qE "^[[:space:]]*${escaped}[[:space:]]*=" "${file}"; then
        sed -E -i.bak \
            "s|^[[:space:]]*${escaped}[[:space:]]*=.*|${key}=${value}|" "${file}"
        rm -f "${file}.bak"
    else
        echo "WARN: property '${key}' is not declared in ${file}; adding low-memory override" >&2
        printf '%s=%s\n' "${key}" "${value}" >> "${file}"
    fi
}

set_yaml_scalar() {
    local file=$1 key=$2 value=$3
    if ! grep -qE "^[[:space:]]*${key}:" "${file}"; then
        echo "WARN: optional YAML key '${key}' is unavailable in ${file}; keeping image default" >&2
        return
    fi
    sed -E -i.bak \
        "s|^([[:space:]]*)${key}:.*|\\1${key}: ${value}|" "${file}"
    rm -f "${file}.bak"
}

[[ -f "${REST_CONF}" ]] || { echo "ERROR: missing ${REST_CONF}" >&2; exit 1; }
[[ -f "${GREMLIN_CONF}" ]] || { echo "ERROR: missing ${GREMLIN_CONF}" >&2; exit 1; }

set_property "${REST_CONF}" batch.max_write_threads \
    "${HG_SERVER_BATCH_WRITE_THREADS:-2}"
set_property "${REST_CONF}" restserver.min_free_memory \
    "${HG_SERVER_MIN_FREE_MEMORY:-16}"
set_yaml_scalar "${GREMLIN_CONF}" threadPoolWorker \
    "${HG_SERVER_GREMLIN_WORKERS:-2}"
set_yaml_scalar "${GREMLIN_CONF}" gremlinPool \
    "${HG_SERVER_GREMLIN_POOL:-2}"

echo "[infra] applied low-memory Server configuration"

if [[ "${1:-}" == "--patch-only" ]]; then
    exit 0
fi

exec ./docker-entrypoint.sh
