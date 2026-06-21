#!/usr/bin/env bash
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
set -euo pipefail

PARENT_NODE_TOKEN="${1:-GkluwBcEViwKRikppZochbognVd}"
EXISTING_DOC_TOKEN="${2:-}"
TITLE="Hubble V2 issue 694 rules compliance review"
CONTENT_FILE=".workflow/hubble-v2-issue-694/rules-compliance-review.xml"

if [[ -n "${EXISTING_DOC_TOKEN}" ]]; then
  create_output=""
  doc_token="${EXISTING_DOC_TOKEN}"
else
  create_output="$(
    lark-cli wiki +node-create \
      --as user \
      --parent-node-token "${PARENT_NODE_TOKEN}" \
      --title "${TITLE}" \
      --format json
  )"

  doc_token="$(
    printf '%s' "${create_output}" | python3 -c '
import json
import sys

payload = json.load(sys.stdin)
data = payload.get("data", payload)
for key in ("obj_token", "document_id", "doc_token"):
    if data.get(key):
        print(data[key])
        break
else:
    node = data.get("node", {})
    for key in ("obj_token", "document_id", "doc_token"):
        if node.get(key):
            print(node[key])
            break
    else:
        raise SystemExit("Cannot find created doc token in lark-cli output")
'
  )"
fi

lark-cli docs +update \
  --as user \
  --api-version v2 \
  --doc "${doc_token}" \
  --command overwrite \
  --content "@${CONTENT_FILE}"

if [[ -n "${create_output}" ]]; then
  printf '%s\n' "${create_output}"
fi
