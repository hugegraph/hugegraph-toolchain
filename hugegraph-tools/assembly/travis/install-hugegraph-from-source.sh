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

# Tools and Client use the same authenticated HTTP/HTTPS test servers.
# Share the locked checkout and same-source dependency installation contract.
REPO_ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
exec bash "$REPO_ROOT/hugegraph-client/assembly/travis/install-hugegraph-from-source.sh" "$@"
