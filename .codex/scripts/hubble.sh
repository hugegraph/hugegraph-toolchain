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

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "${SCRIPT_DIR}/../.." && pwd)
WORKTREE=${CODEX_WORKTREE_PATH:-${REPO_ROOT}}
SOURCE_TREE=${CODEX_SOURCE_TREE_PATH:-${REPO_ROOT}}
NODE_HOME=${HUBBLE_NODE_HOME:-"${HOME}/.nvm/versions/node/v18.20.8"}
CODEX_HOME=${CODEX_HOME:-"${HOME}/.codex"}

hash_text() {
    printf '%s' "$1" | LC_ALL=C shasum -a 256 | awk '{print substr($1, 1, 12)}'
}

STATE_ROOT="${CODEX_HOME}/run/toolchain-$(hash_text "${SOURCE_TREE}")"
WORKTREE_ID=$(hash_text "${WORKTREE}")
RUNTIME_DIR="${STATE_ROOT}/worktrees/${WORKTREE_ID}"
BE_RUNTIME_DIR="${STATE_ROOT}/hubble-be"

fe_port() {
    local path=${1:-${WORKTREE}}
    local checksum
    checksum=$(printf '%s' "${path}" | cksum | awk '{print $1}')
    echo $((3001 + checksum % 99))
}

read_field() {
    local file=$1 key=$2
    awk -F= -v key="${key}" '$1 == key {sub(/^[^=]*=/, ""); print; exit}' "${file}"
}

process_alive() {
    local pid=$1
    [[ "${pid}" =~ ^[0-9]+$ ]] && kill -0 "${pid}" 2>/dev/null
}

process_start_time() {
    ps -o lstart= -p "$1" 2>/dev/null | sed -E 's/^[[:space:]]+//;s/[[:space:]]+$//'
}

process_is_descendant() {
    local child=$1 ancestor=$2 parent
    while [[ "${child}" =~ ^[0-9]+$ ]] && ((child > 1)); do
        [[ "${child}" == "${ancestor}" ]] && return 0
        parent=$(ps -o ppid= -p "${child}" 2>/dev/null | tr -d ' ')
        [[ -n "${parent}" && "${parent}" != "${child}" ]] || return 1
        child=${parent}
    done
    return 1
}

owner_identity_matches() {
    local file=$1 pid expected_start command
    pid=$(read_field "${file}" pid)
    expected_start=$(read_field "${file}" start_time)
    process_alive "${pid}" || return 1
    [[ -n "${expected_start}" ]] || return 1
    [[ "$(process_start_time "${pid}")" == "${expected_start}" ]] || return 1
    command=$(ps -o command= -p "${pid}" 2>/dev/null || true)
    [[ "${command}" == *".codex/scripts/hubble.sh"* ]]
}

owner_service_matches() {
    local file=$1 pid port listener
    owner_identity_matches "${file}" || return 1
    pid=$(read_field "${file}" pid)
    port=$(read_field "${file}" port)
    listener=$(lsof -tiTCP:"${port}" -sTCP:LISTEN 2>/dev/null | head -1 || true)
    [[ -z "${listener}" ]] || process_is_descendant "${listener}" "${pid}"
}

acquire_state_lock() {
    local attempt probe
    mkdir -p "${STATE_ROOT}"
    sleep 5 &
    probe=$!
    STATE_LOCK_PID=$(ps -o ppid= -p "${probe}" | tr -d ' ')
    kill "${probe}" 2>/dev/null || true
    wait "${probe}" 2>/dev/null || true
    [[ "${STATE_LOCK_PID}" =~ ^[0-9]+$ ]] || {
        echo "ERROR: unable to identify owner-lock process" >&2
        return 1
    }
    if command -v flock >/dev/null 2>&1; then
        STATE_LOCK_KIND=flock
        STATE_LOCK_PATH="${STATE_ROOT}/.owner.lock"
        exec 9>"${STATE_LOCK_PATH}"
        flock -w 12 9 || {
            echo "ERROR: timed out waiting for Hubble owner lock" >&2
            return 1
        }
        return
    fi
    if command -v shlock >/dev/null 2>&1; then
        STATE_LOCK_KIND=shlock
        STATE_LOCK_PATH="${STATE_ROOT}/.owner.lock"
        for ((attempt = 0; attempt < 240; attempt++)); do
            shlock -f "${STATE_LOCK_PATH}" -p "${STATE_LOCK_PID}" && return
            sleep 0.05
        done
        echo "ERROR: timed out waiting for Hubble owner lock" >&2
        return 1
    fi

    STATE_LOCK_KIND="mkdir"
    STATE_LOCK_PATH="${STATE_ROOT}/.owner-lock"
    STATE_LOCK_TOKEN="${STATE_LOCK_PID}-${RANDOM}"
    for ((attempt = 0; attempt < 240; attempt++)); do
        if mkdir "${STATE_LOCK_PATH}" 2>/dev/null; then
            printf '%s\n' "${STATE_LOCK_TOKEN}" > "${STATE_LOCK_PATH}/owner"
            return
        fi
        sleep 0.05
    done
    echo "ERROR: owner lock is stuck; remove ${STATE_LOCK_PATH} if no action runs" >&2
    return 1
}

