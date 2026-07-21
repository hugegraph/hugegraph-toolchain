#!/usr/bin/env bash

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
        printf '%s=%s\n' "${key}" "${value}" >> "${file}"
    fi
}

set_yaml_scalar() {
    local file=$1 key=$2 value=$3
    grep -qE "^[[:space:]]*${key}:" "${file}" || {
        echo "ERROR: missing YAML key '${key}' in ${file}" >&2
        exit 1
    }
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
