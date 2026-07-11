# Hubble2 Code Quality TODO

> 唯一实时状态源。这里只保存可检查事项、状态与 evidence 链接；执行规则见 [`plan.md`](plan.md)，跨会话恢复见 [`progress.md`](progress.md)。旧 `.codex-task/hubble2-hardening/` 仅作历史基线。

- 初始化日期：2026-07-11
- 当前分支：`hubble2`
- 审计日期/HEAD：2026-07-11 / `b66848d308d58e6899cab7eb92589f1789b62259`；2026-07-12 旧 review 复核基线 `e76544c70055f7fcb97c1c499f7ceee463e77696`
- 完整本地门禁覆盖的产品实现 SHA：`10c0e8fa4f18694f8f848212ed1da52ef36d80a4`
- 独立整体 review checkpoint：`03be1bd7b9e2cbdbac90499821bbf4ce147124ac`
- 最新已推送代码 checkpoint：`e76544c70055f7fcb97c1c499f7ceee463e77696`；当前 review remediation 尚未提交
- 最终候选/真实 CI SHA：`a56fb6c1ebb27c717568bf1e0baba1cb94afa3c0`
- fresh audit 工作树边界：26 个审计前已存在的 Hubble BE Java 修改，`+150/-57`
- 初始/审计后 TODO 数：25 / 30
- 当前范围内 CQ：30 项原始审计任务完成；新增 8 项 review remediation 中 6 项本地完成、2 项待最终门禁/review；FUTURE 不计入本轮完成率
- 状态：旧 review 当前 HEAD 复核和批准项实现完成；本地阶段门禁通过，待 package/audit、真实 CI、独立 review/re-review
- 当前阶段：Phase 6 review remediation 与最终门禁

## Phase 0：基线与所有权

- [x] **CQ-BASE-01** 记录 HEAD、工作树已有改动、文件所有权和环境版本。证据：[`evidence/2026-07-11-fresh-audit.md`](evidence/2026-07-11-fresh-audit.md)。
- [x] **CQ-BASE-02** 枚举 FE/BE lint、compile/code-style warning 并分类；确认 checkstyle 当前不会因已报告问题失败。证据：fresh audit。
- [x] **CQ-BASE-03** 完成本地分段基线：FE build/Jest/lint、BE compile/unit、Client+Loader install、Hubble package、精确 Server cold/cache-hit 均有当前数据。
- [x] **CQ-BASE-04** 最终候选 HEAD 真实 Hubble CI step/cache/重复工作基线已保存；精确 Server cache key/hit 与阶段耗时可复核。证据：[`evidence/2026-07-12-final-real-ci.md`](evidence/2026-07-12-final-real-ci.md)。
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
- [x] **CQ-LINT-04** 本项目 lint/Jest warning 清零；52 条第三方 source-map warning 已按来源/数量获用户接受；peer/build-stack 债务明确转入未来任务。
- [x] **CQ-LINT-05** React Router warning 6 -> 0；第三方 source-map/act/defaultProps warning 已隔离并登记依赖升级决策。证据：[`evidence/2026-07-11-phase2-quality.md`](evidence/2026-07-11-phase2-quality.md)。
- [x] **CQ-LINT-06** 定位 severity 阈值错配；仅对当前 0 diagnostics 的 Hubble BE 启用 info 级失败，mutation A/B 证明 1 violation 时 exit 1。全仓 15 条 debt 不扩入本目标。

## Phase 3：构建与 CI 性能

- [x] **CQ-PERF-01** 已量化 Compile、Prepare、重复 FE build 与 Server package；Server package 为稳定高价值慢点。
- [x] **CQ-PERF-02** 精确 Server SHA cache 本地 before/after：cold 165.26s；3 次 hit 0.41/0.40/0.35s，中位数 0.40s，约节省 99.76%；真实 CI quota 后补交叉验证。
- [x] **CQ-PERF-03** 高风险/收益不确定方案已记录并获边界决策；核心可视化栈升级明确排除在当前 goal 外。

## Phase 4：server-core 运行兼容

- [x] **CQ-COMPAT-04** 精确 PR #3008 Server tree package、Hubble package 及完整 live acceptance 未发现 Java API/二进制/运行兼容错误。
- [x] **CQ-COMPAT-05** 保存无真实运行兼容错误的否证；首次连接拒绝已证明为 daemon session 生命周期并经 foreground 有限恢复排除。
- [x] **CQ-COMPAT-06** 最终候选 HEAD 的真实 Hubble CI 已通过：精确 Server SHA、package/audit、121 unit、runtime/UI acceptance 全部成功。