release_state_lock() {
    local holder
    case "${STATE_LOCK_KIND:-}" in
        flock)
            flock -u 9 2>/dev/null || true
            exec 9>&-
            ;;
        shlock)
            holder=$(cat "${STATE_LOCK_PATH}" 2>/dev/null || true)
            [[ "${holder}" == "${STATE_LOCK_PID}" ]] && rm -f "${STATE_LOCK_PATH}"
            ;;
        mkdir)
            holder=$(cat "${STATE_LOCK_PATH}/owner" 2>/dev/null || true)
            [[ "${holder}" == "${STATE_LOCK_TOKEN}" ]] && rm -rf "${STATE_LOCK_PATH}"
            ;;
    esac
}

with_state_lock() (
    acquire_state_lock
    trap release_state_lock EXIT
    "$@"
)

new_token() {
    printf '%s-%s-%s\n' "$$" "$(date +%s)" "${RANDOM}"
}

write_owner() {
    local file=$1 kind=$2 pid=$3 port=$4 token=$5 temp
    mkdir -p "$(dirname "${file}")"
    temp="${file}.tmp.${pid}.${RANDOM}"
    {
        echo "kind=${kind}"
        echo "pid=${pid}"
        echo "start_time=$(process_start_time "${pid}")"
        echo "token=${token}"
        echo "port=${port}"
        echo "worktree=${WORKTREE}"
        echo "branch=$(git -C "${WORKTREE}" branch --show-current 2>/dev/null || true)"
        echo "head=$(git -C "${WORKTREE}" rev-parse --short HEAD 2>/dev/null || true)"
    } > "${temp}"
    mv "${temp}" "${file}"
}

remove_owner_if_token() {
    local file=$1 expected_token=$2
    [[ -f "${file}" ]] || return
    if [[ "$(read_field "${file}" token)" == "${expected_token}" ]] && \
       [[ "$(read_field "${file}" pid)" == "$$" ]]; then
        rm -f "${file}"
    fi
}

cleanup_claim() {
    [[ -n "${SERVICE_OWNER:-}" && -n "${SERVICE_TOKEN:-}" ]] || return
    [[ -f "${SERVICE_OWNER}" ]] || return
    [[ "$(read_field "${SERVICE_OWNER}" token)" == "${SERVICE_TOKEN}" ]] || return
    with_state_lock remove_owner_if_token "${SERVICE_OWNER}" "${SERVICE_TOKEN}" || true
}

kill_tree() {
    local pid=$1 child
    while read -r child; do
        [[ -n "${child}" ]] && kill_tree "${child}"
    done < <(pgrep -P "${pid}" 2>/dev/null || true)
    kill -TERM "${pid}" 2>/dev/null || true
}

live_fe_count() {
    local file pid count=0
    shopt -s nullglob
    for file in "${STATE_ROOT}"/fe-*.owner; do
        pid=$(read_field "${file}" pid)
        if owner_service_matches "${file}"; then
            count=$((count + 1))
        else
            echo "WARN: removing stale FE owner for pid ${pid}" >&2
            rm -f "${file}"
        fi
    done
    echo "${count}"
}

claim_fe() {
    local token=$1 preferred offset port owner pid owner_worktree listener
    preferred=$(fe_port)
    for ((offset = 0; offset < 99; offset++)); do
        port=$((3001 + (preferred - 3001 + offset) % 99))
        owner="${STATE_ROOT}/fe-${port}.owner"
        if [[ -f "${owner}" ]]; then
            pid=$(read_field "${owner}" pid)
            owner_worktree=$(read_field "${owner}" worktree)
            if owner_service_matches "${owner}"; then
                if [[ "${owner_worktree}" == "${WORKTREE}" ]]; then
                    echo "ERROR: FE already runs from this worktree on port ${port}" >&2
                    return 1
                fi
                continue
            fi
            echo "WARN: removing stale FE owner for pid ${pid}" >&2
            rm -f "${owner}"
        fi
        listener=$(lsof -tiTCP:"${port}" -sTCP:LISTEN 2>/dev/null | head -1 || true)
        [[ -z "${listener}" ]] || continue
        write_owner "${owner}" fe "$$" "${port}" "${token}"
        echo "${port}"
        return
    done
    echo "ERROR: no free Hubble FE port in 3001-3099" >&2
    return 1
}

