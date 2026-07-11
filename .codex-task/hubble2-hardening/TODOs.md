# Hubble2 TODOs

> 唯一当前状态源。这里只维护当前任务状态；稳定决策写入 design，新运行事实写入按日期新增的
> `evidence/` 文件，历史审计不得覆盖。完成项必须回填日期、环境、版本/SHA、命令或 CI run、证据链接。
> 执行分层、并行 lane、等待/重试和 Chrome 规则见 [`EXECUTION.md`](EXECUTION.md)；本文件不承载执行规范。

- 最近核验：2026-07-11
- 当前分支/最近已验证并推送实现提交：`hubble2` /
  `03285ac1081185b3ca239592187115dd97dace0b`。
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
- [x] **H2-GATE-02：每次暂停前保存 checkpoint。** 2026-07-11，本轮在 TODO 与日期 evidence 中持续记录
  已完成/进行中项、未提交改动、验证结果和下一步；CI 采用有限轮询与一次失败 job 重试，服务/Chrome
  断连均恢复同一 goal，未使用前台长 sleep、busy loop 或伪造恢复命令。
  下一步；quota reset 还须记录错误中的 reset time、时区和 Usage 核验。网络、CI、服务、Chrome
  采用有限重试和退避，不使用前台长 sleep 或 busy loop。
- [x] **H2-GATE-03：守住授权边界。** 2026-07-11，仅操作 Hubble、必要 Loader/Client、构建/CI 和隔离 fixture；
  不接触生产、个人或非隔离数据，不修改 Server/PD 仓库，不合并或发布。Server 单机和分布式环境
  可直接使用最新 master；若发现 Server/PD 问题，写入本文件的 Server 专项并跳过依赖该能力的步骤，
  继续其他可执行任务。
- [x] **H2-GATE-04：新发现先分级再修复。** 2026-07-11，全部新发现均先按 P0/P1/P2 写入本文件；
  reviewer 的 Role 深链问题登记为 H2-P1-10 后修复并复审通过。外部缺失能力已在准确
  Hubble 调用点写明原因、依赖和解除条件的代码 TODO，并在这里同步 blocker 状态。
- [x] **H2-GATE-05：证据可复现且不伪造。** 2026-07-11，每个完成项已回填日期、环境、Toolchain/Server/PD
  版本或 SHA、配置、命令/退出码、日志/截图/JSON、CI run URL；mock 仅作单测，不作 E2E 证据。

## P0

