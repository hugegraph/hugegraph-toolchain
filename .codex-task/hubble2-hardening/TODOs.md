# Hubble2 TODOs

> 唯一当前状态源。只在这里更新任务状态；审计记录事实，设计文档记录决策，Dashboard 只读展示本文件。

- 最近核验：2026-07-11
- Toolchain 基线：`fd64a83b29fec9a3ec25b236eac06bf68348c78c`
- 最新 CI：[29108595125](https://github.com/hugegraph/hugegraph-toolchain/actions/runs/29108595125)（failure）；认证根因详见 [29107231006](https://github.com/hugegraph/hugegraph-toolchain/actions/runs/29107231006)
- 产品证据：[2026-07-08 只读审计](evidence/2026-07-08-hubble2-readonly-product-audit.md)
- API 决策：[Hubble API Cleanup Design](../../../docs/superpowers/specs/2026-07-10-hubble-api-cleanup-design.md)

## P0

- [ ] **H2-P0-01：修复 CI 浏览器 smoke 登录态。** Chromium 已安装成功，但 UI smoke 未建立真实 Hubble 会话，业务 API 返回 401；应真实登录或复用 live acceptance cookie。
- [ ] **H2-P0-02：完成 Server PR #3008 外部前置。** 2026-07-11 观察 head `c3bd8c9e`、状态 OPEN/BLOCKED；Hubble CI 仍动态使用该 PR head。
- [ ] **H2-P0-03：实现并验证只清数据、保留 schema。** 当前两种清理模式最终使用同一 `clearGraph`；在 Server 提供独立能力前遵循 [过渡设计](../../../docs/superpowers/specs/2026-07-10-hubble-api-cleanup-design.md#graph-clear-transitional-behavior)。

## P1

- [ ] **H2-P1-01：处理 `/super`、`/resource`、`/role` 导航死链。** 隐藏或禁用未接入入口，或补齐真实路由。
- [ ] **H2-P1-02：增加 Dashboard 外链健康检查。** 校验协议、地址和可达性，并展示不可用状态。
- [ ] **H2-P1-03：改善 GraphDetail statistics 失败态。** 为缺失 message/cause 提供明确 fallback，并避免重复提示。
- [ ] **H2-P1-04：扩展浏览器 route/mutation smoke。** 覆盖 GraphDetail、Schema/Meta、Datasource、Task，以及隔离 fixture 下的 create/update/delete/clear。

## P2

- [ ] **H2-P2-01：展示 Gremlin 执行历史失败详情。** 当前只持久化 FAILED 与 duration。
- [ ] **H2-P2-02：清理剩余英文模式中文文案。** 按 route matrix 处理 Account、Role、Resource、GraphDetail 等页面。
- [ ] **H2-P2-03：治理可复现的 legacy `.catch()` 和依赖兼容 warning。** 只处理有明确收益的范围。

## 最近完成

- [x] **H2-DONE-01：完成 graph API canonical cleanup。** 已移除无用 facade、保留 Client 兼容边界并加强清图确认。
- [x] **H2-DONE-02：修复 Playwright Chromium CI 环境。** Linux runner 的缓存、浏览器和系统依赖安装均通过。
- [x] **H2-DONE-03：清理历史 worktree、progress、plan 和中间 evidence。** 当前只保留本文件、最新审计及其截图。
