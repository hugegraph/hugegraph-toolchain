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
NODE_HOME=${HUBBLE_NODE_HOME:-"${HOME}/.nvm/versions/node/v18.20.8"}
if [[ -n "${JAVA11_HOME:-}" ]] && [[ -x "${JAVA11_HOME}/bin/java" ]]; then
    JAVA_HOME=${JAVA11_HOME}
elif [[ -x /usr/libexec/java_home ]]; then
    JAVA_HOME=$(/usr/libexec/java_home -v 11)
elif command -v java >/dev/null 2>&1 && \
     java -version 2>&1 | head -1 | grep -Eq 'version "11\.'; then
    JAVA_HOME=$(cd "$(dirname "$(command -v java)")/.." && pwd)
else
    echo "ERROR: Java 11 is required; set JAVA11_HOME" >&2
    exit 1
fi
export JAVA_HOME PATH="${NODE_HOME}/bin:${PATH}"

MAVEN=
for candidate in "${HUBBLE_MVND_BIN:-}" \
                 /opt/homebrew/opt/mvnd@1/bin/mvnd \
                 /usr/local/opt/mvnd@1/bin/mvnd \
                 "$(command -v mvnd 2>/dev/null || true)"; do
    if [[ -n "${candidate}" ]] && [[ -x "${candidate}" ]] && \
       "${candidate}" --version >/dev/null 2>&1; then
        MAVEN=${candidate}
        break
    fi
done
MAVEN=${MAVEN:-mvn}

fast_verify() {
    (
        cd "${WORKTREE}/hugegraph-hubble/hubble-fe"
        yarn lint
        yarn i18n:check
    )
    (
        cd "${WORKTREE}"
        "${MAVEN}" -pl hugegraph-hubble/hubble-be -am \
            -DskipTests compile -Djacoco.skip=true -ntp
    )
}

full_verify() {
    fast_verify
    (
        cd "${WORKTREE}/hugegraph-hubble/hubble-fe"
        CI=true yarn test --watchAll=false --runInBand
        CI=true yarn build
    )
    (
        cd "${WORKTREE}"
        "${MAVEN}" test -P unit-test -pl hugegraph-hubble/hubble-be \
            -Djacoco.skip=true -ntp
    )
}

case "${1:-fast}" in
    fast) fast_verify ;;
    full) full_verify ;;
    *) echo "Usage: $0 {fast|full}" >&2; exit 2 ;;
esac
