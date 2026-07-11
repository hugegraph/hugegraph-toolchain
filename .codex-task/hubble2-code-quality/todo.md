# Hubble2 Code Quality TODO

> 唯一实时状态源。这里只保存可检查事项、状态与 evidence 链接；执行规则见 [`plan.md`](plan.md)，跨会话恢复见 [`progress.md`](progress.md)。旧 `.codex-task/hubble2-hardening/` 仅作历史基线。

- 初始化日期：2026-07-11
- 当前分支：`hubble2`
- 审计日期/HEAD：2026-07-11 / `b66848d308d58e6899cab7eb92589f1789b62259`
- 工作树边界：26 个 fresh audit 前已存在的 Hubble BE Java 修改，`+150/-57`
- 初始/审计后 TODO 数：25 / 30
- 状态：fresh audit、server-core 依赖解析恢复及 BE lint/compile 清理已完成
- 当前阶段：Phase 3 / Phase 5；等待 FE 第三方 warning 决策

## Phase 0：基线与所有权

- [x] **CQ-BASE-01** 记录 HEAD、工作树已有改动、文件所有权和环境版本。证据：[`evidence/2026-07-11-fresh-audit.md`](evidence/2026-07-11-fresh-audit.md)。
- [x] **CQ-BASE-02** 枚举 FE/BE lint、compile/code-style warning 并分类；确认 checkstyle 当前不会因已报告问题失败。证据：fresh audit。
- [ ] **CQ-BASE-03** 完成本地 cold/warm 分段耗时基线：FE build/Jest 已采样；BE compile/test/package 待依赖解析恢复后补采。
- [ ] **CQ-BASE-04** 当前 HEAD 真实 Hubble CI step/cache/重复工作基线；旧 run 仅保留历史对照。
- [x] **CQ-BASE-05** 稳定复现 server-core 依赖错误并固定当前 HEAD/版本/日志；旧失败 run 日志已过期不可用。证据：fresh audit。
- [x] **CQ-BASE-06** 建立认证、Graph/GraphSpace、Schema、Gremlin、Datasource/Loader/Task 覆盖初表。证据：fresh audit。

## Phase 1：server-core 依赖解析前置恢复

- [x] **CQ-COMPAT-01** 验证发布 POM `${revision}` 根因与 `hugegraph-core -> hg-store-common -> hugegraph-struct` 路径。证据：fresh audit Phase 1。
- [x] **CQ-COMPAT-02** 通过排除 Hubble 未使用的 `hg-store-common` 恢复依赖解析，版本/API 不变。证据：dependency tree + compile。
- [x] **CQ-COMPAT-03** 清理 stale JaCoCo 后 BE 119 unit tests、Client/Loader install 联动均通过；联动耗时 41.04s。

## Phase 2：lint 与 code-style

- [x] **CQ-LINT-01** 329 个 JS/JSX 文件显式 ESLint 0 warning/error；`yarn lint` 已接入真实 CI。
- [x] **CQ-LINT-02** 修复唯一 TS 文件缺少 parser services 的配置阻塞及 36 个机械 lint error；全量 330 文件 0 warning/error。
- [x] **CQ-LINT-03** Hubble BE checkstyle 由 128 项降为 0，javac unchecked warning 由 49 项降为 0，并清理本项目 deprecation warning；compile、119 unit、checkstyle 均通过。
- [ ] **CQ-LINT-04** 复核所有行为敏感 warning，确保已拆批、补测或提交用户决策。
- [x] **CQ-LINT-05** React Router warning 6 -> 0；第三方 source-map/act/defaultProps warning 已隔离并登记依赖升级决策。证据：[`evidence/2026-07-11-phase2-quality.md`](evidence/2026-07-11-phase2-quality.md)。
- [x] **CQ-LINT-06** 定位 severity 阈值错配；仅对当前 0 diagnostics 的 Hubble BE 启用 info 级失败，mutation A/B 证明 1 violation 时 exit 1。全仓 15 条 debt 不扩入本目标。

## Phase 3：构建与 CI 性能

- [x] **CQ-PERF-01** 已量化 Compile、Prepare、重复 FE build 与 Server package；Server package 为稳定高价值慢点。
- [ ] **CQ-PERF-02** 已实施精确 Server SHA tarball cache 并完成静态/cache-hit 验证；待至少 3 次同条件 warm CI 中位数 before/after。
- [ ] **CQ-PERF-03** 将高风险或收益不确定方案记录为用户决策项，不擅自实施。

## Phase 4：server-core 运行兼容

- [ ] **CQ-COMPAT-04** 在依赖解析恢复后确认是否存在 Java API/二进制/运行兼容错误。
- [ ] **CQ-COMPAT-05** 为真实运行兼容错误添加最小复现并实施范围内修复；若无则保存否证。
- [ ] **CQ-COMPAT-06** 完成 Hubble package/API acceptance 与真实 CI 兼容验证。

## Phase 5：核心 API 测试

- [x] **CQ-TEST-01** 完成核心 API 覆盖矩阵；仅 Schema view 存在最小关键缺口。
- [x] **CQ-TEST-02** 新增 Schema view 成功映射合同。
- [x] **CQ-TEST-03** 新增缺失 property key 的现有失败合同。
- [x] **CQ-TEST-04** mutation RED 1 failure、GREEN targeted 2/2、full BE 121/121；未重复既有覆盖。证据：Phase 2 evidence。

## Phase 6：最终门禁与审查

- [ ] **CQ-FINAL-01** 新鲜运行完整 FE/BE、联动、package/audit 门禁并保存耗时与退出码。
- [ ] **CQ-FINAL-02** 确认必需 Hubble CI 在最终 head 真实通过。
- [ ] **CQ-FINAL-03** 独立只读 reviewer 完成最终 diff 审查并记录 reviewer identity/result。
- [ ] **CQ-FINAL-04** 修复 actionable findings，完成受影响范围复审且无未解决高严重度问题。
- [ ] **CQ-FINAL-05** 更新最终 progress，并用 reflection candidate-only 模式完成 lessons。

## 用户决策项

- **DEC-FE-01（待决策）**：是否批准精确 pin `react`/`react-dom` 18.2.0，以清除 18.3 的迁移期 Jest warning，并恢复 Maven production build 的 `CI=true`。风险低；回滚为独立 commit；验证为 148 Jest、production build、关键 UI、package/audit/API。
- **DEC-FE-02（待决策）**：是否接受 52 条已定位的第三方发布制品 source-map warning。兼容世代内无可证明有效的升级：X6 major 仍缺源、Dagre override 有布局风险、Antd major 会改变 UI。建议接受并保留计数证据；不关闭 source map、不做 loader 过滤、不 patch 第三方包。
- **DEC-CI-02（暂缓）**：FE build 去重、release audit 去重、Maven cache 合并仍属收益/等价性未充分证明的 workflow 重构，不实施。
