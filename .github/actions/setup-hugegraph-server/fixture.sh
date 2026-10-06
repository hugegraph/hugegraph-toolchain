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

# Identity includes every build input. Never restore an archive from another source.
FIXTURE_REPOSITORY=${FIXTURE_REPOSITORY:-${SERVER_REPOSITORY:-apache/hugegraph}}
FIXTURE_COMMIT=${FIXTURE_COMMIT:-${1:-}}
FIXTURE_REF=${FIXTURE_REF:-$FIXTURE_COMMIT}
FIXTURE_JAVA=${FIXTURE_JAVA:-11}
FIXTURE_DIR=${FIXTURE_DIR:-${RUNNER_TEMP:-${TMPDIR:-/tmp}}/hugegraph-fixture}
FIXTURE_HELPER=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/manifest.py
FIXTURE_BUILD_INPUTS=${FIXTURE_BUILD_INPUTS:-$(python3 "$FIXTURE_HELPER" inputs)}
FIXTURE_PLATFORM=${FIXTURE_PLATFORM:-$(uname -sm)}
if [[ "$FIXTURE_JAVA" == 17 ]]; then
    FIXTURE_BUILD_COMMAND=candidate-sdk-install
else
    FIXTURE_BUILD_COMMAND='package -DskipTests -Dmaven.javadoc.skip=true -ntp'
fi
FIXTURE_CONFIG="$FIXTURE_BUILD_COMMAND;schema=1;inputs=$FIXTURE_BUILD_INPUTS;platform=$FIXTURE_PLATFORM"
export FIXTURE_REPOSITORY FIXTURE_COMMIT FIXTURE_JAVA FIXTURE_CONFIG FIXTURE_DIR
[[ "$FIXTURE_REPOSITORY" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]]
[[ "$FIXTURE_COMMIT" =~ ^[0-9a-f]{40}$ ]]
[[ "$FIXTURE_JAVA" =~ ^[0-9]+$ ]]

fixture_validate_java() {
    actual_java=$(java -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*".*/\1/p' | head -1)
    [[ "$actual_java" == "$FIXTURE_JAVA" ]] || { echo 'Fixture JDK mismatch' >&2; return 1; }
}

fixture_verify() { python3 "$FIXTURE_HELPER" verify; }
fixture_build() {
    local cache_key cache_dir source_dir archive candidate_repo=''
    cache_key=$(printf '%s\n' "$FIXTURE_REPOSITORY" "$FIXTURE_COMMIT" "$FIXTURE_JAVA" "$FIXTURE_CONFIG" | shasum -a 256 | cut -d ' ' -f 1)
    cache_dir="${HOME}/hugegraph-fixture-cache/$cache_key"
    mkdir -p "$FIXTURE_DIR"
    if [[ -f "$cache_dir/server.tar.gz" && ! -L "$cache_dir/server.tar.gz" &&
          -f "$cache_dir/manifest.json" && ! -L "$cache_dir/manifest.json" ]] &&
       cp "$cache_dir"/server.tar.gz "$cache_dir"/manifest.json "$FIXTURE_DIR/"; then
        if fixture_verify; then return; fi
    fi
    rm -f "$FIXTURE_DIR/server.tar.gz" "$FIXTURE_DIR/manifest.json"
    fixture_validate_java
    source_dir="$(mktemp -d "${TMPDIR:-/tmp}/hg-fixture-source.XXXXXX")/source"
    if [[ "$FIXTURE_JAVA" == 17 ]]; then
        # Reuse the verified SDK build's same-source distribution and isolated repository.
        candidate_repo=$(mktemp -d "${TMPDIR:-/tmp}/hg-fixture-sdk-m2.XXXXXX")
        SERVER_REPOSITORY=$FIXTURE_REPOSITORY SERVER_FETCH_REF=$FIXTURE_REF \
            bash "${FIXTURE_HELPER%/*}/../../../hugegraph-client/assembly/travis/install-candidate-sdk.sh" \
                "$FIXTURE_COMMIT" "$source_dir" "$candidate_repo"
    else
        SERVER_REPOSITORY=$FIXTURE_REPOSITORY SERVER_FETCH_REF=$FIXTURE_REF \
            bash "${FIXTURE_HELPER%/*}/../../../hugegraph-client/assembly/travis/checkout-server.sh" "$FIXTURE_COMMIT" "$source_dir"
        (cd "$source_dir" && mvn package -DskipTests -Dmaven.javadoc.skip=true -ntp)
    fi
    archive=$(find "$source_dir/hugegraph-server" -maxdepth 1 -name 'apache-hugegraph-*.tar.gz' -print)
    [[ -n "$archive" && -f "$archive" ]]
    cp "$archive" "$FIXTURE_DIR/server.tar.gz"
    export FIXTURE_ARCHIVE_NAME=${archive##*/}
    python3 "$FIXTURE_HELPER" create
    fixture_verify
    mkdir -p "$cache_dir"
    cp "$FIXTURE_DIR/server.tar.gz" "$FIXTURE_DIR/manifest.json" "$cache_dir/"
    rm -rf "$source_dir"
    if [[ -n "$candidate_repo" ]]; then rm -rf "$candidate_repo"; fi
}
fixture_copy_archive() {
    fixture_validate_java
    fixture_verify
    local name
    name=$(python3 "$FIXTURE_HELPER" name)
    cp "$FIXTURE_DIR/server.tar.gz" "./$name"
}
source "${FIXTURE_HELPER%/*}/service-wait.sh"

case "${FIXTURE_MODE:-standalone}" in
    build) fixture_build ;;
    start) fixture_copy_archive ;;
    standalone) fixture_build; fixture_copy_archive ;;
    *) echo 'Invalid fixture mode' >&2; exit 1 ;;
esac
