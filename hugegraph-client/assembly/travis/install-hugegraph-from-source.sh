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

if [[ $# -lt 1 || $# -gt 2 || ( $# -eq 2 && "$2" != "--build-only" ) ]]; then
    echo "Usage: install-hugegraph-from-source.sh <commit SHA> [--build-only]" >&2
    exit 1
fi

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
bash "$SCRIPT_DIR/checkout-server.sh" "$1" hugegraph
# Install the complete same-source reactor, including Common and PD client/gRPC.
# The calling CI supplies an isolated Maven repository via MAVEN_ARGS.
(cd hugegraph && mvn install -DskipTests -Dmaven.javadoc.skip=true -ntp)
if [[ "${2:-}" != "--build-only" ]]; then
    ARCHIVES=(hugegraph/hugegraph-server/apache-hugegraph-*.tar.gz)
    [[ ${#ARCHIVES[@]} -eq 1 && -f "${ARCHIVES[0]}" ]]
    bash "$SCRIPT_DIR/start-hugegraph-servers.sh" "${ARCHIVES[0]}"
fi
