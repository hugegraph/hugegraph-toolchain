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
export LANG=zh_CN.UTF-8
set -ev

if [[ $# -lt 1 || $# -gt 2 ]]; then
    echo "Usage: $0 <commit-id> [fetch-ref]" >&2
    exit 1
fi

COMMIT_ID=$1
COMMIT_REF=${2:-}
HUGEGRAPH_GIT_URL="https://github.com/apache/hugegraph.git"
GIT_DIR=hugegraph
CACHE_DIR="${HOME}/hugegraph-cache-${COMMIT_ID}"

mkdir -p "${CACHE_DIR}"
CACHED_TARBALL=$(find "${CACHE_DIR}" -maxdepth 1 \
                       -name "apache-hugegraph-*.tar.gz" -print -quit)

if [[ -f "${CACHED_TARBALL}" ]]; then
    echo "Found HugeGraph server tarball cached for commit ${COMMIT_ID}."
    cp "${CACHED_TARBALL}" ./
    exit 0
fi

# download code and compile
git clone --depth 150 $HUGEGRAPH_GIT_URL $GIT_DIR
cd "${GIT_DIR}"
if [[ -n "${COMMIT_REF}" ]]; then
    git fetch --depth 1 origin "${COMMIT_REF}"
fi
git checkout "${COMMIT_ID}"
ACTUAL_COMMIT_ID=$(git rev-parse HEAD)
if [[ "${ACTUAL_COMMIT_ID}" != "${COMMIT_ID}" ]]; then
    echo "HugeGraph checkout mismatch: expected ${COMMIT_ID}, got ${ACTUAL_COMMIT_ID}" >&2
    exit 1
fi
mvn package -DskipTests -Dmaven.javadoc.skip=true -ntp

cd hugegraph-server
TAR=$(echo apache-hugegraph-*.tar.gz)
cp apache-hugegraph-*.tar.gz ../../
cd ../../
rm -rf "${GIT_DIR}"
cp apache-hugegraph-*.tar.gz "${CACHE_DIR}"/
