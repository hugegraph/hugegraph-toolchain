#!/usr/bin/env bash
set -euo pipefail

PARENT_DOC="${1:-https://hugegraph.feishu.cn/wiki/GkluwBcEViwKRikppZochbognVd}"
HEADING_KEYWORD="${2:-Final Traceability Review - 2026-06-09}"
IMPLEMENT_URL="${3:-https://hugegraph.feishu.cn/wiki/ZQuKwRZlyiE5lkk4hiuc6KrNnlw}"
REVIEWER_URL="${4:-}"

if [[ -z "${REVIEWER_URL}" ]]; then
  echo "Usage: $0 <parent-doc-url> <heading-keyword> <implement-url> <reviewer-url>" >&2
  echo "Missing reviewer-url. Create reviewer report first, then rerun this script." >&2
  exit 2
fi

fetch_output="$(
  lark-cli docs +fetch \
    --as user \
    --api-version v2 \
    --doc "${PARENT_DOC}" \
    --detail with-ids \
    --format json
)"

parse_output="$(
  FETCH_OUTPUT="${fetch_output}" HEADING_KEYWORD="${HEADING_KEYWORD}" python3 - <<'PY'
import html
import json
import os
import re

payload = json.loads(os.environ["FETCH_OUTPUT"])
keyword = os.environ["HEADING_KEYWORD"]
content = payload["data"]["document"]["content"]

blocks = []
open_tag_pattern = re.compile(
    r"<(?P<tag>h[1-9]|p|li|table|blockquote|pre|callout|grid|checkbox|hr)\b"
    r"(?P<attrs>[^>]*)/?>(?:.*?</(?P=tag)>)?",
    re.S,
)
for match in open_tag_pattern.finditer(content):
    tag = match.group("tag")
    attrs = match.group("attrs") or ""
    id_match = re.search(r'\bid="([^"]+)"', attrs)
    if not id_match:
        continue
    block = match.group(0)
    if tag.startswith("h"):
        close = re.search(rf"<{tag}\b[^>]*>.*?</{tag}>", content[match.start():], re.S)
        if close:
            block = close.group(0)
    text = re.sub(r"<[^>]+>", "", block)
    text = html.unescape(text).strip()
    blocks.append({"id": id_match.group(1), "tag": tag, "text": text})

start = None
for index, block in enumerate(blocks):
    if block["tag"].startswith("h") and (keyword in block["text"] or "最终可追溯性审查" in block["text"]):
        start = index
        break

if start is None:
    raise SystemExit(f"Cannot find heading containing: {keyword}")

print(json.dumps({
    "heading_id": blocks[start]["id"],
    "delete_ids": [block["id"] for block in blocks[start + 1:]]
}, ensure_ascii=False))
PY
)"

heading_id="$(
  printf '%s' "${parse_output}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["heading_id"])'
)"

delete_ids="$(
  printf '%s' "${parse_output}" | python3 -c 'import json,sys; print(",".join(json.load(sys.stdin)["delete_ids"]))'
)"

if [[ -n "${delete_ids}" ]]; then
  lark-cli docs +update \
    --as user \
    --api-version v2 \
    --doc "${PARENT_DOC}" \
    --command block_delete \
    --block-id "${delete_ids}"
fi

lark-cli docs +update \
  --as user \
  --api-version v2 \
  --doc "${PARENT_DOC}" \
  --command block_replace \
  --block-id "${heading_id}" \
  --content "<h1>最终可追溯性审查 - 2026-06-09</h1><p>本节用于修正 CC 汇总中容易误读的地方：Hubble 发版就绪性的核心链路已经完成验证，但算法支持范围不能写成所有界面算法均已通过。</p><h2>归档文档</h2><ul><li><a href=\"${IMPLEMENT_URL}\">实现与规则合规复盘文档</a>：记录需求、设计、任务、执行过程和证据链的完整对照。</li><li><a href=\"${REVIEWER_URL}\">审查报告文档</a>：面向审查者汇总结论、证据、风险、边界和复跑步骤。</li></ul><h2>已验证通过的核心链路</h2><ul><li>Hubble 分发包构建、启动和健康检查已通过。</li><li>GraphServer 与 Loader 联调链路已通过，覆盖图模式、CSV 上传、文件映射、导入任务、作业状态、Gremlin 计数和 <code>shortestPath</code> 对比。</li><li>浏览器前后端集成已通过，图管理、元数据配置、数据导入、数据分析和异步任务五个核心路由均匹配预期的 Hubble 后端接口，并已保存截图。</li><li>运行态国际化切换已通过，中文与英文切换均有截图和文本变化证据。</li><li>二进制包清单检查已通过，候选包包含必要的法律文件和目录，未发现运行残留文件。</li></ul><h2>必须保留的范围说明</h2><ul><li>接口边界清单显示：前端算法入口共 16 个，Hubble 后端已支持 1 个，仍属于前端展示或接口边界缺口的入口为 15 个。</li><li>当前已验证的 Hubble 后端算法范围是 <code>shortestPath</code>/<code>shortpath</code>；其他前端列出的算法入口不能在审查结论或发版说明中描述为 Hubble 后端已支持。</li><li><code>dataset/*.zip</code> 没有被本工作区的 Git 跟踪；<code>git ls-files dataset</code> 为空，且 <code>.gitignore</code> 会忽略 <code>dataset/*.zip</code>。</li><li>Hubble 分发包不包含 <code>dataset/</code> 目录。</li><li>算法验收口径应表述为 <code>shortestPath</code> 已通过，非 <code>shortestPath</code> 算法的接口缺口已记录；不能表述为全部算法已通过。</li><li>“无发版阻塞项”只适用于已经验证的范围：构建、启动、导入、Gremlin 查询、<code>shortestPath</code>、界面冒烟、国际化切换和二进制包合规检查。</li><li>本地数据集归档文件仅作为冒烟测试输入；在来源、版权、许可证和再分发条款完成审查前，不纳入源码包或二进制发版产物。</li><li>最终 ASF 投票审查仍需要针对真实候选发版产物检查源码包、签名、校验和、LICENSE、NOTICE 以及依赖许可证一致性。</li></ul><h2>审查结论</h2><p>当前证据足以支持 issue #694 在构建、运行、GraphServer 与 Loader 联调、浏览器集成、运行态国际化、二进制包清单和接口边界分类这些验收门禁上收口。最终 issue comment 或 PR review 必须明确写出算法范围仅覆盖 <code>shortestPath</code>，避免把界面中列出的算法全部写成已验证通过。</p>"

printf 'replaced_section_heading_id=%s\n' "${heading_id}"
