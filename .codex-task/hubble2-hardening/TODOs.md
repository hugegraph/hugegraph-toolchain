# Hubble2 TODOs

> 唯一当前状态源。这里只维护当前任务状态；稳定决策写入 design，新运行事实写入按日期新增的
> `evidence/` 文件，历史审计不得覆盖。完成项必须回填日期、环境、版本/SHA、命令或 CI run、证据链接。
> 执行分层、并行 lane、等待/重试和 Chrome 规则见 [`EXECUTION.md`](EXECUTION.md)；本文件不承载执行规范。

- 最近核验：2026-07-11
- 当前分支/最近已验证并推送实现提交：`hubble2` /
  `bf4e66a15a2feb77ae7bddcf421c9d4b8ddc4bf6`。
- 改动基线：`fd64a83b29fec9a3ec25b236eac06bf68348c78c`
- CI 状态：后端 CI 是主路径远程门禁；前端 CI 的存量问题在双模式真实验收和最终本地门禁后处理，
  不作为 P0 或真实环境建立的前置阻塞。既有前端失败见 [29110373207](https://github.com/hugegraph/hugegraph-toolchain/actions/runs/29110373207)，浏览器认证根因见 [29107231006](https://github.com/hugegraph/hugegraph-toolchain/actions/runs/29107231006)。
- 产品证据：[2026-07-08 只读审计](evidence/2026-07-08-hubble2-readonly-product-audit.md)
- API 决策：[Hubble API Cleanup Design](../../docs/superpowers/specs/2026-07-10-hubble-api-cleanup-design.md)
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
- [x] **H2-P0-02：核验 Server PR #3008 外部前置。** 2026-07-11 再次实查固定 head
  `3bd990d8b58e81cb61e3b85c287d34243836f181`；[PR #3008](https://github.com/apache/hugegraph/pull/3008)
  已 MERGED、APPROVED，dependency、license、Server RocksDB/HBase、PD、Store、HStore 和 CodeQL 等
  15 个 checks 全部 SUCCESS，外部前置已解除。仅核验和记录，未修改 Server/PD 仓库；证据见
  [本轮记录](evidence/2026-07-11-hubble2-hardening-run.md#server-pr-3008-外部前置解除)。

## Server / PD 外部事项（只记录，不修改其仓库）

- [x] **H2-SERVER-01：双模式运行前固定最新 master SHA。** 2026-07-11 从 Apache 远端固定
  `master` 为 `99936be5f41fccd193f120e01206e3cf3c73a050`，在 `/private/tmp` shallow clone，Temurin
  `11.0.22` 执行 `mvn package -Dmaven.test.skip=true -ntp`，43 个 reactor 模块全部 SUCCESS；原本地
  Server 仓库未修改。non-PD 已使用该 SHA，PD 也以该 SHA 建立；证据见
  [本轮记录](evidence/2026-07-11-hubble2-hardening-run.md#non-pd--rocksdb-真实环境)。
- [x] **H2-P0-03：核验只清数据、保留 schema 的外部能力边界。** 2026-07-11，non-PD Chrome
  对隔离 fixture 执行 `Clear Data`；确认框明确提示当前无已验证的 Server 保留 schema 操作。最新
  Server master `99936be5` 执行后直查 vertices/propertykeys/vertexlabels/edgelabels 均为 0，证明两种
  清理仍落到同一 `clearGraph`。Hubble 不伪实现、不声称保留 schema；准确 FE/BE 调用点已保留包含
  原因、依赖与解除条件的代码 TODO。该项以已核验外部缺失能力完成记录，解除条件为 Server 提供并
  验证独立 data-only API；证据见[本轮记录](evidence/2026-07-11-hubble2-hardening-run.md#non-pd-chrome-clear-data-真实-mutation)。
- [x] **H2-P0-04：收窄 Client DELETE 200 兼容范围。** 2026-07-11 提交前审计发现通用
  `checkStatus()` 会让非 DELETE 请求也接受意外 HTTP 200；已限定为实际 DELETE request，并增加
  DELETE 正例和 POST 负例。`RestClientStatusTest,GraphsAPITest` 共 7 tests、0 failure/error，`mvnd`
  BUILD SUCCESS；未通过弱化状态断言换绿。

## P1

- [x] **H2-P1-01：处理 `/super`、`/resource`、`/role` 导航死链。** 2026-07-11 non-PD 发布包
  Chrome 验证：未接入入口不再出现在 Navigation/System Management；直接访问三个路径均 replace
  redirect 到 `/navigation`，意外 404 与渲染失败为 0。证据见
  [Chrome matrix](evidence/2026-07-11-nonpd/chrome-route-english-matrix.json)。
- [x] **H2-P1-02：增加 Dashboard 外链健康检查。** 校验协议、地址和可达性，并展示清晰的
  不可用/配置提示；必须用 Chrome 验证可达与强制失败场景。2026-07-11 复审发现当前页面加载即对
  配置地址执行 `no-cors GET`，存在浏览器侧任意内网请求风险，且 FE 接受完整 URL 的契约与 Vermeer
  `server.protocol + dashboard.address` 冲突。2026-07-11 已改为后端零出站、统一 host/port + http(s)
  契约，并仅在用户显式点击时探测。最终发布包 SHA-256
  `7f735a2c6a20223a89534ba1fe8c06006fbdf9f3c8dd521daf3d25f5f5653368` 在隔离 PD Chrome 验证：
  同步创建空白窗口后探测，成功才跳转；失败关闭窗口且只提示 1 次；浏览器阻止 popup 时探测请求为 0，
  只显示 1 条可行动英文提示。测试另覆盖非法协议/路径/凭据/query 和窗口时序。证据见
  [最终 Dashboard matrix](evidence/2026-07-11-final-dashboard/chrome-dashboard-window-matrix.json)。
- [x] **H2-P1-03：改善 GraphDetail statistics 失败态。** 为缺失 message/cause 提供可行动
  fallback，单次失败只提示一次，不显示原始堆栈或不可读响应；前后端测试 + Chrome 强制失败验收。
  2026-07-11 首次断开隔离 Server 的 Chrome 验收发现整页空白并重复 4 个连接失败 toast；已扩展为
  GraphDetail 初始化请求由页面统一拥有错误反馈，新增 metadata 全失败回归与 request transport toast
  suppression，相关针对性 Jest、scoped ESLint、i18n 1524/1524 通过。最终发布包 Chrome 断连复验：
  可行动 Alert=1、raw connection text=0、toast=0，页面不再空白；证据见
  [failure matrix](evidence/2026-07-11-nonpd/graph-detail-failure-matrix.json)。
- [x] **H2-P1-04：完成真实浏览器 route/mutation 验收。** 2026-07-11，Chrome 使用最终发布包
  `df94a648448ec3b65f631c811a4f2ab83bf67de357f0aab3c49ac545f5227e14`、真实 non-PD/RocksDB 与
  PD/HStore 服务完成全部 route/mutation matrix；Server REST 前后态和隔离 fixture 清理均已留证。先由人工 Chrome 使用发布包、真实
  Server/Hubble、真实认证/API/路由/mutation，覆盖 GraphDetail、Schema/Meta、Datasource、Task 及
  隔离 fixture 的 create/update/delete/clear；CI Playwright 后置到 `H2-P2-00`，不能替代人工 Chrome。
- [x] **H2-P1-05：错误体验统一验收。** 范围内错误提示清晰、非空、可行动、不重复；页面不展示
  原始堆栈或不可读响应；强制失败矩阵逐项留证。2026-07-11 run10b 最终发布包在两种模式精准重放
  graph API-only、statistics-only、auth、Gremlin、destructive、
  Dashboard/Schema/Account failure；当前反馈均为 1 条、raw stack/连接文本为 0，恢复路径通过。证据见
  [non-PD matrix](evidence/2026-07-11-nonpd/chrome-failure-english-matrix.json) 与
  [PD matrix](evidence/2026-07-11-pd/chrome-failure-english-matrix.json)。
- [x] **H2-P1-06：修复完整前端 Jest 的 `lodash-es` ESM 加载失败。** 2026-07-11 本机
  Node `25.6.0` / Yarn `1.22.22` 下 20 个 suite 中 4 个在加载阶段失败，16 个 suite/58 tests 通过；
  Node `18.20.8` 复现；i18n 聚合改用已直接依赖的 CommonJS `lodash`，陈旧 App 模板测试改为验证
  router/layout 装配且隔离无关 Graphin 依赖。完整 Jest fresh run：20 suites、65 tests 全通过，exit 0；
  未跳过 suite。证据见[本轮记录](evidence/2026-07-11-hubble2-hardening-run.md#前端-jest-修复)。

- [x] **H2-P1-07：保证 PD 默认图切换的单一默认不变量。** 2026-07-11，发布包 SHA-256
  `c46fb14b034b5959fc47363dd919b7c8e6c5f659783d86ead31c7cd8e3d09b86`，Server/PD/Store
  `99936be5f41fccd193f120e01206e3cf3c73a050`。Hubble 采用 set-target-first 再清理旧默认；Client
  兼容 Server DELETE 成功返回 200 JSON。定向后端/Client tests 通过；Chrome 将 `h2pdgraph` 设为唯一
  默认再恢复 `hugegraph`，两次均只有一个 Default 且无失败提示，Server API 精确返回单元素数组。最后
  通过 Chrome 删除 `h2pdgraph`，Server 仅剩且默认 `hugegraph`。证据见
  [PD mutation matrix](evidence/2026-07-11-pd/chrome-mutation-matrix.json)。

## P2

- [ ] **H2-P2-00：最后处理前端 CI 浏览器 smoke 与遗留问题。** 当前真实登录实现和单测已落地，
  但远端前端 CI 可能包含较多存量问题；仅在 non-PD、PD 真实 Chrome 验收、产品修复和最终本地门禁
  完成后集中运行与修复。必须使用发布包 + 真实服务验证认证/API/路由/mutation；不得关闭 CI、跳过
  测试、`continue-on-error`、忽略退出码、硬编码成功、弱化断言或伪造响应。其失败在该阶段前不阻塞
  双模式环境和其他产品验收，但最终发布就绪前仍须真实通过或按目标规则明确处置。
- [x] **H2-P2-01：展示 Gremlin 执行历史失败详情。** 2026-07-11 run8 旧 H2 fixture 启动日志确认幂等
  添加 `failure_reason`；Chrome 执行非法 Gremlin 后，新 FAILED 行显示一次本地化可行动原因，历史中的
  SUCCESS 与迁移前失败行不伪造详情，页面不显示 raw signature/stack。同步失败仅持久化受控 reason code；
  异步入口已在准确调用点记录 Server Task DTO 安全字段依赖 TODO。证据见
  [本轮记录](evidence/2026-07-11-hubble2-hardening-run.md#non-pd-run8-统一-chrome-复验)。
- [x] **H2-P2-02：清理英文模式中文文案。** 按完整 route matrix 处理 Navigation、Account、Role、
  Resource、GraphDetail、Schema/Meta、Datasource、Task、Gremlin、登录/会话等；产品可控中文残留为 0，
  外部返回内容例外逐项记录。2026-07-11 Meta main/dialog 已修复并 Chrome 为 0；run7 发布包已确认
  Graph Edit portal 的标题、字段、placeholder、按钮全为英文。继续盘点发现 `/task/detail/1` 的表头与
  速率单位仍为产品可控中文；run8 Chrome 已确认全部表头和单位为英文。IconSelect 唯一残留也已改用
  既有翻译并在 Vertex Style portal 实际显示 `Please select`。2026-07-11 PD/HStore 发布包真实登录后，
  Chrome 确认 `/graphspace`、`/graphspace/:graphspace/schema`、`/account` 及公共超级管理员身份存在成批
  硬编码中文，登记为 P1 英文验收 blocker；按同根因 i18n 批次集中修复并统一定向测试、构建和 Chrome
  复验。`/role/graphspace/:graphspace/:role` 当前仅为孤立 legacy 深链，最终 route matrix 记录可达性后
  决定是否纳入产品路径。最终 non-PD 17 routes、PD 18 routes 及 Meta/GraphSpace/Schema/Account/
  Datasource/Task/Gremlin 动态 portal 产品可控中文为 0；`超级管理员`、`默认图空间` 分别来自 Server/PD
  nickname，已作为外部内容例外记录。
- [x] **H2-P2-03：治理可复现 legacy `.catch()` 和依赖兼容 warning。** 2026-07-11，已修复范围内
  legacy 空/重复 `.catch()`、6 处 AntD Dropdown `overlay`、BrowserRouter future flags 和 51 个算法
  Collapse DOM props；测试与双模式 Chrome 应用 warning=0。剩余 source-map 缺源、AntD/rc React
  deprecation、Browserslist 数据和 2.15 MB bundle 属上游依赖/后续性能治理，已记录来源、解除条件并禁止
  通过关闭 source map、调高阈值或忽略退出码伪绿，不作为本次 Hubble 行为发布 blocker。

## 1. 本地构建、测试与发布包门禁

- [x] **H2-LOCAL-01：Java 11 后端编译/测试通过。** 2026-07-11，Temurin `11.0.22`：
  `mvn test -P unit-test -pl hugegraph-hubble/hubble-be -ntp` exit 0，103 tests、0 failure/error/skip；
  证据见[本轮记录](evidence/2026-07-11-hubble2-hardening-run.md#java-11-后端门禁)。
- [x] **H2-LOCAL-02：前端测试与 i18n 门禁通过。** 2026-07-11 Node `18.20.8` / Yarn `1.22.22`：
  最新 Graph/Meta/error 批次全 JS/JSX ESLint exit 0；Jest 25 suites/86 tests passed；i18n
  1548/1548、999 static keys，exit 0。
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
  license、10 native-bearing JAR）。run7 包 SHA-256 为
  `45d2f41fb54a5de0e5cf83d7bdec5590293021a4d2cc126f29cdf63c8380a79b`；证据见
  [本轮记录](evidence/2026-07-11-hubble2-hardening-run.md#non-pd-graph-批次-run7-chrome-复验)。
- [ ] **H2-LOCAL-06：最终工作树门禁新鲜重跑。** 在最终 diff 固定后重跑所有受影响检查，完整读取
  输出并记录；旧结果不能作为完成声明依据。

## 2. non-PD / RocksDB 真实验收（第一顺位）

- [x] **H2-NONPD-01：建立隔离真实环境。** 2026-07-11，Toolchain
  `cc0c58c072d3700fecfc0b3d4e7c22438d6479f2`、Server
  `99936be5f41fccd193f120e01206e3cf3c73a050` / 1.7.0、Temurin `11.0.22`；隔离 RocksDB 位于
  `/private/tmp/hubble2-hardening/nonpd-99936be5-run2/data`，Server `127.0.0.1:8080`、发布包 Hubble
  1.8.0 `127.0.0.1:18088`。真实 smoke 覆盖认证、Schema、Loader、Task、REST 计数和路径，exit 0；
  证据见 [live-smoke-run3.json](evidence/2026-07-11-nonpd/live-smoke-run3.json)。
- [x] **H2-NONPD-02：Chrome 完成全部可达导航 route matrix。** 2026-07-11 发布包 Chrome 覆盖
  登录/会话、Navigation、Graph、GraphDetail、Meta、Datasource、Task、Gremlin、Algorithms、
  AsyncTasks、My Profile 及三个 legacy 直接路由；non-PD 不可达的 PD Account/Role 按 route guard
  回退。意外 404、产品致命 console error、渲染失败均为 0；仅观察到 Chrome 扩展通信噪声且无页面
  脚本来源。run10b 最终包再次重放 17 条直接/guard/scoped routes，意外 404、render failure、产品中文和
  应用 console exception 均为 0；证据：
  [final package matrix](evidence/2026-07-11-nonpd/chrome-route-final-package-run10.json)。
- [x] **H2-NONPD-03：Chrome 完成隔离 mutation matrix。** 覆盖 graph create/update/default/delete、
  schema/meta、datasource、task/import、Gremlin 与 clear 两模式；核验后端真实状态，不以 toast 代替结果。
  2026-07-11 run7 已完成 Graph nickname update/restore，以及 Property create/delete；两者均用真实 Hubble
  API 状态复核。run10b 已完成 Chrome CSV upload → Task/import → detail → Server REST → Gremlin/Meta/
  Datasource 清理闭环；Graph create/default/delete 明确为 `PRODUCT_UNREACHABLE`，clear 两模式按已核验
  Server 合同记录为外部能力边界。证据见
  [mutation matrix](evidence/2026-07-11-nonpd/chrome-mutation-matrix.json)。
- [x] **H2-NONPD-04：Chrome 完成失败与英文矩阵。** 强制认证/API/统计/destructive failure，确认每次
  只出现一个清晰可行动提示且无原始堆栈；英文模式产品可控中文残留为 0。run10b 已精准完成 general
  graph API-only 与 statistics-only 两项：各自只失败单个 XHR，另两个 control API 均 HTTP 200，页面各
  只有 1 条 actionable Alert、raw stack=0 且恢复后消失。最终包也已重放 login/stale-session、Meta
  destructive、Gremlin failure 与 17 route/dynamic dialog 英文扫描，产品中文、意外 404、render failure、
  应用 console exception 均为 0；见
  [failure matrix](evidence/2026-07-11-nonpd/chrome-failure-english-matrix.json)。

## 3. PD / HStore 真实验收（第二顺位）

- [x] **H2-PD-01：建立隔离真实环境。** 2026-07-11，Toolchain 基线
  `bf4e66a15a2feb77ae7bddcf421c9d4b8ddc4bf6` + 当前工作 diff，Server/PD/Store master
  `99936be5f41fccd193f120e01206e3cf3c73a050` / 1.7.0，Temurin 11.0.29，HStore/PD/Store/Server/Hubble
  分别在隔离端口 `18686/18620/18610`、`18500/18520/18510`、`18080/18182`、`28088` 运行。
  最终重放发布包 SHA-256 `df94a648448ec3b65f631c811a4f2ab83bf67de357f0aab3c49ac545f5227e14`，Chrome
  真实登录、PD 导航、认证图列表与 Schema Template create/delete 后端核验通过；Server/PD 仓库未修改。
  证据见 [环境快照](evidence/2026-07-11-pd/environment.json)、
  [健康检查](evidence/2026-07-11-pd/startup-health.json) 与
  [mutation matrix](evidence/2026-07-11-pd/chrome-mutation-matrix.json)。
- [x] **H2-PD-02：Chrome 完成全部可达导航 route matrix。** 2026-07-11，最终发布包 SHA-256
  `df94a648448ec3b65f631c811a4f2ab83bf67de357f0aab3c49ac545f5227e14`，Chrome 重放 18 条可达/legacy
  路由；意外 404、渲染失败、产品 fatal console error 和应用 warning 均为 0。Account、Schema Template、
  GraphSpace 创建 portal 全英文；仅保留 Server 用户 nickname `超级管理员` 与 PD nickname `默认图空间`
  两项外部内容例外。证据见 [PD route matrix](evidence/2026-07-11-pd/chrome-route-matrix.json)。
- [x] **H2-PD-03：Chrome 完成隔离 mutation matrix。** 覆盖与 non-PD 同等关键 mutation 和 PD 特有
  行为，使用独立 fixture 并核验后端真实状态。当前已完成 Graph create/update/default/restore/delete、
  Schema Template create/delete、Datasource metadata create/delete，以及最新包的 Meta Property/Vertex Type
  create/delete + Gremlin vertex create/drop；均有 Server/Hubble API 状态与清理证据。run10b 又完成
  真实 CSV upload → Task/import → HStore REST → Gremlin/Meta/Datasource 清理闭环；任务
  `SUCCEED`、REST 200/404 前后态均已留证，图数据/schema/datasource fixture 全部清理。见
  [PD mutation matrix](evidence/2026-07-11-pd/chrome-mutation-matrix.json)。
- [x] **H2-PD-04：Chrome 完成失败与英文矩阵。** 与 non-PD 使用同一验收标准；最终包已精准重放
  graph API-only、statistics-only、认证、Gremlin、Meta destructive、Schema duplicate、Dashboard 和
  Account failure；每个当前反馈均只有 1 条、raw stack/连接文本=0。18 routes 与动态 portal 产品中文=0，
  Server 用户 nickname `超级管理员` 和 PD nickname `默认图空间` 单列为外部内容例外。证据见
  [PD failure/English matrix](evidence/2026-07-11-pd/chrome-failure-english-matrix.json)。

## 4. 远程 CI、独立审查与发布就绪结论

- [x] **H2-REMOTE-01：提交并推送当前 `hubble2` 分支用于验证。** 2026-07-11，提交
  `bf4e66a15a2feb77ae7bddcf421c9d4b8ddc4bf6` 已推送到 `hugegraph/hubble2`；提交前
  `git diff --cached --check` exit 0，提交包含 non-PD run8 已验证实现与证据；未合并、未发布、未修改
  Server/PD 仓库。
- [ ] **H2-REMOTE-02：最终 diff 的后端 CI 真实全绿。** 这是远程主路径门禁；记录最终 Toolchain
  HEAD、每个后端 run/check URL、结论与关键 artifact。前端 CI 不纳入本项，不因其存量失败阻塞
  non-PD/PD 与产品修复；前端远程检查由最后顺位的 `H2-P2-00` 收口。排队/网络失败使用有限退避和
  checkpoint，不把未运行当通过。
- [ ] **H2-REVIEW-01：独立只读 reviewer 完成最终审查。** reviewer 不得参与实现；输入完整 goal、
  最终 committed range + staged/unstaged/untracked diff、TODO/design/evidence 与测试/CI 证据；记录 reviewer
  身份、findings 和结论。无法创建 reviewer 时保持 goal active 并询问用户。
- [ ] **H2-REVIEW-02：修复或由用户接受所有 actionable findings，并 re-review。** 修复后由独立
  reviewer 复审受影响最终 diff；再执行新鲜最终门禁，记录身份、结果和证据。
- [ ] **H2-DONE-01：发布就绪核对。** 所有可控 TODO 有证据且完成，仅保留已核验的外部缺失能力或
  用户明确接受 blocker；未解决高严重度问题为 0，TODO/design/evidence 三者一致后方可完成 goal。
