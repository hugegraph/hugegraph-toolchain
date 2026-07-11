# Hubble2 Code Quality Lessons

> Candidate-only reflection；不是实时进度源。候选规则不得自动写入 AGENTS.md 或 Memory。

## Session Summary

- Goal：将 Hubble2 提升到质量稳定、CI 可诊断且核心路径具有最小有效测试保障的状态。
- Outcome：本地实现、package/live acceptance 与独立 review 已完成；真实最终 CI 因 GitHub quota 延迟，目标尚未完成。
- Key evidence：FE lint/Jest/build、BE 121 tests、Client/Loader 联动、Hubble package/sidecar audit、精确 Server acceptance、独立 review/re-review。

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

## Lessons

### 区分外部排队与完成门禁

- Event：GitHub quota 使多数 job 长时间 queued；用户明确要求它不阻塞本地实测和 review。
- Root cause：把“当前不可立即取得”误等同于“所有工作被阻塞”，会浪费可并行的本地验证窗口。
- Effective response：继续完成本地完整门禁、live acceptance 和独立 review，同时保留真实 CI 为最终完成条件。
- Next time：外部 CI 排队时先推进所有独立本地工作；只有最终声明完成时才要求届时实际 head 的 CI 证据。

### 依赖清单必须由生成器同步

- Event：license checker 将当前 runtime snapshot 与旧 `known-dependencies.txt` 做 diff，准确暴露 31 add/2 remove。
- Root cause：依赖图变化后没有同步受控 third-party inventory；手工只改直接依赖会遗漏传递项。
- Effective response：运行仓库生成器产生 516 项清单，再用 CI 同一检查脚本验证 exit 0，并由独立 reviewer 确认没有混入升级。
- Next time：任何改变 runtime dependency graph 的提交都应在本地重新生成并检查 third-party inventory。

### 文档 SHA 应表达证据语义而非自指当前值

- Event：reviewer 发现文档提交后，SOT 仍把其 parent 实现 SHA 称为“当前 HEAD”。
- Root cause：把“完整验证覆盖的 SHA”和“不断前移的 PR HEAD”混为一谈。
- Effective response：拆成产品验证基线、review checkpoint 和最终 CI 的届时实际 PR head；re-review 确认 finding resolved。
- Next time：证据文件记录稳定的“验证覆盖 SHA/观测 checkpoint”，避免在将改变 HEAD 的提交中写静态“当前 HEAD”。

## Promotion Candidates

### HGTC-CQ-01 runtime 依赖变化同步 third-party inventory

- Proposed rule：修改 runtime dependency graph 后，运行 `regenerate_known_dependencies.sh current-dependencies.txt` 并用 `check_dependencies.sh` 验证受控清单。
- Scope：repository
- Target：repo `AGENTS.md`
- Evidence：PR #4 license checker 在清单落后时稳定失败；生成后本地同门禁 exit 0，独立复审无 finding。
- Counterexample：仅测试 scope 或不进入 runtime snapshot 的依赖变化无需改清单，但仍应运行检查确认。
- Validation：在临时分支增加/移除一个 runtime 依赖，确认检查先红；同步清单后转绿。
- Status：pending

### HGTC-CQ-02 CI quota 排队不阻塞独立本地工作

- Proposed rule：CI 因 quota queued 时继续本地门禁、证据整理和独立 review；保留最终实际 PR head 的真实 CI 作为完成门禁。
- Scope：repository
- Target：repo `AGENTS.md`
- Evidence：本轮在排队期间完成 package、sidecar、live acceptance 和 review，没有弱化最终 CI 要求。
- Counterexample：缺失外部服务使本地无法等价验证，且所有剩余工作都依赖该服务时，仍可能构成真实阻塞。
- Validation：检查恢复入口能从 CI 待证项继续，而不会重跑已通过的独立本地门禁。
- Status：pending

### HGTC-CQ-03 证据 SHA 使用稳定语义

- Proposed rule：SOT 区分 fresh-audit SHA、完整验证覆盖 SHA、review checkpoint 和最终 CI head，避免在提交内把 parent SHA 称为当前 HEAD。
- Scope：repository
- Target：repo `AGENTS.md`
- Evidence：独立 reviewer 将 SHA 混用列为唯一 Important；语义拆分后 re-review resolved。
- Counterexample：只读报告且不会产生后续提交时，可以直接记录报告生成时的当前 HEAD，但必须带观测时间。
- Validation：提交 SOT 后检查其中 SHA 描述仍真实，不因该提交自身前移 HEAD 而过期。
- Status：pending
