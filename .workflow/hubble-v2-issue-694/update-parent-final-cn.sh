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

PARENT_DOC="${1:-https://hugegraph.feishu.cn/wiki/GkluwBcEViwKRikppZochbognVd}"
CHINESE_TEXT="${2:-本次 issue #694 的实现、验证与审查材料已经完成归档。实现与 rules 合规复盘文档、reviewer 审查报告均已作为本页面的子文档挂载；后续审查时请重点关注 evidence 目录中的 live-loader-flow、ui-full-acceptance、binary inventory 和 API boundary inventory，并保留 shortestPath/shortpath 是当前唯一已验证 Hubble BE algorithm 范围的说明。}"

fetch_output="$(
  lark-cli docs +fetch \
    --as user \
    --api-version v2 \
    --doc "${PARENT_DOC}" \
    --detail with-ids \
    --format json
)"

block_id="$(
  FETCH_OUTPUT="${fetch_output}" python3 - <<'PY'
import html
import json
import os
import re

payload = json.loads(os.environ["FETCH_OUTPUT"])
content = payload["data"]["document"]["content"]

paragraphs = []
for match in re.finditer(r"<p\b([^>]*)>(.*?)</p>", content, re.S):
    attrs = match.group(1)
    body = match.group(2)
    id_match = re.search(r'\bid="([^"]+)"', attrs)
    if not id_match:
        continue
    text = re.sub(r"<[^>]+>", "", body)
    text = html.unescape(text).strip()
    if not text:
        continue
    letters = sum(ch.isascii() and ch.isalpha() for ch in text)
    cjk = sum("\u4e00" <= ch <= "\u9fff" for ch in text)
    # Treat the final paragraph with meaningful English text as the tail note.
    if letters >= 20 and letters > cjk:
        paragraphs.append((id_match.group(1), text))

if not paragraphs:
    raise SystemExit("Cannot find a final English paragraph in parent document")

print(paragraphs[-1][0])
PY
)"

lark-cli docs +update \
  --as user \
  --api-version v2 \
  --doc "${PARENT_DOC}" \
  --command block_replace \
  --block-id "${block_id}" \
  --content "<p>${CHINESE_TEXT}</p>"

printf 'updated_block_id=%s\n' "${block_id}"
