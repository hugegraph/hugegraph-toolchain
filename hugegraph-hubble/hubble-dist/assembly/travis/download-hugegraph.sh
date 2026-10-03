#!/bin/bash
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with this
# work for additional information regarding copyright ownership. The ASF
# licenses this file to You under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations
# under the License.
#
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
    echo "Usage: $0 <commit-id> [fetch-ref]" >&2
    exit 1
fi

COMMIT_ID=$1
export SERVER_FETCH_REF=${2:-${SERVER_FETCH_REF:-}}
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
TOOLCHAIN_ROOT=$(cd "$SCRIPT_DIR/../../../.." && pwd)

# CI builds once before compiling Hubble, so the server and Maven dependencies
# come from the same verified commit. Direct callers use the same installer.
if [[ -n "${HUGEGRAPH_SERVER_ARCHIVE:-}" ]]; then
    ARCHIVE=$HUGEGRAPH_SERVER_ARCHIVE
else
    BUILD_DIR=$(mktemp -d "${TMPDIR:-/tmp}/hubble-server-build.XXXXXX")
    trap 'rm -rf -- "$BUILD_DIR"' EXIT
    (cd "$BUILD_DIR" && bash \
        "$TOOLCHAIN_ROOT/hugegraph-client/assembly/travis/install-hugegraph-from-source.sh" \
        "$COMMIT_ID" --build-only)
    ARCHIVES=("$BUILD_DIR"/hugegraph/hugegraph-server/apache-hugegraph-*.tar.gz)
    [[ ${#ARCHIVES[@]} -eq 1 && -f "${ARCHIVES[0]}" ]]
    ARCHIVE=${ARCHIVES[0]}
fi

[[ -f "$ARCHIVE" ]]
cp "$ARCHIVE" ./