- [x] **H2-P0-05：修复最终 PR license-header CI。** 2026-07-11，PR #4 / head `a9231067` 的
  `license checker / check-license-header` run
  [29145773126](https://github.com/hugegraph/hugegraph-toolchain/actions/runs/29145773126) 真实失败；日志列出
  22 个分支新增/修改 Java/JS 文件只有截断 header 或完全缺失 header。批量补齐仓库标准 ASF header，
  不改业务逻辑；head `03285ac1` 的同一 check 已在
  [29145996488](https://github.com/hugegraph/hugegraph-toolchain/actions/runs/29145996488) SUCCESS。
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

- [x] **H2-P1-10：关闭孤立 PD RoleAuth legacy 深链。** 最终独立 reviewer
  `/root/final_independent_reviewer` 发现 `/role/graphspace/:graphspace/:role` 在 PD 模式仍可直接访问，页面
  含产品可控中文且未进入最终 route matrix；`/role` 与导航入口已停用，因此将 scoped 深链同样 replace
  redirect 到 `/navigation`。2026-07-11，targeted 13/13、完整 Jest 40 suites/148 tests、scoped ESLint、
  i18n、`CI=true` build 与 package audit 全部通过；最终包 `687f7936...aa7f` 在真实 PD Chrome 从深链
  replace 到 `/navigation`，RoleAuth 未渲染、HTTP error/意外 404/渲染失败/产品中文均为 0。证据见
  [最终 Role redirect](evidence/2026-07-11-final-role-redirect/chrome-role-deep-link.json)，待同一 reviewer re-review。
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

- [x] **H2-P2-04：核验 Loader Kafka CI 单次波动。** 2026-07-11，最终 head `03285ac1` 的 Loader
  run [29145996501](https://github.com/hugegraph/hugegraph-toolchain/actions/runs/29145996501) 中
  unit/file/hdfs/jdbc 已通过，Kafka `testNumberToStringInKafkaSource` 单次期望 7、实际 5；相邻代码 head
  `a9231067` 的 PR run `29145773130` 已全绿，最终提交仅改变 license 注释。允许同 run 有限 rerun failed
  一次；attempt 2 全 workflow SUCCESS，未再次出现，无需弱化测试或修改 Loader。证据见
  [最终 CI 记录](evidence/2026-07-11-final-ci-and-review.md#final-pr-checks)。
- [x] **H2-P2-00：最后处理前端 CI 浏览器 smoke 与遗留问题。** 2026-07-11，最终 head
  `03285ac1` 的 Hubble CI run
  [29145996476](https://github.com/hugegraph/hugegraph-toolchain/actions/runs/29145996476) SUCCESS；真实执行
  i18n、production compile/package、release audit、111 个 BE tests 和发布包 + Server 的认证/API/Loader/
  browser route/i18n acceptance，未使用 `CI=false`、跳过真实测试、忽略退出码或弱化断言。
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
  复验。孤立 legacy `/role/graphspace/:graphspace/:role` 已按稳定产品决策 replace redirect 到 Navigation，
  最终 non-PD 17 routes、PD 原 18 routes + 该深链及 Meta/GraphSpace/Schema/Account/
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
- [x] **H2-LOCAL-06：最终工作树门禁新鲜重跑。** 2026-07-11，最终实现冻结后：Hubble BE
  111/111、Client UnitTestSuite 50/50、完整 FE Jest 40 suites/148 tests、scoped ESLint、i18n 1585/1585、
  compile/production build、Client+Loader install、Hubble package/distribution audit 和最终包 issue-694
  真实 acceptance 全部 exit 0。reviewer 修复后重新打包的 tar SHA-256
  `687f7936f0bc3d4d66bdad99ea762efb82e398f36ed68597e18fae82bc20aa7f`，FE
  `main.4ccb11e5.js`；最终 Role 深链受影响范围另以真实 PD Chrome 验证通过。
  证据见[最终 CI/本地记录](evidence/2026-07-11-final-ci-and-review.md#fresh-local-gates)。
  审计跟进及 reviewer 修复后再次以 Java 11 `mvnd` 新鲜运行 Hubble BE 119/119、0 failure/error/skip；
  最终真实 Hubble/hubble-be/hubble-dist package BUILD SUCCESS（1m56s），distribution audit 为 392 JAR、275
  license、43 FE license、10 native-bearing，tar SHA-256
  `77a3c7aacc43d4eef7afc96dc3397571d90f8bff0146e82138f47c40b371ff77`、FE
  `main.7d134651.js`。证据见[审计跟进](evidence/2026-07-11-audit-followup.md#final-error-contract-package)。

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
- [x] **H2-PD-02：Chrome 完成全部可达导航 route matrix。** 2026-07-11，完整矩阵发布包 SHA-256
  `df94a648448ec3b65f631c811a4f2ab83bf67de357f0aab3c49ac545f5227e14`，Chrome 重放 18 条可达/legacy
  路由；意外 404、渲染失败、产品 fatal console error 和应用 warning 均为 0。Account、Schema Template、
  GraphSpace 创建 portal 全英文；仅保留 Server 用户 nickname `超级管理员` 与 PD nickname `默认图空间`
  两项外部内容例外。证据见 [PD route matrix](evidence/2026-07-11-pd/chrome-route-matrix.json)。
  最终 reviewer 补充发现的 RoleAuth 深链已由最终包 `687f7936...aa7f` 在真实 PD Chrome 验证 replace
  redirect，无意外 404/渲染失败/产品中文；证据见
  [final role redirect](evidence/2026-07-11-final-role-redirect/chrome-role-deep-link.json)。
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
  `a9231067d8446e35e4a865b2dc3e72ed49603af6`（实现）与
  `03285ac1081185b3ca239592187115dd97dace0b`（license header）均已推送到 `hugegraph/hubble2`；提交前
  `git diff --cached --check` exit 0。未合并、未发布、未修改 Server/PD 仓库。
- [x] **H2-REMOTE-02：最终 diff 的后端 CI 真实全绿。** 2026-07-11，PR #4 精确 head
  `03285ac1081185b3ca239592187115dd97dace0b` 的 Hubble、Java Client、Loader、Tools、Go Client、Spark、
  license header/dependency 和 labeler checks 全部 SUCCESS；GitHub 汇总 10 successful、0 failing、
  0 pending。Loader 仅 attempt 1 出现一次 Kafka 计数波动，attempt 2 同 head 全绿。所有 run URL、event、
  attempt 与无可下载 artifact 的事实见[最终 CI 记录](evidence/2026-07-11-final-ci-and-review.md#final-pr-checks)。
- [x] **H2-REVIEW-01：独立只读 reviewer 完成最终审查。** 2026-07-11，独立只读 reviewer
  `/root/final_independent_reviewer` 未参与实现；输入完整 goal、
  最终 committed range + staged/unstaged/untracked diff、TODO/design/evidence 与测试/CI 证据；记录 reviewer
  身份、findings 和结论。无法创建 reviewer 时保持 goal active 并询问用户。
  本轮新增 diff 另由未参与实现的独立只读 reviewer `/root/final_followup_reviewer` 审查 base
  `1b78c9d2` 到完整 working tree、ignored evidence 与原图；身份和结论见
  [审计跟进](evidence/2026-07-11-audit-followup.md#independent-review-follow-up)。
- [x] **H2-REVIEW-02：修复或由用户接受所有 actionable findings，并 re-review。** 2026-07-11，唯一
  Important finding H2-P1-10 已修复；同一 reviewer 定向复审受影响最终 diff，确认 resolved，且
  Critical/Important/Minor 均为 0、无新 actionable finding。测试、发布包与真实 Chrome 证据见
  [最终 CI/审查记录](evidence/2026-07-11-final-ci-and-review.md#independent-review)。
  follow-up reviewer 发现 1 个 CSV 临时文件 Important，修复后同一 reviewer re-review PASS，确认 finding
  resolved、无新 actionable；targeted 4/4、完整 BE 119/119 与 post-review 发布包均通过，证据见
  [审计跟进](evidence/2026-07-11-audit-followup.md#independent-review-follow-up)。

## 5. 本轮审计新发现问题与测试核验（2026-07-11）

- [x] **H2-AUDIT-01（P1 调查）：非 PD 个人信息契约核验。** 2026-07-11，已确认
  `GET /auth/users/{id}` 是 ID
  endpoint，使用 username `admin` 复现的 400 不能证明 `/my` 个人信息失效；non-PD 正常页面使用
  `/auth/users/getpersonal`。须用真实发布包 Chrome 核验读取/编辑/刷新、错误反馈及 `-27:admin` 是否可见；
  只有真实个人流程失败才升级 P0。真实发布包 Chrome `/my` 已完成读取、nickname 修改/保存/恢复；页面
  始终显示 `admin`，未暴露 `-27:admin`，英文残留和可见 alert 均为 0，因此未复现 P0，剩余仅为 P2
  ID/username 契约说明。证据见[审计跟进](evidence/2026-07-11-audit-followup.md#non-pd-my-profile-contract)。
- [x] **H2-AUDIT-02（P2 退役）：清理未使用的 `/graphs/{graph}/storage` 空壳。** 2026-07-11，前端仅定义并导出
  `getGraphStorage`、全仓无调用，后端对应端点也是注释死代码；两端残留已删除，design 已记录退役且未补
  伪接口。GraphDetail 2/2 与 scoped ESLint 通过，证据见[审计跟进](evidence/2026-07-11-audit-followup.md#audit-cleanup-batch)。
- [x] **H2-AUDIT-03（P1 错误规范批次）：规范 `/execute-histories` 缺参错误。** 2026-07-11，`type` 是必填参数，
  HTTP 400 合理；缺失参数已由统一 advisor 返回 HTTP/business 400、中英文可行动消息且 cause=null。
  TDD 与最终发布包真实认证 REST 均通过，证据见[审计跟进](evidence/2026-07-11-audit-followup.md#final-follow-up-package)。
- [x] **H2-AUDIT-04/05（P2 外部兼容）：PD/HStore status 下游能力缺失。** 当前 FE 不消费
  `/pds/status`、`/services/storage/status`，监控入口外跳 Dashboard；Server 18080 不提供所需 `/pd` 与
  `/hstore/status`。在准确 Hubble 调用点记录原因、依赖和解除条件，不伪实现；Hubble 可控响应不得再用
  HTTP 200 包装业务 400/下游 404。2026-07-11，准确调用点 TODO 保留外部依赖与解除条件；Hubble
  局部映射为 HTTP/business 503、中英文可行动消息、`cause=null`。同一最终发布包已在 non-PD 38088
  与 PD/HStore 38089 真实认证 REST 验证，证据见
  [审计跟进](evidence/2026-07-11-audit-followup.md#final-error-contract-package)。
- [x] **H2-AUDIT-06（P2 退役）：明确 Audit 未发布边界。** 2026-07-11，当前无页面、路由、导航和 FE API，且依赖
  默认未配置的 Elasticsearch；整段注释 controller 已删除，稳定 design 明确退役，未恢复未经验证能力；
  Hubble BE 112/112 通过。证据见[审计跟进](evidence/2026-07-11-audit-followup.md#audit-cleanup-batch)。
- [x] **H2-AUDIT-07（P2 契约）：记录 non-PD `/graphspaces` 降级语义。** 2026-07-11，`/graphspaces/list` 返回
  `["DEFAULT"]` 供业务导航，而管理分页为空表达“无可管理 GraphSpace”；统一 API 文档/契约，避免合成
  record 误启用管理 mutation；稳定 design 已记录该能力区别，既有 non-PD Chrome route 证据证明管理
  页面受 guard 保护。
- [x] **H2-AUDIT-08（P1 错误规范批次）：修正 Vermeer 可选集成失败反馈。** 2026-07-11，当前失败会降级而非
  清空普通图列表；核验并修正 Graph Analysis 错误提示条件、中文硬编码日志及原始连接异常，保证最多一次
  清晰提示且普通 graph 列表保持可用。最终发布包 `/vermeer` 返回 `enable=false`，普通 graph 列表仍显示
  `hugegraph`；前端已有全局 message + 空消息 fallback 的单 owner 防重复语义，后端中文/原始 HTML 日志已
  改为英文结构化摘要。证据见[审计跟进](evidence/2026-07-11-audit-followup.md#final-follow-up-package)。
- [x] **H2-AUDIT-11（P1 资源安全）：关闭失败的 Dashboard/Vermeer 探测连接。** 2026-07-11，新发布包
  真实运行发现 `VermeerService` 仅在成功分支关闭 `RestClient`，失败时泄漏连接并把下游整段 HTML 写入
  日志；现已在 finally 关闭 client，日志只保留结构化可行动摘要。最终包真实失败探测已验证无原始 HTML。
- [x] **H2-AUDIT-09（P2 契约）：统一 graph nickname 长度约束。** 2026-07-11，12 字符限制针对 nickname 而非
  graph name；Server master `3bd990d8` 的真实上限是 48，前端校验和中英文文案已对齐。最终发布包 Chrome
  成功保存超过旧上限的 `nickname_long1` 并恢复 `hugegraph`，可见 alert=0；证据见
  [审计跟进](evidence/2026-07-11-audit-followup.md#final-follow-up-package)。
- [x] **H2-AUDIT-10（P1 异常安全）：移除可达路径的原始堆栈输出。** 2026-07-11，图列表 schema 获取失败时
  `GraphsService` 直接 `printStackTrace()`；并审计 Vermeer 等可达分支的同类调用。改为结构化日志和统一
  业务反馈，浏览器不得出现原始堆栈或重复提示。TDD RED 精确检出 8 处，全部改为结构化日志并由静态
  回归锁定；Hubble BE 112/112 通过，主源码 `printStackTrace()` 为 0。证据见
  [审计跟进](evidence/2026-07-11-audit-followup.md#targeted-redgreen)。
- [x] **H2-AUDIT-12（P1 错误信息安全）：统一客户端异常响应的 cause 契约。** 核验通用 advisor 是否把
  `Throwable` 内部结构序列化给客户端；若可见，响应统一 `cause=null`，详细异常仅保留在结构化日志，
  并用针对性测试和真实 REST 证明不暴露堆栈或不可读结构。2026-07-11，TDD RED 证明 cause 暴露，修复后
  advisor 及直接 Response 路径均返回 `cause=null`；真实双模式 REST 复验通过，证据见
  [审计跟进](evidence/2026-07-11-audit-followup.md#error-contract-follow-up-batch)。
- [x] **H2-AUDIT-13（P1 mutation 真实性）：修复删除超级管理员权限的假成功。** 当前公开 endpoint 捕获
  异常后仅写日志并返回成功；失败必须传播为一次明确错误，或在确认无消费者后正式退役 endpoint，且补
  权限 mutation 回归测试。2026-07-11，已移除 catch-and-success；模拟 Server mutation 失败的 RED 后，
  失败正确传播且 targeted batch 24/24、完整 BE 117/117 通过，证据见
  [审计跟进](evidence/2026-07-11-audit-followup.md#error-contract-follow-up-batch)。
- [x] **H2-AUDIT-14（P2 调查）：核验 K8s token 与用户 CSV 导入错误边界。** 先确认两者是否属于当前可达
  发布表面；可达时修复产品可控中文、空路径/NPE 或部分导入假成功，不可达时记录准确退役/外部边界。
  2026-07-11，全 FE 搜索确认两者仅有未调用 facade、无页面消费者；公开后端边界仍完成防御性修复：K8s
  中文/NPE 改为 i18n 异常，CSV 临时文件/解析失败不再返回 null 或继续 mutation，临时文件 finally 删除。
  TDD RED/GREEN、reviewer finding 的三条边界回归与完整 BE 119/119 通过，证据见
  [审计跟进](evidence/2026-07-11-audit-followup.md#error-contract-follow-up-batch)。

- [ ] **H2-DONE-01：发布就绪核对。** 所有可控 TODO 有证据且完成，仅保留已核验的外部缺失能力或
  用户明确接受 blocker；未解决高严重度问题为 0，TODO/design/evidence 三者一致后方可完成 goal。