start_fe() {
    local port owner count token
    mkdir -p "${RUNTIME_DIR}"
    token=$(new_token)
    port=$(with_state_lock claim_fe "${token}")
    owner="${STATE_ROOT}/fe-${port}.owner"
    SERVICE_OWNER=${owner}
    SERVICE_TOKEN=${token}
    trap cleanup_claim EXIT

    count=$(with_state_lock live_fe_count)
    if ((count > 3)); then
        echo "WARN: ${count} Hubble frontend servers are already running" >&2
    fi
    [[ -x "${NODE_HOME}/bin/node" ]] || {
        echo "ERROR: missing Node 18.20.8 at ${NODE_HOME}" >&2
        exit 1
    }
    [[ -d "${WORKTREE}/hugegraph-hubble/hubble-fe/node_modules" ]] || {
        echo "ERROR: frontend dependencies are missing; run the environment setup" >&2
        exit 1
    }

    export PATH="${NODE_HOME}/bin:${PATH}"
    cd "${WORKTREE}/hugegraph-hubble/hubble-fe"
    echo "[hubble] FE ${WORKTREE} -> http://127.0.0.1:${port}"
    PORT="${port}" BROWSER=none yarn start
}

java11_home() {
    if [[ -n "${JAVA11_HOME:-}" ]] && [[ -x "${JAVA11_HOME}/bin/java" ]]; then
        echo "${JAVA11_HOME}"
    elif [[ -x /usr/libexec/java_home ]]; then
        /usr/libexec/java_home -v 11 2>/dev/null
    elif command -v java >/dev/null 2>&1 && \
         java -version 2>&1 | head -1 | grep -Eq 'version "11\.'; then
        cd "$(dirname "$(command -v java)")/.." && pwd
    else
        echo "ERROR: Java 11 is required; set JAVA11_HOME" >&2
        return 1
    fi
}

maven_runner() {
    local java_home=$1 candidate
    for candidate in "${HUBBLE_MVND_BIN:-}" \
                     /opt/homebrew/opt/mvnd@1/bin/mvnd \
                     /usr/local/opt/mvnd@1/bin/mvnd \
                     "$(command -v mvnd 2>/dev/null || true)"; do
        if [[ -n "${candidate}" ]] && [[ -x "${candidate}" ]] && \
           JAVA_HOME="${java_home}" "${candidate}" --version >/dev/null 2>&1; then
            echo "${candidate}"
            return
        fi
    done
    echo mvn
}

loader_home() {
    local candidate
    if [[ -n "${HUBBLE_LOADER_HOME:-}" ]]; then
        echo "${HUBBLE_LOADER_HOME}"
        return
    fi
    candidate=$(find "${SOURCE_TREE}/hugegraph-loader" -maxdepth 1 -type d \
        -name 'apache-hugegraph-loader-*' | sort | tail -1)
    if [[ -z "${candidate}" ]]; then
        echo "ERROR: no packaged Loader found; set HUBBLE_LOADER_HOME" >&2
        exit 1
    fi
    echo "${candidate}"
}

claim_be() {
    local switch=$1 token=$2 owner="${STATE_ROOT}/be.owner"
    local pid owner_worktree listener wait_for
    if [[ -f "${owner}" ]]; then
        pid=$(read_field "${owner}" pid)
        owner_worktree=$(read_field "${owner}" worktree)
        if owner_service_matches "${owner}"; then
            if [[ "${owner_worktree}" == "${WORKTREE}" ]]; then
                echo "[hubble] BE already runs from this worktree (pid ${pid})" >&2
                return 10
            fi
            if [[ "${switch}" != true ]]; then
                echo "ERROR: BE is owned by ${owner_worktree} (pid ${pid})" >&2
                echo "Use the 'Hubble BE Switch' action to replace it." >&2
                return 1
            fi
            rm -f "${owner}"
            kill_tree "${pid}"
            wait_for=0
            while process_alive "${pid}" && ((wait_for < 50)); do
                sleep 0.1
                wait_for=$((wait_for + 1))
            done
            if process_alive "${pid}"; then
                echo "ERROR: previous BE pid ${pid} did not stop" >&2
                return 1
            fi
        else
            echo "WARN: refusing to signal stale or mismatched BE pid ${pid}" >&2
            rm -f "${owner}"
        fi
    fi

    listener=$(lsof -tiTCP:8088 -sTCP:LISTEN 2>/dev/null | head -1 || true)
    if [[ -n "${listener}" ]]; then
        echo "ERROR: unowned process ${listener} is listening on port 8088" >&2
        return 1
    fi
    write_owner "${owner}" be "$$" 8088 "${token}"
}

