#!/usr/bin/env bash

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
    local first second other
    first=$("${CODEX_DIR}/scripts/hubble.sh" fe-port "/tmp/worktree-a")
    second=$("${CODEX_DIR}/scripts/hubble.sh" fe-port "/tmp/worktree-a")
    other=$("${CODEX_DIR}/scripts/hubble.sh" fe-port "/tmp/worktree-b")

    [[ "${first}" == "${second}" ]] || fail "port assignment is not stable"
    ((first >= 3001 && first <= 3099)) || fail "port ${first} is outside 3001-3099"
    [[ "${first}" != "${other}" ]] || fail "fixture paths unexpectedly collide"
}

test_concurrent_frontend_claim() {
    local worktree="${TEST_TMP}/fe-worktree"
    local fake_node="${TEST_TMP}/fake-node"
    local first_output="${TEST_TMP}/first-fe.log"
    local second_output="${TEST_TMP}/second-fe.log"
    local first_pid
    mkdir -p "${worktree}/hugegraph-hubble/hubble-fe/node_modules" \
        "${fake_node}/bin"
    printf '#!/bin/sh\nexit 0\n' > "${fake_node}/bin/node"
    printf '#!/bin/sh\nsleep 1\n' > "${fake_node}/bin/yarn"
    chmod +x "${fake_node}/bin/node" "${fake_node}/bin/yarn"

    CODEX_HOME="${TEST_TMP}/codex-home" \
        CODEX_SOURCE_TREE_PATH="${TEST_TMP}/source" \
        CODEX_WORKTREE_PATH="${worktree}" \
        HUBBLE_NODE_HOME="${fake_node}" \
        "${CODEX_DIR}/scripts/hubble.sh" fe-start >"${first_output}" 2>&1 &
    first_pid=$!
    sleep 0.2
    if CODEX_HOME="${TEST_TMP}/codex-home" \
       CODEX_SOURCE_TREE_PATH="${TEST_TMP}/source" \
       CODEX_WORKTREE_PATH="${worktree}" \
       HUBBLE_NODE_HOME="${fake_node}" \
       "${CODEX_DIR}/scripts/hubble.sh" fe-start >"${second_output}" 2>&1; then
        fail "concurrent FE start unexpectedly acquired a second owner"
    fi
    wait "${first_pid}"
    grep -q 'FE already runs from this worktree' "${second_output}"
    [[ -z "$(find "${TEST_TMP}/codex-home" -name 'fe-*.owner' -print -quit)" ]] ||
        fail "FE owner was not removed by its token-aware EXIT cleanup"
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

test_owner_lock_contention() {
    local lock_home="${TEST_TMP}/lock-home" index=0
    while ((index < 20)); do
        CODEX_HOME="${lock_home}" \
            "${CODEX_DIR}/scripts/hubble.sh" status >/dev/null &
        index=$((index + 1))
    done
    wait
    [[ -z "$(find "${lock_home}" -name .owner-lock -print -quit)" ]] ||
        fail "owner lock leaked after concurrent status calls"
}

test_owner_lock_excludes_long_critical_section() {
    local lock_home="${TEST_TMP}/long-lock-home" first_pid second_pid
    CODEX_HOME="${lock_home}" \
        "${CODEX_DIR}/scripts/hubble.sh" _lock-hold 1 &
    first_pid=$!
    sleep 0.1
    CODEX_HOME="${lock_home}" \
        "${CODEX_DIR}/scripts/hubble.sh" _lock-hold 0 &
    second_pid=$!
    sleep 0.2
    kill -0 "${second_pid}" 2>/dev/null ||
        fail "second action entered a long owner critical section"
    wait "${first_pid}"
    wait "${second_pid}"
}

test_server_repo_resolution() {
    local output
    output=$(HUGEGRAPH_SERVER_REPO="${TEST_TMP}/server" \
        "${CODEX_DIR}/scripts/infra.sh" resolve-server-repo)
    [[ "${output}" == "${TEST_TMP}/server" ]] || fail "explicit server repo was ignored"

    mkdir -p "${TEST_TMP}/graph/toolchain" "${TEST_TMP}/graph/server/docker"
    output=$(CODEX_SOURCE_TREE_PATH="${TEST_TMP}/graph/toolchain" \
        "${CODEX_DIR}/scripts/infra.sh" resolve-server-repo)
    [[ "${output}" == "${TEST_TMP}/graph/server" ]] || fail "sibling server repo was not found"
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
    mkdir -p "${conf}"
    cat > "${conf}/rest-server.properties" <<'EOF'
batch.max_write_threads=16
restserver.min_free_memory=512
EOF
    cat > "${conf}/gremlin-server.yaml" <<'EOF'
threadPoolWorker: 8
gremlinPool: 8
EOF

    HG_SERVER_CONF_DIR="${conf}" \
        "${CODEX_DIR}/infra/patch-server-config.sh" --patch-only

    grep -qx 'batch.max_write_threads=2' "${conf}/rest-server.properties"
    grep -qx 'restserver.min_free_memory=16' "${conf}/rest-server.properties"
    grep -qx 'threadPoolWorker: 2' "${conf}/gremlin-server.yaml"
    grep -qx 'gremlinPool: 2' "${conf}/gremlin-server.yaml"
}

test_environment_toml
test_setup_dry_run
test_stable_frontend_ports
test_concurrent_frontend_claim
test_shared_backend_runtime
test_owner_lock_contention
test_owner_lock_excludes_long_critical_section
test_server_repo_resolution
test_compose_command_is_offline_and_layered
test_server_config_patch

echo "PASS: toolchain local environment tests"