## Phase 5：核心 API 测试

- [x] **CQ-TEST-01** 完成核心 API 覆盖矩阵；仅 Schema view 存在最小关键缺口。
- [x] **CQ-TEST-02** 新增 Schema view 成功映射合同。
- [x] **CQ-TEST-03** 新增缺失 property key 的现有失败合同。
- [x] **CQ-TEST-04** mutation RED 1 failure、GREEN targeted 2/2、full BE 121/121；未重复既有覆盖。证据：Phase 2 evidence。

## Phase 6：最终门禁与审查

- [x] **CQ-FINAL-01** 新鲜运行完整 FE/BE、联动、package/audit 门禁并保存耗时与退出码。证据：[`evidence/2026-07-12-final-local-gates.md`](evidence/2026-07-12-final-local-gates.md)。
- [x] **CQ-FINAL-02** 最终候选 HEAD `a56fb6c1` 的 Hubble CI、dependency license 及全部 PR checks 真实通过。
- [x] **CQ-FINAL-03** 独立只读 reviewer `/root/final_reviewer` 完成 `b66848d..03be1bd7` 整体审查；实现无高严重度 finding。
- [x] **CQ-FINAL-04** 唯一 Important（SOT SHA 语义）已修复并复审通过；依赖清单后续修复 `0a160afc..e2c6926e` 亦由同一 reviewer 复审，无 finding。
- [x] **CQ-FINAL-05** progress 已更新，并以 reflection candidate-only 模式完成 lessons；未修改 AGENTS 或 Memory。

## Phase 6B：历史 review 当前 HEAD 复核与修复

- [x] **CQ-REV-01** 修复默认图卡片使用 `isDefault` 的错误字段；默认图两种 destructive action 均不可点击，真实渲染测试在修复前 RED、修复后 GREEN。
- [x] **CQ-REV-02** 按 DEC-REV-01 移除伪 data-only 入口和 destructive GET；唯一 `POST .../{graph}/clear` 明确清除 Schema+数据，旧 GET 和 data-only facade 均不存在；FE/BE 留有 Server 能力恢复 TODO。
- [x] **CQ-REV-03** 按 DEC-REV-02 迁移个人资料 JSON PUT、401 reject、`graph`/`nullableProps` 字段，并删除退役 Super/UUAP API、页面、路由和 facade；新增成功、未认证、校验、旧端点和字段合同测试。
- [x] **CQ-REV-04** 按 DEC-REV-03 修复登录 Session 原子提交、ID 轮换、异常清理、Client/连接关闭和 standalone timeout；保留当前 Gremlin 所需的 10 分钟短期凭据。
- [x] **CQ-REV-05** readiness 严格验证 `/about` JSON；环境依赖算法非 200 记 `skipped` 而非 `passed`；CI `always()` 上传 smoke JSON、截图和日志。
- [x] **CQ-REV-06** 按 DEC-REV-04 将 Server 固定到 master 已合入提交 `99936be5f41fccd193f120e01206e3cf3c73a050`，不再动态依赖关闭的 PR #3008。
- [ ] **CQ-REV-07** 当前 remediation diff 完成 JDK 11 FE/BE、Client/Loader、package/audit、live acceptance 和真实 CI 最终门禁。
- [ ] **CQ-REV-08** 未参与实现的独立 reviewer 完成最终 diff review；finding 修复后 re-review，无未解决高严重度问题。

## 用户决策项