start_be() {
    local switch=${1:-false}
    local owner="${STATE_ROOT}/be.owner" token claim_status
    local java_home runner classpath loader config_arg=()
    mkdir -p "${RUNTIME_DIR}"
    mkdir -p "${BE_RUNTIME_DIR}/logs" "${BE_RUNTIME_DIR}/upload-files"
    token=$(new_token)
    if with_state_lock claim_be "${switch}" "${token}"; then
        :
    else
        claim_status=$?
        [[ "${claim_status}" -eq 10 ]] && return
        return "${claim_status}"
    fi
    SERVICE_OWNER=${owner}
    SERVICE_TOKEN=${token}
    trap cleanup_claim EXIT

    java_home=$(java11_home)
    runner=$(maven_runner "${java_home}")
    classpath="${RUNTIME_DIR}/hubble-be.classpath"
    echo "[hubble] compiling BE with Java 11 + ${runner}"
    (
        cd "${WORKTREE}/hugegraph-hubble"
        JAVA_HOME="${java_home}" "${runner}" -pl hubble-be -DskipTests compile \
            dependency:build-classpath -Dmdep.outputFile="${classpath}" -ntp
    )
    [[ -s "${classpath}" ]] || {
        echo "ERROR: Maven did not create ${classpath}" >&2
        exit 1
    }

    loader=$(loader_home)
    if [[ -n "${HUBBLE_CONFIG_FILE:-}" ]]; then
        config_arg=("${HUBBLE_CONFIG_FILE}")
    fi

    cd "${BE_RUNTIME_DIR}"
    echo "[hubble] BE owner: ${WORKTREE}"
    "${java_home}/bin/java" \
        -Dfile.encoding=UTF-8 \
        -Dhubble.home.path="${BE_RUNTIME_DIR}" \
        -Dloader.home.path="${loader}" \
        -XX:ActiveProcessorCount=2 \
        -Xms64m -Xmx256m \
        -XX:MaxMetaspaceSize=128m \
        -XX:MaxDirectMemorySize=64m \
        -cp "${WORKTREE}/hugegraph-hubble/hubble-be/target/classes:$(<"${classpath}")" \
        org.apache.hugegraph.HugeGraphHubble "${config_arg[@]}"
}

stop_current() {
    local owner pid owner_worktree
    shopt -s nullglob
    for owner in "${STATE_ROOT}"/fe-*.owner "${STATE_ROOT}/be.owner"; do
        [[ -f "${owner}" ]] || continue
        pid=$(read_field "${owner}" pid)
        owner_worktree=$(read_field "${owner}" worktree)
        if [[ "${owner_worktree}" != "${WORKTREE}" ]]; then
            echo "[hubble] leave ${owner_worktree} owner running"
            continue
        fi
        if owner_service_matches "${owner}"; then
            echo "[hubble] stopping pid ${pid} from ${WORKTREE}"
            rm -f "${owner}"
            kill_tree "${pid}"
        else
            echo "WARN: owner identity mismatch; not signaling pid ${pid}: ${owner}" >&2
        fi
    done
}

show_status() {
    local file pid port path branch head rss
    mkdir -p "${STATE_ROOT}"
    printf '%-5s %-7s %-8s %-10s %s\n' TYPE PORT PID RSS_KB OWNER
    shopt -s nullglob
    for file in "${STATE_ROOT}"/fe-*.owner "${STATE_ROOT}/be.owner"; do
        [[ -f "${file}" ]] || continue
        pid=$(read_field "${file}" pid)
        if ! owner_service_matches "${file}"; then
            echo "WARN: stale or mismatched owner: ${file}" >&2
            rm -f "${file}"
            continue
        fi
        port=$(read_field "${file}" port)
        path=$(read_field "${file}" worktree)
        branch=$(read_field "${file}" branch)
        head=$(read_field "${file}" head)
        rss=$(ps -o rss= -p "${pid}" | tr -d ' ')
        printf '%-5s %-7s %-8s %-10s %s [%s@%s]\n' \
            "$(read_field "${file}" kind)" "${port}" "${pid}" "${rss:-?}" \
            "${path}" "${branch:-detached}" "${head}"
    done
}

case "${1:-help}" in
    fe-port) fe_port "${2:-${WORKTREE}}" ;;
    be-runtime) echo "${BE_RUNTIME_DIR}" ;;
    fe-start) start_fe ;;
    be-start) start_be false ;;
    be-switch) start_be true ;;
    status) with_state_lock show_status ;;
    stop | cleanup) with_state_lock stop_current ;;
    *)
        echo "Usage: $0 {fe-port|be-runtime|fe-start|be-start|be-switch|status|stop|cleanup}" >&2
        exit 2
        ;;
esac
