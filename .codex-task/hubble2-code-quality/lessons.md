# Hubble2 Code Quality Lessons
> 本文件不是实时进度源。目标结束前使用 `reflection` skill 的 candidate-only 模式更新；候选规则不得自动写入 AGENTS.md 或 Memory。

## 已复用的历史经验

### 1. 分层验证比逐项完整构建更高效

- 证据来源：Hubble2 hardening 的批内 targeted checks、阶段完整门禁和最终 package/CI 分层实践。
- 本轮应用：按共享验证面批处理，完整 build/package 仅在阶段边界和最终 diff 冻结后运行。
- 不适用情况：公共契约变化、难定位回归或高风险修改必须提前拆批并扩大验证。

### 2. 历史 mvnd 复用经验在本轮工具合同下失效

- 证据来源：历史最终门禁中 Hubble BE 约 13 秒、Client UnitTestSuite 约 4 秒、Client+Loader install 约 37 秒；完整 Hubble package 仍接近 2 分钟。
- 本轮结论：当前 mvnd agent 要求 Java 17，与项目 Java 11 合同冲突；统一使用 Java 11 + Maven 3.9.11，并单独记录 cold/warm 数据。
- 不适用情况：只有后续 mvnd 明确支持当前 Java 合同，且与 CI 原生 Maven 做过等价性验证后，才可重新评估 daemon 复用。

### 3. 性能优化必须保留真实门禁

- 证据来源：历史 `CI=true` build 曾暴露 59 files/183 warnings；隐藏 warning 会造成错误绿色。
- 本轮应用：只消除重复工作、无效缓存和不必要串行等待，不删除测试或弱化断言。
- 不适用情况：无；若优化只能通过降低覆盖获得收益，应作为拒绝或用户决策项。

## 本轮 reflection 候选

当前尚无本轮证据化候选。目标结束时，每项候选必须包含：建议规则、适用范围、目标文件、证据、反例或不适用情况、验证方法和 `pending/accepted/rejected/promoted` 状态。
