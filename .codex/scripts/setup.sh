#!/usr/bin/env bash

set -euo pipefail
unset CDPATH

WORKTREE=${CODEX_WORKTREE_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}
NODE_HOME=${HUBBLE_NODE_HOME:-"${HOME}/.nvm/versions/node/v18.20.8"}
FE_DIR="${WORKTREE}/hugegraph-hubble/hubble-fe"
DRY_RUN=false

if [[ "${1:-}" == "--dry-run" ]]; then
    DRY_RUN=true
fi

echo "[setup] worktree: ${WORKTREE}"
echo "[setup] require Node 18.20.8 from ${NODE_HOME}"

if ${DRY_RUN}; then
    echo "[setup] yarn install --frozen-lockfile --prefer-offline --non-interactive"
    exit 0
fi

if [[ ! -x "${NODE_HOME}/bin/node" ]]; then
    echo "ERROR: Node 18.20.8 is not installed at ${NODE_HOME}" >&2
    exit 1
fi

export PATH="${NODE_HOME}/bin:${PATH}"
[[ "$(node --version)" == "v18.20.8" ]] || {
    echo "ERROR: expected Node v18.20.8, got $(node --version)" >&2
    exit 1
}
command -v yarn >/dev/null || {
    echo "ERROR: yarn is not available with Node 18.20.8" >&2
    exit 1
}

if [[ ! -f "${FE_DIR}/yarn.lock" ]]; then
    echo "ERROR: missing ${FE_DIR}/yarn.lock" >&2
    exit 1
fi

lock_hash=$(LC_ALL=C shasum -a 256 "${FE_DIR}/yarn.lock" | awk '{print $1}')
stamp="${FE_DIR}/node_modules/.codex-yarn-lock.sha256"
if [[ -f "${stamp}" ]] && [[ "$(<"${stamp}")" == "${lock_hash}" ]]; then
    echo "[setup] frontend dependencies already match yarn.lock"
else
    echo "[setup] installing frontend dependencies from the shared Yarn cache"
    (
        cd "${FE_DIR}"
        yarn install --frozen-lockfile --prefer-offline --non-interactive
    )
    printf '%s\n' "${lock_hash}" > "${stamp}"
fi

if [[ -n "${JAVA11_HOME:-}" ]] && [[ -x "${JAVA11_HOME}/bin/java" ]]; then
    java_home=${JAVA11_HOME}
elif [[ -x /usr/libexec/java_home ]] && \
     java_home=$(/usr/libexec/java_home -v 11 2>/dev/null); then
    :
elif command -v java >/dev/null 2>&1 && \
     java -version 2>&1 | head -1 | grep -Eq 'version "11\.'; then
    java_home=$(cd "$(dirname "$(command -v java)")/.." && pwd)
else
    java_home=
fi

if [[ -n "${java_home}" ]]; then
    echo "[setup] Java 11: ${java_home}"
else
    echo "ERROR: Java 11 is required for Hubble BE development" >&2
    exit 1
fi

mvnd_bin=
for candidate in "${HUBBLE_MVND_BIN:-}" \
                 /opt/homebrew/opt/mvnd@1/bin/mvnd \
                 /usr/local/opt/mvnd@1/bin/mvnd \
                 "$(command -v mvnd 2>/dev/null || true)"; do
    if [[ -n "${candidate}" ]] && [[ -x "${candidate}" ]] && \
       JAVA_HOME="${java_home}" "${candidate}" --version >/dev/null 2>&1; then
        mvnd_bin=${candidate}
        break
    fi
done
if [[ -n "${mvnd_bin}" ]]; then
    echo "[setup] Java 11 + mvnd: ${mvnd_bin}"
else
    echo "[setup] Java 11 + mvnd unavailable; Hubble BE will use Java 11 + Maven"
fi

echo "[setup] complete"
