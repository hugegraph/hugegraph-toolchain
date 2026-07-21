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

CODEX_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
REPO_ROOT=$(cd "${CODEX_DIR}/.." && pwd)
TEST_TMP=$(mktemp -d "${TMPDIR:-/tmp}/toolchain-env-test.XXXXXX")
TEST_TMP=$(cd "${TEST_TMP}" && pwd)
trap 'rm -rf "${TEST_TMP}"' EXIT

fail() {
    echo "FAIL: $*" >&2
    exit 1
}

assert_contains() {
    local actual=$1
    local expected=$2
    [[ "${actual}" == *"${expected}"* ]] ||
        fail "expected '${actual}' to contain '${expected}'"
}

test_environment_toml() {
    python3 - "${CODEX_DIR}/environments/environment.toml" <<'PY'
import pathlib
import sys
import tomllib

path = pathlib.Path(sys.argv[1])
with path.open("rb") as stream:
    config = tomllib.load(stream)

assert config["version"] == 1
assert config["name"] == "toolchain-hubble"
assert config["setup"]["script"]
assert config["cleanup"]["script"]
names = {action["name"] for action in config["actions"]}
required = {
    "Hubble FE Dev",
    "Hubble BE Dev",
    "Hubble Status",
    "Hubble Stop",
    "HStore Low Memory",
    "Infrastructure Status",
    "Infrastructure Stop",
    "Infrastructure Reset",
}
assert required <= names, required - names
PY
}

test_setup_dry_run() {
    local output
    output=$(CODEX_WORKTREE_PATH="${REPO_ROOT}" \
        "${CODEX_DIR}/scripts/setup.sh" --dry-run)
    assert_contains "${output}" "Node 18.20.8"
    assert_contains "${output}" "yarn install --frozen-lockfile"
}

test_stable_frontend_ports() {
    local first second
    first=$("${CODEX_DIR}/scripts/hubble.sh" fe-port "/tmp/worktree-a")
    second=$("${CODEX_DIR}/scripts/hubble.sh" fe-port "/tmp/worktree-a")

    [[ "${first}" == "${second}" ]] || fail "port assignment is not stable"
    ((first >= 3001 && first <= 3099)) || fail "port ${first} is outside 3001-3099"
}

test_concurrent_frontend_claim() {
    local worktree="${TEST_TMP}/fe-worktree" fake_node="${TEST_TMP}/fake-node"
    local first_log="${TEST_TMP}/first-fe.log" first_pid
    mkdir -p "${worktree}/hugegraph-hubble/hubble-fe/node_modules" "${fake_node}/bin"
    printf '#!/bin/sh\nexit 0\n' > "${fake_node}/bin/node"
    printf '#!/bin/sh\nsleep 1\n' > "${fake_node}/bin/yarn"
    chmod +x "${fake_node}/bin/node" "${fake_node}/bin/yarn"

    CODEX_HOME="${TEST_TMP}/codex-home" CODEX_SOURCE_TREE_PATH="${TEST_TMP}/source" \
        CODEX_WORKTREE_PATH="${worktree}" HUBBLE_NODE_HOME="${fake_node}" \
        "${CODEX_DIR}/scripts/hubble.sh" fe-start >"${first_log}" 2>&1 &
    first_pid=$!
    sleep 0.2
    if CODEX_HOME="${TEST_TMP}/codex-home" CODEX_SOURCE_TREE_PATH="${TEST_TMP}/source" \
       CODEX_WORKTREE_PATH="${worktree}" HUBBLE_NODE_HOME="${fake_node}" \
       "${CODEX_DIR}/scripts/hubble.sh" fe-start >/dev/null 2>&1; then
        fail "duplicate frontend start unexpectedly succeeded"
    fi
    wait "${first_pid}"
    [[ -z "$(find "${TEST_TMP}/codex-home" -name 'fe-*.owner' -print -quit)" ]] ||
        fail "frontend owner was not cleaned up"
}

test_shared_backend_runtime() {
    local runtime
    runtime=$(CODEX_HOME="${TEST_TMP}/codex-home" \
        CODEX_SOURCE_TREE_PATH="${TEST_TMP}/source" \
        CODEX_WORKTREE_PATH="${TEST_TMP}/worktree-a" \
        "${CODEX_DIR}/scripts/hubble.sh" be-runtime)
    assert_contains "${runtime}" "/hubble-be"
    [[ "${runtime}" != *"worktree-a"* ]] ||
        fail "BE runtime is still scoped to a source worktree"
}

test_compose_command_is_offline_and_layered() {
    local server_repo="${TEST_TMP}/server"
    local output
    mkdir -p "${server_repo}/docker"
    : > "${server_repo}/docker/docker-compose.yml"

    output=$(HUGEGRAPH_SERVER_REPO="${server_repo}" \
        "${CODEX_DIR}/scripts/infra.sh" compose-command low)
    assert_contains "${output}" "docker-compose.yml"
    assert_contains "${output}" "compose.low-memory.yml"
    assert_contains "${output}" "--pull never"
}

test_server_config_patch() {
    local conf="${TEST_TMP}/server-conf"
    local output
    mkdir -p "${conf}"
    cat > "${conf}/rest-server.properties" <<'EOF'
batch.max_write_threads=16
EOF
    cat > "${conf}/gremlin-server.yaml" <<'EOF'
other: 1
EOF

    output=$(HG_SERVER_CONF_DIR="${conf}" \
        "${CODEX_DIR}/infra/patch-server-config.sh" --patch-only 2>&1)

    grep -qx 'batch.max_write_threads=2' "${conf}/rest-server.properties"
    grep -qx 'restserver.min_free_memory=16' "${conf}/rest-server.properties"
    grep -qx 'other: 1' "${conf}/gremlin-server.yaml"
    assert_contains "${output}" "WARN: property 'restserver.min_free_memory'"
    assert_contains "${output}" "WARN: optional YAML key 'threadPoolWorker'"
    assert_contains "${output}" "WARN: optional YAML key 'gremlinPool'"
}

test_server_config_patch_requires_config_files() {
    local missing_rest="${TEST_TMP}/server-conf-missing-rest"
    local missing_yaml="${TEST_TMP}/server-conf-missing-yaml" output
    mkdir -p "${missing_rest}" "${missing_yaml}"
    printf 'batch.max_write_threads=16\n' > "${missing_yaml}/rest-server.properties"

    if output=$(HG_SERVER_CONF_DIR="${missing_rest}" \
        "${CODEX_DIR}/infra/patch-server-config.sh" --patch-only 2>&1); then
        fail "patch unexpectedly accepted missing configuration files"
    fi
    assert_contains "${output}" "ERROR: missing ${missing_rest}/rest-server.properties"

    if output=$(HG_SERVER_CONF_DIR="${missing_yaml}" \
        "${CODEX_DIR}/infra/patch-server-config.sh" --patch-only 2>&1); then
        fail "patch unexpectedly accepted a missing gremlin-server.yaml"
    fi
    assert_contains "${output}" "ERROR: missing ${missing_yaml}/gremlin-server.yaml"
    grep -qx 'batch.max_write_threads=16' "${missing_yaml}/rest-server.properties"
}

test_environment_toml
test_setup_dry_run
test_stable_frontend_ports
test_concurrent_frontend_claim
test_shared_backend_runtime
test_compose_command_is_offline_and_layered
test_server_config_patch
test_server_config_patch_requires_config_files

echo "PASS: toolchain local environment tests"
