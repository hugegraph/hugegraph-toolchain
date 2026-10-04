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

if [[ $# -ne 1 ]]; then
    echo "Must input an existing commit id of hugegraph server" && exit 1
fi

COMMIT_ID=$1
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
SHARED_DIR="$SCRIPT_DIR/../../../hugegraph-client/assembly/travis"
[[ "$COMMIT_ID" =~ ^[0-9a-fA-F]{40}$ ]] || { echo "Expected a full commit SHA" >&2; exit 1; }
CACHE_DIR=${SERVER_CACHE_DIR:-"${HOME}/hugegraph-cache-${COMMIT_ID}"}

mkdir -p "${CACHE_DIR}"

CACHED_TARBALL=$(find "${CACHE_DIR}" -maxdepth 1 -name "apache-hugegraph-*.tar.gz" -print -quit)

if [[ -f "${CACHED_TARBALL}" ]]; then
    echo "Found cached HugeGraph server tarball, skipping build."
    cp "${CACHED_TARBALL}" ./
else
    echo "Building HugeGraph server from source (commit ${COMMIT_ID})..."
    bash "$SHARED_DIR/checkout-server.sh" "$COMMIT_ID" hugegraph
    cd hugegraph
    mvn package -DskipTests -Dmaven.javadoc.skip=true -ntp
    cd hugegraph-server
    mv apache-hugegraph-*.tar.gz ../../
    cd ../../
    rm -rf hugegraph
    cp apache-hugegraph-*.tar.gz "${CACHE_DIR}"/
fi

ARCHIVES=(apache-hugegraph-*.tar.gz)
if [[ ${#ARCHIVES[@]} -ne 1 || ! -f "${ARCHIVES[0]}" ]]; then
    printf 'Expected exactly one server archive, found: %s\n' "${ARCHIVES[*]}" >&2
    exit 1
fi
exec bash "$SHARED_DIR/start-hugegraph-servers.sh" "${ARCHIVES[0]}"
