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
PARENT_DOC_URL="${2:-https://hugegraph.feishu.cn/wiki/GkluwBcEViwKRikppZochbognVd}"
IMPLEMENT_DOC_TOKEN="${3:-ChX0d9T2Low031xl75HcMEDMnob}"
REVIEWER_URL="${4:-}"

echo "Updating implement/rules compliance document..."
.workflow/hubble-v2-issue-694/create-rules-compliance-doc.sh \
  "${PARENT_NODE_TOKEN}" \
  "${IMPLEMENT_DOC_TOKEN}"

echo "Creating reviewer report document under parent wiki node..."
if [[ -z "${REVIEWER_URL}" ]]; then
  reviewer_output="$(
    .workflow/hubble-v2-issue-694/create-reviewer-report-doc.sh \
    "${PARENT_NODE_TOKEN}"
  )"
  printf '%s\n' "${reviewer_output}"

  REVIEWER_URL="$(
    REVIEWER_OUTPUT="${reviewer_output}" python3 - <<'PY'
import os
import re

urls = re.findall(r"https://hugegraph\.feishu\.cn/wiki/[A-Za-z0-9]+", os.environ["REVIEWER_OUTPUT"])
if not urls:
    raise SystemExit("Cannot find reviewer wiki url in create output")
print(urls[-1])
PY
  )"
fi

echo "Replacing parent Final Traceability Review section with Chinese..."
.workflow/hubble-v2-issue-694/replace-parent-final-traceability-cn.sh \
  "${PARENT_DOC_URL}" \
  "Final Traceability Review - 2026-06-09" \
  "https://hugegraph.feishu.cn/wiki/ZQuKwRZlyiE5lkk4hiuc6KrNnlw" \
  "${REVIEWER_URL}"
