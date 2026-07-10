# Hubble2 TODOs

> 唯一当前状态源。这里只维护当前任务状态；稳定决策写入 design，新运行事实写入按日期新增的
> `evidence/` 文件，历史审计不得覆盖。完成项必须回填日期、环境、版本/SHA、命令或 CI run、证据链接。

- 最近核验：2026-07-11
- 当前分支/HEAD：`hubble2` / `225140f2a0302eab5d3c35fadd071c0e65175c09`
- 改动基线：`fd64a83b29fec9a3ec25b236eac06bf68348c78c`
- CI 状态：尚未全绿；[29110373207](https://github.com/hugegraph/hugegraph-toolchain/actions/runs/29110373207) 暴露的 evidence 文件换行回归已在当前分支修复，浏览器认证根因详见 [29107231006](https://github.com/hugegraph/hugegraph-toolchain/actions/runs/29107231006)
- 产品证据：[2026-07-08 只读审计](evidence/2026-07-08-hubble2-readonly-product-audit.md)
- API 决策：[Hubble API Cleanup Design](../../../docs/superpowers/specs/2026-07-10-hubble-api-cleanup-design.md)
- 本轮证据：[2026-07-11 hardening run](evidence/2026-07-11-hubble2-hardening-run.md)

## 0. 续跑、边界与证据门禁

- [x] **H2-GATE-01：完成续跑 preflight。** 2026-07-11，Codex thread
  `019f4d43-922e-71c2-9ffe-8e52656d75ef` 的原 goal 仍为 active；原生 goal 可恢复，Codex
  heartbeat 可唤醒同一线程；以本文件和日期 evidence 作 checkpoint，暂不新增辅助脚本。证据见
  [本轮记录](evidence/2026-07-11-hubble2-hardening-run.md#续跑-preflight)。
- [ ] **H2-GATE-02：每次暂停前保存 checkpoint。** 记录已完成/进行中项、未提交改动、验证结果、
  下一步；quota reset 还须记录错误中的 reset time、时区和 Usage 核验。网络、CI、服务、Chrome
  采用有限重试和退避，不使用前台长 sleep 或 busy loop。
- [ ] **H2-GATE-03：守住授权边界。** 只操作 Hubble、必要 Loader/Client、构建/CI 和隔离 fixture；
  不接触生产、个人或非隔离数据，不修改 Server/PD 仓库，不合并或发布。Server 单机和分布式环境
  可直接使用最新 master；若发现 Server/PD 问题，写入本文件的 Server 专项并跳过依赖该能力的步骤，
  继续其他可执行任务。
- [ ] **H2-GATE-04：新发现先分级再修复。** P0/P1/P2 写入本文件；外部缺失能力必须在准确
  Hubble 调用点写明原因、依赖和解除条件的代码 TODO，并在这里同步 blocker 状态。
- [ ] **H2-GATE-05：证据可复现且不伪造。** 每个完成项回填日期、环境、Toolchain/Server/PD
  版本或 SHA、配置、命令/退出码、日志/截图/JSON、CI run URL；mock 仅作单测，不作 E2E 证据。

## P0

- [x] **H2-P0-00：修复 `CI=true` production build 的完整 ESLint 门禁。** 2026-07-11 在 CI 固定
  Node `18.20.8` 真实复现：i18n 通过后 CRA 将既有 warnings 作为 errors，覆盖 ERView、算法、分析、
  Schema/Meta/Task 等多模块。禁止恢复 `CI:false`、禁用规则或忽略退出码；按根因分批修复并以完整
  production build exit 0 收口。原 59 files/183 warnings 已清零；主线 fresh 验证全 JS ESLint
  exit 0、Jest 21 suites/69 tests、i18n 1447/1447、`CI=true yarn build` exit 0。证据见
  [本轮记录](evidence/2026-07-11-hubble2-hardening-run.md#ci-true-production-build-green)。
- [ ] **H2-P0-01：修复 CI 浏览器 smoke 登录态。** Chromium 已安装成功，但 UI smoke 未建立
  真实 Hubble 会话，业务 API 返回 401。必须通过发布包 + 真实服务真实登录或复用 live acceptance
  cookie；禁止关闭 CI、跳过测试、`continue-on-error`、忽略退出码、硬编码成功、弱化断言或伪造响应。
- [ ] **H2-P0-02：核验 Server PR #3008 外部前置。** 2026-07-11 实查 head
  `3bd990d8b58e81cb61e3b85c287d34243836f181`，OPEN、MERGEABLE、BLOCKED/REVIEW_REQUIRED；匹配
  head 的 [CI run 29113817117](https://github.com/apache/hugegraph/actions/runs/29113817117) 尚在运行。
  Hubble CI 动态使用该 PR head。只核验和记录，不修改 Server 仓库；若长期阻断双模式
  真实环境则暂停询问用户。

## Server / PD 外部事项（只记录，不修改其仓库）

- [ ] **H2-SERVER-01：双模式运行前固定最新 master SHA。** non-PD 与 PD 环境分别记录实际使用的
  Server master SHA、构建命令和配置；若能力失败，记录复现、日志、依赖与解除条件，跳过直接依赖
  的验收步骤并继续其余任务。只有真实测试环境长期无法建立时才暂停询问用户。
- [ ] **H2-P0-03：实现并验证只清数据、保留 schema。** 当前两种清理模式最终使用同一
  `clearGraph`；在 Server 提供独立能力前遵循
  [过渡设计](../../../docs/superpowers/specs/2026-07-10-hubble-api-cleanup-design.md#graph-clear-transitional-behavior)，
  不伪实现、不声称 schema 一定保留，并在准确调用点保留原因/依赖/解除条件 TODO。

## P1

- [ ] **H2-P1-01：处理 `/super`、`/resource`、`/role` 导航死链。** 隐藏或禁用未接入入口，
  或补齐真实路由；必须用 Chrome 验证入口与直接路由不产生意外 404。
- [ ] **H2-P1-02：增加 Dashboard 外链健康检查。** 校验协议、地址和可达性，并展示清晰的
  不可用/配置提示；必须用 Chrome 验证可达与强制失败场景。
- [ ] **H2-P1-03：改善 GraphDetail statistics 失败态。** 为缺失 message/cause 提供可行动
  fallback，单次失败只提示一次，不显示原始堆栈或不可读响应；前后端测试 + Chrome 强制失败验收。
- [ ] **H2-P1-04：扩展真实浏览器 route/mutation smoke。** CI 使用发布包、真实 Server/Hubble、
  真实认证/API/路由/mutation，覆盖 GraphDetail、Schema/Meta、Datasource、Task 及隔离 fixture 的
  create/update/delete/clear；Playwright 不替代人工 Chrome 验收。
- [ ] **H2-P1-05：错误体验统一验收。** 范围内错误提示清晰、非空、可行动、不重复；页面不展示
  原始堆栈或不可读响应；强制失败矩阵逐项留证。
- [x] **H2-P1-06：修复完整前端 Jest 的 `lodash-es` ESM 加载失败。** 2026-07-11 本机
  Node `25.6.0` / Yarn `1.22.22` 下 20 个 suite 中 4 个在加载阶段失败，16 个 suite/58 tests 通过；
  Node `18.20.8` 复现；i18n 聚合改用已直接依赖的 CommonJS `lodash`，陈旧 App 模板测试改为验证
  router/layout 装配且隔离无关 Graphin 依赖。完整 Jest fresh run：20 suites、65 tests 全通过，exit 0；
  未跳过 suite。证据见[本轮记录](evidence/2026-07-11-hubble2-hardening-run.md#前端-jest-修复)。

## P2

- [ ] **H2-P2-01：展示 Gremlin 执行历史失败详情。** 当前只持久化 FAILED 与 duration；详情必须
  可读且避免泄露不可控原始响应。
- [ ] **H2-P2-02：清理英文模式中文文案。** 按完整 route matrix 处理 Navigation、Account、Role、
  Resource、GraphDetail、Schema/Meta、Datasource、Task、Gremlin、登录/会话等；产品可控中文残留为 0，
  外部返回内容例外逐项记录。
- [ ] **H2-P2-03：治理可复现 legacy `.catch()` 和依赖兼容 warning。** 仅处理范围内有明确收益、
  可复现且不会引入无关重构的项目。

## 1. 本地构建、测试与发布包门禁

- [x] **H2-LOCAL-01：Java 11 后端编译/测试通过。** 2026-07-11，Temurin `11.0.22`：
  `mvn test -P unit-test -pl hugegraph-hubble/hubble-be -ntp` exit 0，103 tests、0 failure/error/skip；
  证据见[本轮记录](evidence/2026-07-11-hubble2-hardening-run.md#java-11-后端门禁)。
- [x] **H2-LOCAL-02：前端测试与 i18n 门禁通过。** 2026-07-11 Node `18.20.8` / Yarn `1.22.22`：
  全 JS ESLint exit 0；Jest 21 suites/69 tests passed；i18n 1447/1447、866 static keys，exit 0。
  最终 diff 固定后仍由 `H2-LOCAL-06` 新鲜重跑。
- [x] **H2-LOCAL-03：前端 production build 通过。** 2026-07-11 `CI=true`，未跳过/忽略退出码，
  `yarn build` exit 0，生成 `hubble-fe/build`。依赖 source-map 与 bundle-size warnings 已记录；最终
  diff 固定后仍由 `H2-LOCAL-06` 新鲜重跑。
- [x] **H2-LOCAL-04：必要 Client/Loader 联动验证通过。** 2026-07-11，Temurin `11.0.22`：
  `mvn install -pl hugegraph-client,hugegraph-loader -am -Dmaven.javadoc.skip=true -DskipTests -ntp`
  exit 0，root/client/loader reactor 全部 SUCCESS；最终 diff 固定后仍由 `H2-LOCAL-06` fresh rerun。
- [x] **H2-LOCAL-05：Hubble 发布包构建与审计通过。** 2026-07-11，Node `18.20.8`、Java 11：
  `mvn package -DskipTests -ntp` exit 0，Hubble/hubble-be/hubble-dist reactor 全部 SUCCESS；真实
  `apache-hugegraph-hubble-1.8.0.tar.gz` 通过 distribution check（392 JAR、275 license、43 FE
  license、10 native-bearing JAR）。证据见[本轮记录](evidence/2026-07-11-hubble2-hardening-run.md#本地-java-联动与发布包)。
- [ ] **H2-LOCAL-06：最终工作树门禁新鲜重跑。** 在最终 diff 固定后重跑所有受影响检查，完整读取
  输出并记录；旧结果不能作为完成声明依据。

## 2. non-PD / RocksDB 真实验收（第一顺位）

- [ ] **H2-NONPD-01：建立隔离真实环境。** 记录 Toolchain/Server 精确 SHA/版本、Java、RocksDB
  配置、端口、启动命令、健康检查和日志；使用发布包 Hubble，不接触非隔离数据。
- [ ] **H2-NONPD-02：Chrome 完成全部可达导航 route matrix。** 覆盖登录/会话、Navigation、Graph、
  GraphDetail、Schema/Meta、Datasource、Task、Gremlin、Account/权限及所有可达入口；意外 404、致命
  console error、渲染失败均为 0，并保存截图、console/network 与矩阵 JSON。
- [ ] **H2-NONPD-03：Chrome 完成隔离 mutation matrix。** 覆盖 graph create/update/default/delete、
  schema/meta、datasource、task/import、Gremlin 与 clear 两模式；核验后端真实状态，不以 toast 代替结果。
- [ ] **H2-NONPD-04：Chrome 完成失败与英文矩阵。** 强制认证/API/统计/destructive failure，确认每次
  只出现一个清晰可行动提示且无原始堆栈；英文模式产品可控中文残留为 0。

## 3. PD / HStore 真实验收（第二顺位）

- [ ] **H2-PD-01：建立隔离真实环境。** 记录 Toolchain/Server/PD 精确 SHA/版本、Java、HStore/PD
  配置、端口、启动命令、健康检查和日志；使用发布包 Hubble，不修改 Server/PD 仓库。
- [ ] **H2-PD-02：Chrome 完成全部可达导航 route matrix。** 覆盖与 non-PD 同等页面及 PD 特有路径；
  意外 404、致命 console error、渲染失败均为 0，保存截图、console/network 与矩阵 JSON。
- [ ] **H2-PD-03：Chrome 完成隔离 mutation matrix。** 覆盖与 non-PD 同等关键 mutation 和 PD 特有
  行为，使用独立 fixture 并核验后端真实状态。
- [ ] **H2-PD-04：Chrome 完成失败与英文矩阵。** 与 non-PD 使用同一验收标准；外部返回中文例外
  单列来源与不可控性。

## 4. 远程 CI、独立审查与发布就绪结论

- [x] **H2-REMOTE-01：提交并推送当前 `hubble2` 分支用于验证。** 2026-07-11，提交
  `225140f2a0302eab5d3c35fadd071c0e65175c09` 已推送到 `hugegraph/hubble2`；提交前
  `git diff --cached --check` exit 0 并检查敏感字段，未合并、未发布、未修改 Server/PD 仓库。
- [ ] **H2-REMOTE-02：Hubble CI 和最终 diff 触发的全部检查真实全绿。** 记录最终 Toolchain HEAD、
  每个 run/check URL、结论与关键 artifact；排队/网络失败使用有限退避和 checkpoint，不把未运行当通过。
- [ ] **H2-REVIEW-01：独立只读 reviewer 完成最终审查。** reviewer 不得参与实现；输入完整 goal、
  最终 committed range + staged/unstaged/untracked diff、TODO/design/evidence 与测试/CI 证据；记录 reviewer
  身份、findings 和结论。无法创建 reviewer 时保持 goal active 并询问用户。
- [ ] **H2-REVIEW-02：修复或由用户接受所有 actionable findings，并 re-review。** 修复后由独立
  reviewer 复审受影响最终 diff；再执行新鲜最终门禁，记录身份、结果和证据。
- [ ] **H2-DONE-01：发布就绪核对。** 所有可控 TODO 有证据且完成，仅保留已核验的外部缺失能力或
  用户明确接受 blocker；未解决高严重度问题为 0，TODO/design/evidence 三者一致后方可完成 goal。
