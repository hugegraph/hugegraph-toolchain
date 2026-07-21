#!/usr/bin/env bash

set -euo pipefail
unset CDPATH

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
CODEX_DIR=$(cd "${SCRIPT_DIR}/.." && pwd)
REPO_ROOT=$(cd "${CODEX_DIR}/.." && pwd)
SOURCE_TREE=${CODEX_SOURCE_TREE_PATH:-${REPO_ROOT}}

resolve_server_repo() {
    if [[ -n "${HUGEGRAPH_SERVER_REPO:-}" ]]; then
        echo "${HUGEGRAPH_SERVER_REPO}"
        return
    fi
    local sibling
    sibling=$(cd "${SOURCE_TREE}/.." 2>/dev/null && pwd)/server
    if [[ -d "${sibling}/docker" ]]; then
        echo "${sibling}"
        return
    fi
    echo "ERROR: set HUGEGRAPH_SERVER_REPO or place server beside toolchain" >&2
    exit 1
}

compose_files() {
    local profile=${1:-low}
    local server_repo=$2
    COMPOSE_ARGS=(
        -f "${server_repo}/docker/docker-compose.yml"
        -f "${CODEX_DIR}/infra/compose.low-memory.yml"
    )
    if [[ "${profile}" == "balanced" ]]; then
        COMPOSE_ARGS+=(-f "${CODEX_DIR}/infra/compose.balanced.yml")
    elif [[ "${profile}" != "low" ]]; then
        echo "ERROR: unknown profile ${profile}" >&2
        exit 2
    fi
}

print_compose_command() {
    local profile=${1:-low} server_repo arg
    server_repo=$(resolve_server_repo)
    [[ -f "${server_repo}/docker/docker-compose.yml" ]] || {
        echo "ERROR: missing server Compose file" >&2
        exit 1
    }
    compose_files "${profile}" "${server_repo}"
    printf 'TOOLCHAIN_CODEX_DIR=%q HUGEGRAPH_VERSION=latest docker compose' "${CODEX_DIR}"
    for arg in "${COMPOSE_ARGS[@]}"; do printf ' %q' "${arg}"; done
    printf ' up -d --pull never --wait\n'
}

warn_dirty_server() {
    local server_repo=$1 dirty
    dirty=$(git -C "${server_repo}" status --short 2>/dev/null || true)
    if [[ -n "${dirty}" ]]; then
        echo "WARN: server repo has local changes; latest images do not include them:" >&2
        echo "${dirty}" | head -20 >&2
    fi
}

assert_docker() {
    docker info >/dev/null 2>&1 || {
        echo "ERROR: Docker is not running" >&2
        exit 1
    }
}

assert_port_available_for_compose() {
    local port=$1 expected_container=$2 listener container
    local project='' service='' mapping=''
    listener=$(lsof -tiTCP:"${port}" -sTCP:LISTEN 2>/dev/null | head -1 || true)
    [[ -z "${listener}" ]] && return
    container=$(docker ps --filter "name=^/${expected_container}$" \
        --format '{{.ID}}' | head -1)
    if [[ -n "${container}" ]]; then
        project=$(docker inspect --format \
            '{{index .Config.Labels "com.docker.compose.project"}}' "${container}")
        service=$(docker inspect --format \
            '{{index .Config.Labels "com.docker.compose.service"}}' "${container}")
        mapping=$(docker port "${container}" "${port}/tcp" 2>/dev/null || true)
    fi
    if [[ -z "${container}" || "${project}" != hugegraph-single || \
          "${service}" != "${expected_container#hg-}" || \
          "${mapping}" != *":${port}"* ]]; then
        echo "ERROR: port ${port} is owned by non-Compose pid ${listener}" >&2
        exit 1
    fi
}

hstore_start() {
    local profile=${1:-low} server_repo
    server_repo=$(resolve_server_repo)
    assert_docker
    warn_dirty_server "${server_repo}"
    assert_port_available_for_compose 8080 hg-server
    assert_port_available_for_compose 8520 hg-store
    assert_port_available_for_compose 8620 hg-pd
    compose_files "${profile}" "${server_repo}"
    echo "[infra] starting HStore 1+1+1 (${profile}, local latest images)"
    TOOLCHAIN_CODEX_DIR="${CODEX_DIR}" HUGEGRAPH_VERSION=latest \
        docker compose "${COMPOSE_ARGS[@]}" up -d --pull never --wait
}

infra_status() {
    local server_repo
    server_repo=$(resolve_server_repo)
    assert_docker
    compose_files low "${server_repo}"
    TOOLCHAIN_CODEX_DIR="${CODEX_DIR}" HUGEGRAPH_VERSION=latest \
        docker compose "${COMPOSE_ARGS[@]}" ps
    echo
    docker stats --no-stream --format \
        'table {{.Name}}\t{{.MemUsage}}\t{{.CPUPerc}}' \
        hg-pd hg-store hg-server 2>/dev/null || true
    echo
    lsof -nP -iTCP:8080 -sTCP:LISTEN 2>/dev/null || true
}

infra_stop() {
    local server_repo
    server_repo=$(resolve_server_repo)
    assert_docker
    compose_files low "${server_repo}"
    echo "[infra] stopping Compose services; named volumes are preserved"
    TOOLCHAIN_CODEX_DIR="${CODEX_DIR}" HUGEGRAPH_VERSION=latest \
        docker compose "${COMPOSE_ARGS[@]}" down
}

infra_reset() {
    local server_repo confirmation=${CONFIRM_INFRA_RESET:-}
    server_repo=$(resolve_server_repo)
    assert_docker
    compose_files low "${server_repo}"
    if [[ "${confirmation}" != RESET ]]; then
        if [[ ! -t 0 ]]; then
            echo "ERROR: destructive reset requires CONFIRM_INFRA_RESET=RESET" >&2
            exit 1
        fi
        read -r -p "Type RESET to delete local HStore Compose volumes: " confirmation
    fi
    [[ "${confirmation}" == RESET ]] || {
        echo "[infra] reset cancelled"
        return
    }
    echo "[infra] stopping Compose services and deleting their named volumes"
    TOOLCHAIN_CODEX_DIR="${CODEX_DIR}" HUGEGRAPH_VERSION=latest \
        docker compose "${COMPOSE_ARGS[@]}" down --volumes --remove-orphans
}

infra_pull() {
    local server_repo
    server_repo=$(resolve_server_repo)
    assert_docker
    compose_files low "${server_repo}"
    echo "[infra] explicitly updating latest images"
    TOOLCHAIN_CODEX_DIR="${CODEX_DIR}" HUGEGRAPH_VERSION=latest \
        docker compose "${COMPOSE_ARGS[@]}" pull
}

case "${1:-help}" in
    resolve-server-repo) resolve_server_repo ;;
    compose-command) print_compose_command "${2:-low}" ;;
    hstore-start) hstore_start "${2:-low}" ;;
    status) infra_status ;;
    stop) infra_stop ;;
    reset) infra_reset ;;
    pull) infra_pull ;;
    *)
        echo "Usage: $0 {resolve-server-repo|compose-command|hstore-start|status|stop|reset|pull}" >&2
        exit 2
        ;;
esac