- **DEC-FE-01（已批准，2026-07-11）**：精确 pin `react`/`react-dom` 18.2.0，以清除 18.3 的迁移期 Jest warning，并恢复 Maven production build 的 `CI=true`。验证范围：148 Jest、production build、关键 UI、package/audit/API。
- **DEC-FE-02（已接受，2026-07-11）**：接受当前精确识别的 52 条第三方发布制品 source-map warning（X6 38、Dagre 10、Antd 4）。仅接受当前来源与数量；不关闭 source map、不做 loader 过滤、不 patch 第三方包；新增、增量或来源变化必须重新调查。
- **DEC-CI-02（暂缓）**：FE build 去重、release audit 去重、Maven cache 合并仍属收益/等价性未充分证明的 workflow 重构，不实施。
- **DEC-CI-03（已处理，2026-07-12）**：用户批准兼容前提下升级 CI actions；checkout/setup/cache/codecov/CodeQL 等已迁移当前 major，Temurin 替代 deprecated adopt。SkyWalking Eyes 当前 main 内部非失败 cache annotation 为第三方实现限制，用户已接受。
- **DEC-REV-01（已批准，2026-07-12）**：隐藏 data-only，直接迁移 destructive GET 为非 GET；Server 提供保 Schema API 后第一时间恢复高价值 data-only 功能。
- **DEC-REV-02（已批准，2026-07-12）**：Hubble 2.0 无旧契约包袱，个人资料、Axios 401、Super/UUAP、`graphe` 和 `NullableProps` 直接按合理新契约迁移，不保留兼容层。
- **DEC-REV-03（已批准，2026-07-12）**：本轮保留 Gremlin 依赖的短期 Session 凭据，只实施 Session 原子性、轮换、异常清理和资源安全；认证重构转未来。
- **DEC-REV-04（已批准，2026-07-12）**：CI 固定 HugeGraph master 已合入 commit，不再动态依赖 PR。
- **DEC-REV-05（已批准，2026-07-12）**：逐 JAR license/native allowlist 属未来 release-hardening；当前 Category-X/package/audit gate 保持不弱化。

## 未来任务：核心可视化技术栈现代化（明确不属于当前 task/goal）

> 前置条件：先交付并冻结本 goal 的稳定可用基线。以下任务只记录后续方向，本轮禁止实施、混入 commit 或用作当前完成条件。

- [ ] **FUTURE-VIS-01** 建立 Antd、X6、Graphin/G6、Dagre、React 及构建链的版本/维护状态/安全/兼容矩阵，确定目标技术栈与分阶段迁移顺序。
- [ ] **FUTURE-VIS-02** 固化核心页面视觉与交互基线：登录、导航、GraphSpace、Schema、图查询、图详情、ER 图编辑、菜单/工具栏、表格/弹窗及错误态；保存截图、关键 DOM/交互和可访问性证据。
- [ ] **FUTURE-VIS-03** 为图可视化建立布局与数据合同：节点/边数量、位置稳定性、缩放/拖拽/选择、菜单/工具栏、复杂图性能及异常/空图边界，避免升级只验证“能渲染”。
- [ ] **FUTURE-VIS-04** 分离升级 React 测试生态与 UI/图组件：先升级 Testing Library/类型/测试设施，再按独立批次迁移 Antd major、X6/Graphin/G6、Dagre；每批可单独回滚。
- [ ] **FUTURE-VIS-05** Antd major 迁移需审计组件 API、CSS token、DOM、主题、表格/表单/弹窗行为和视觉差异，并完成真实浏览器回归。
- [ ] **FUTURE-VIS-06** X6/Graphin/G6/Dagre 迁移需验证图模型转换、布局输出、事件语义、插件/React shape、序列化兼容和大图性能，禁止仅以 build/test 通过收口。
- [ ] **FUTURE-VIS-07** 清理兼容层、废弃 API、旧 polyfill、重复图工具和过期构建配置；每项删除必须有无调用证据和回滚点。
- [ ] **FUTURE-VIS-08** 输出同条件 before/after：bundle、首屏/交互耗时、布局耗时、内存、warning/debt 数量及浏览器兼容；达不到收益或稳定性门槛则不合并。
- [ ] **FUTURE-VIS-09** 清理当前 Yarn peer-dependency 债务：Graphin/lodash-es、Testing Library/types、react-json-view/flux、CRA Babel、vis-network peers、Ecomfe lint/stylelint peers；先证明真实消费路径，不以盲目补包换静默。
- [ ] **FUTURE-VIS-10** 现代化 Browserslist/CRA/build toolchain，更新浏览器数据策略并评估替代已停止演进的 CRA；同时制定 2.14 MB 主 bundle 的 code-splitting 与性能预算。

## 未来任务：Server 能力与 release hardening（明确不属于当前 task/goal）

- [ ] **FUTURE-SERVER-01** Server 提供并验证保留 Schema 的 data-only clear API 后，第一时间恢复 Hubble FE/BE “仅清数据”能力、确认文案、权限/默认图保护和成功/失败合同测试。
- [ ] **FUTURE-AUTH-01** 重构 Gremlin Basic 认证，移除 HTTP Session 中的短期明文凭据，并验证登录、查询和 token 生命周期。
- [ ] **FUTURE-REL-01** 建立逐 JAR license 映射及 native-bearing JAR allowlist/NOTICE 审计；正式 RC 使用真实发布密钥完成 `.sha512`/`.asc` 验证。
