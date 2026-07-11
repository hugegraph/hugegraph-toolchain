# Hubble 2.0 hardening run evidence — 2026-07-11

> 本文件记录 2026-07-11 起的新运行事实；当前状态以 [`TODOs.md`](../TODOs.md) 为准。

## 续跑 preflight

- 时间：2026-07-11 CST
- Codex thread：`019f4d43-922e-71c2-9ffe-8e52656d75ef`
- Goal：平台返回同一 Hubble 2.0 hardening objective，状态 `active`；重复创建 goal 被拒绝，证明当前
  goal 身份保持。
- Wakeup：Codex App 暴露面向当前 thread 的 heartbeat automation，可在等待 quota/CI/网络/服务恢复时
  定时唤醒同一线程；不需要虚构恢复命令。
- Checkpoint：以 [`TODOs.md`](../TODOs.md) 保存唯一当前状态，以本日期 evidence 保存命令、环境、日志、
  CI URL 与浏览器证据；同一 goal 恢复后从未完成 checkbox 继续。
- 决策：原生 goal + heartbeat + 仓库 checkpoint 足以满足续跑要求，当前不在 `tools/` 新增辅助脚本。
  若实际 quota reset 无法由 heartbeat 可靠恢复，再先实现并 dry-run 验证目标要求的最小脚本。

## 起始状态

- 仓库：`/Users/imbajin/github/graph/toolchain`
- 分支：`hubble2`，跟踪 `hugegraph/hubble2`
- 起始 HEAD：`ecb08d2bb88e8e7cba3cfeae8b78989970f2e674`
- 改动基线：`fd64a83b29fec9a3ec25b236eac06bf68348c78c`
- 起始工作树：clean
- 已读取 source of truth：TODO、accepted API cleanup design、2026-07-08 read-only audit。

## Checkpoint 模板

后续暂停时追加以下字段，不覆盖既有记录：

```text
时间/原因：
已完成 TODO：
进行中 TODO：
未提交改动（git status）：
已通过/失败验证：
环境与服务状态：
下一步：
恢复/唤醒方式：
```

## 运行日志

### 目标约束更新

- 2026-07-11：用户允许 non-PD 与 PD 环境直接使用最新 Server master。若发现 Server/PD 问题，
  Toolchain 侧不伪实现、不修改外部仓库；在 TODO Server 专项记录后跳过依赖步骤，并继续其他 task。
- 唯一暂停条件收窄为：真实测试环境经有限重试后长期无法建立。

### 前端起始基线

- 时间：2026-07-11 CST
- 环境：Node `v25.6.0`，Yarn `1.22.22`；默认 Java `21.0.2`，Maven `3.9.11`。
- `yarn i18n:check`：exit 0；`zh-CN=1447`、`en-US=1447`、静态 key `866`。
- `yarn test --watchAll=false --runInBand`：exit 1；20 suites 中 16 passed、4 failed，58 tests passed。
  四个 suite 均在加载 `src/i18n/resources/zh-CN/index.js` 导入 `lodash-es` 时出现
  `SyntaxError: Unexpected token 'export'`，属于 Jest ESM transform 边界，尚未修复。
- 结论：记录为 `H2-P1-06`；需先在 CI 固定 Node `18.20.8` 环境复核，再做最小 TDD 修复。
- Java 说明：默认 JDK 21 不能作为 Hubble Java 11 门禁证据，后续切换 Java 11 新鲜运行。

### 前端 Jest 修复

- RED 1（Node `18.20.8`）：20 suites 中 4 failed，`lodash-es` 在 i18n 聚合链无法由 CRA/Jest 转译。
- 最小修复 1：`zh-CN`/`en-US` i18n 聚合改用仓库已依赖的 CommonJS `lodash`。
- 中间结果：19 suites passed；剩余陈旧 `App.test.js` 加载整个路由树时由 Graphin 内部再次引入
  `lodash-es`，且测试仍断言已不存在的 `learn react` 模板内容。
- 最小修复 2：App test 改为验证 `App -> Route -> Layout` 装配，并 mock 该单元之外的 route/layout。
- GREEN（Node `18.20.8`）：`yarn test --watchAll=false --runInBand` exit 0；20 suites、65 tests passed。
  输出仍包含 React Testing Library/React Router 兼容 warning，按 `H2-P2-03` 留待真实 Chrome 栈分级。

### CI 认证根因与第一轮修复

- 远端证据：[run 29107231006](https://github.com/hugegraph/hugegraph-toolchain/actions/runs/29107231006)
  的发布包 Python live smoke 已真实通过 Hubble login/auth-status、Server login、API、Loader flow；随后
  独立 Playwright context 因没有服务端 session cookie，所有业务 API 返回 HTTP 401。
- 根因：UI smoke 只伪造 `sessionStorage.user_`；Python CookieJar 与两个 Node 子进程的 BrowserContext
  均不共享 cookie。`businessStatus === 401` 还被写成可接受条件，属于危险弱门禁意图。
- TDD RED：新增 `ui_auth.test.js` 后因 helper 不存在而失败。
- GREEN：实现同一 BrowserContext 内真实 login + auth/status，并只用服务端返回 user 初始化前端 session；
  HTTP 401、业务非 200、缺 level 均失败。`node --test .../ui_auth.test.js`：4/4 passed。
- 接入：两个 UI smoke 各自真实登录；删除 401 通过语义；密码仅经环境传递，不写入 evidence 命令；
  workflow 删除三个 `CI: false`。JS `node --check`、shell `bash -n`、`git diff --check` 均 exit 0。
- 状态：尚未运行发布包 + 真实 Server 集成，因此 `H2-P0-01` 仍未完成。

### 双模式环境只读盘点

- Server repo：`/Users/imbajin/github/graph/server`，PR #3008 head
  `3bd990d8b58e81cb61e3b85c287d34243836f181`；工作树有既存用户改动/产物，不清理、不覆盖。
- 现有 Server/Hubble 包均早于当前 head，不能作为本轮证据；当前无相关服务端口监听。
- non-PD/RocksDB 与 PD/Store/HStore 脚本和 JDK 11 齐备，无已确认长期 blocker；均须从当前 head
  新构建并解压到 `/private/tmp` 的独立运行目录。顺序为 non-PD 完成停净后，再 PD -> Store ->
  HStore Server -> Hubble。

### `CI=true` production build RED

- 环境：Node `18.20.8`，Yarn `1.22.22`，`CI=true`，`NODE_OPTIONS=--max-old-space-size=4096`。
- 命令：`yarn build`；exit 1。i18n gate 先通过（1447/1447，866 keys），CRA production compile
  因 CI 将 ESLint warnings 作为 errors 而失败。
- 范围：既有 `no-console`、`no-unused-vars`、`react-hooks/exhaustive-deps`、`react/jsx-no-bind`
  分布于 ERView、Topbar、Algorithm/Analysis、AsyncTasks、GraphSpace、Schema/Meta、TaskEdit 等模块。
- 分级：`H2-P0-00`。这些告警此前被 workflow 的 `CI: false` 隐藏；不得恢复该弱门禁或全局禁用规则。
  后续先做无行为变化的未使用代码/console 清理，再对 Hook 与 handler 用针对性测试约束后修改。

### `CI=true` production build GREEN

- 环境：Node `18.20.8`，Yarn `1.22.22`。
- `eslint 'src/**/*.js'`：exit 0，0 warning/error；原 59 files / 183 warnings 已清零，未添加
  `eslint-disable`、未修改规则。
- `yarn test --watchAll=false --runInBand`：exit 0，21 suites / 69 tests passed。
- `yarn i18n:check`：exit 0，zh-CN/en-US 各 1447 keys，866 static keys。
- `CI=true NODE_OPTIONS=--max-old-space-size=4096 yarn build`：exit 0；生成 `build/`，主 bundle
  gzip 2.15 MB。仍有 `@antv/x6-react-components`、`dagre-compound` 缺源文件的 source-map warning、
  Browserslist 数据陈旧和 bundle-size warning，均保留为依赖/性能治理证据。
- 结论：`H2-P0-00`、`H2-LOCAL-02`、`H2-LOCAL-03` 本轮通过；最终 diff 后仍须 fresh rerun。

### Java 11 后端门禁

- 环境：Eclipse Temurin `11.0.22`，Maven `3.9.11`。
- 命令：`mvn test -P unit-test -pl hugegraph-hubble/hubble-be -ntp`；exit 0。
- 结果：103 tests，0 failure、0 error、0 skipped，`BUILD SUCCESS`。

### 本地 Java 联动与发布包

- 环境：Eclipse Temurin `11.0.22`；前端 Node `18.20.8`、Yarn `1.22.22`，`CI=true`。
- Client/Loader：`mvn install -pl hugegraph-client,hugegraph-loader -am -Dmaven.javadoc.skip=true
  -DskipTests -ntp`，exit 0；root、hugegraph-client、hugegraph-loader 全部 `SUCCESS`。
- Hubble：在 `hugegraph-hubble` 执行 `mvn package -DskipTests -ntp`，exit 0；Hubble、hubble-be、
  hubble-dist 全部 `SUCCESS`，耗时 1m57s。
- 真实产物：`hugegraph-hubble/target/apache-hugegraph-hubble-1.8.0.tar.gz`；assembly 内置
  distribution check 通过：392 JAR、275 license files、43 FE license files、10 native-bearing JAR。
- 本地状态隔离：父 reactor 的 editorconfig 会扫描两个既存 ignored 本地项；构建期间通过 trap
  临时移到仓库外，命令结束后 `hubble2-progress-site/`、`db.mv.db`、`db.trace.db` 均已恢复，未纳入提交。
- 说明：本轮为阶段性推送前验证；最终 diff 固定后仍须执行 `H2-LOCAL-06` 新鲜门禁，并补充要求
  sidecar 的独立 release audit JSON。

### 阶段性提交与推送

- 时间：2026-07-11 CST。
- 提交：`225140f2a0302eab5d3c35fadd071c0e65175c09`，`fix(hubble): harden release validation`。
- 推送：`ecb08d2b..225140f2  hubble2 -> hubble2`，远端 `hugegraph/hugegraph-toolchain`；未合并、未发布。
- 推送后立即查询 Actions，尚未出现该 SHA 的 run；已有列表最新仍为上一 SHA `ecb08d2b`。因此
  `H2-REMOTE-02` 保持未完成，后续须通过实际 PR/check 触发面确认全部最终 diff 检查。

### CI 优先级调整

- 2026-07-11 用户明确：远程 CI 的主路径通过标准优先指后端 CI；前端 CI 仍需完成，但因可能存在
  较多存量问题，调整到 non-PD、PD 真实 Chrome 验收、产品修复和最终本地门禁之后执行。
- 该调整只改变顺序和阻塞关系，不弱化前端 CI 真实性：发布就绪收口前仍禁止跳过、忽略退出码、
  弱化断言或伪造响应；人工 Chrome 双模式验收也不由 CI Playwright 替代。
- 2026-07-11 用户进一步要求降低长测试等待：后续采用“针对性 RED/GREEN → 环境批次完整门禁 →
  发布包 Chrome”的三层节奏；完整 build/package 不再随每个微改动重复，等待期间并行执行不冲突 lane。

### non-PD / RocksDB 真实环境

- Toolchain：`cc0c58c072d3700fecfc0b3d4e7c22438d6479f2`；Hubble 发布包 1.8.0。
- Server：Apache `master` `99936be5f41fccd193f120e01206e3cf3c73a050`，版本 1.7.0；从远端 shallow
  clone 到 `/private/tmp/hubble2-hardening/server-99936be5-run2`，未修改用户的 Server 仓库。
- 构建：Temurin `11.0.22`，`mvn package -Dmaven.test.skip=true -ntp`，43 个 reactor 模块全部
  SUCCESS，耗时 2m22s。这里构建用于建立外部测试环境；Hubble 自身后端测试未跳过，已由
  `H2-LOCAL-01` 以 103/103 单测证明。
- 配置：隔离 RocksDB data/wal 位于
  `/private/tmp/hubble2-hardening/nonpd-99936be5-run2/data/{rocksdb,rocksdb-wal}`；Server 仅监听
  `127.0.0.1:8080`，StandardAuthenticator，隔离 admin fixture；Hubble 发布包仅监听
  `127.0.0.1:18088`。Server/Hubble 均以前台持久会话运行，避免 shell daemon 随会话结束退出。
- 首次尝试：daemon Server 随启动命令会话结束退出，Hubble login 得到连接拒绝；JSON/应用日志明确
  记录该失败。改用前台持久会话后有限重试成功，不属于产品通过证据。
- 真实 smoke：`run_live_hubble_smoke.py` exit 0；真实 login/auth-status、Server login、Graphspace、
  Schema、Datasource/CSV Loader、Task SUCCEED、Server 直查 3 vertices/2 edges、shortest path、Cypher
  边界均通过。证据：[live-smoke-run3.json](2026-07-11-nonpd/live-smoke-run3.json)。

### non-PD Chrome 第一轮 route / English 盘点

- 使用用户 Chrome 真实登录 `http://127.0.0.1:18088`，admin 会话建立成功。
- 已到达 Navigation、Graph、GraphDetail、Meta、Datasource、Task、Gremlin、Algorithms、AsyncTasks、
  My Profile；`/super`、`/resource`、`/role` 均重定向 `/navigation`，未出现意外 404 或渲染失败。
- Chrome console 中累计错误均为扩展通信 `Could not establish connection. Receiving end does not
  exist.`，非页面脚本 URL；作为浏览器扩展噪声单列，不计产品致命 console error。
- 英文模式逐页扫描：Navigation、Graph、GraphDetail、Datasource、Task、Gremlin、Algorithms、
  AsyncTasks、My Profile 的可见主区域中文残留为 0；Meta 有 15 组产品可控中文，包括标题、模式、
  tabs、按钮、表头与删除操作，确认为 `H2-P2-02` actionable finding。截图：
  [meta-english-chinese-residual.png](2026-07-11-nonpd/meta-english-chinese-residual.png)。
- 当前仅完成 route 第一轮盘点；mutation、强制失败、修复后英文复验尚未完成，故
  `H2-NONPD-02/03/04` 保持未勾选。

### non-PD Chrome Gremlin 成功与失败

- 英文模式真实选择 `DEFAULT/hugegraph`，在页面输入并执行 `g.V().count()`；Execution Records 状态
  `Success`、耗时 0.018s，Table 结果为 `3`，与 Server 直查 fixture 顶点数一致。
- 强制失败输入 `g.V().thisMethodDoesNotExist()`；页面产生一条 Failed history，但同一底层 Groovy
  方法签名同时出现在结果区和全局 toast，违反单次、可行动且不暴露内部响应的门禁。
- TDD RED：扩展 `request-error-semantics.test.js`，`request`/`request2` 均因仍弹 toast 而失败。
- GREEN：为明确由内联结果拥有的业务错误增加 `suppressBusinessErrorToast` request config；Gremlin/
  Cypher query 启用该配置，并将失败结果替换为本地化、可行动的通用提示。request semantics 8/8、
  联同 manage contract 11/11、scoped ESLint 均通过。当前 i18n 检查因并行 Meta i18n 实现尚未补齐
  其新增 key 而暂时失败；待该实现合并后统一运行，不把中间共享工作树状态误报为本修复失败。
- 仍需重建发布包并用 Chrome 重放同一失败，确认恰好一个可见提示且无底层签名后才能完成
  `H2-P1-05/H2-NONPD-04`。

### non-PD 修复后发布包 Chrome 复验

- 完整前端门禁：24 suites/80 tests、全 JS ESLint、i18n 1523/1523（968 static keys）、`CI=true`
  production build 全部 exit 0；依赖 source-map、Browserslist 与 bundle-size warnings 沿用
  `H2-P2-03` 记录。
- 重建 Hubble 1.8.0 发布包：Hubble/hubble-be/hubble-dist reactor 全 SUCCESS，distribution check
  392 JAR、275 license、43 FE license、10 native-bearing JAR，exit 0。新包以前台持久会话替换旧包，
  复用隔离 H2 fixture 数据库并重新真实登录。
- Meta 英文复验：列表标题、模式、5 个 tab、按钮、表头为英文；Property Create 与 Vertex Type
  Create 弹层中文字符扫描均为 0。截图：
  [meta-english-after-fix.png](2026-07-11-nonpd/meta-english-after-fix.png)。
- Gremlin 同一非法语句复验：可行动 fallback 可见次数=1，底层 `No signature of method` 次数=0，
  `.ant-message-notice` 次数=0；Failed history 仍正确产生。截图：
  [gremlin-single-actionable-error-after-fix.png](2026-07-11-nonpd/gremlin-single-actionable-error-after-fix.png)。
- 路由、英文残留和修复后断言汇总：
  [chrome-route-english-matrix.json](2026-07-11-nonpd/chrome-route-english-matrix.json)。

### GraphDetail 完整断连失败

- 首次真实失败：停止隔离 Server 后打开 GraphDetail，页面主区域为空，statistics fallback 未渲染，
  同时出现 4 条 `Failed to connect to /127.0.0.1:8080` toast；无堆栈但明显违反单次、可行动反馈。
- 根因：graphspace、graph、statistics 三个初始化请求各由全局 interceptor 弹业务错误，前两个失败时
  loading 永不结束；其他布局请求还会叠加 toast。
- TDD RED：新增“所有初始化请求拒绝时渲染一个 page error”的 GraphDetail test，原实现停留在
  loading，找不到错误文案。
- 修复：三个 GraphDetail 请求显式声明 inline error ownership；全局 request/request2 对该 config 的
  business/transport error 均不弹 toast；metadata 请求成功/失败都会结束 loading，失败时使用路由参数
  构造标题并仅显示一个可行动 Alert，statistics 单独失败仍使用较轻 warning。
- GREEN：GraphDetail + request semantics + manage contract 共 15/15，scoped ESLint 0，i18n
  1524/1524、969 static keys。
- 第一轮修复包 Chrome 仍有 Topbar `/auth/status` 产生的 1 条 raw connection toast；CDP Network
  精确确认页面共发出 auth/status + GraphDetail 三个请求。auth/status 只承担 session sentinel，改为静默
  transport/business toast，让当前页面的单一错误 owner 负责反馈；针对性 Topbar/GraphDetail/request
  tests 13/13、scoped ESLint 通过。
- 最终发布包断开 Server 复验：`Graph details are unavailable. Check the server and retry.` 可见 1 次，
  raw `Failed to connect` 0 次，toast 0，主区域正常渲染标题与 Alert。截图：
  [graph-detail-single-error-after-fix.png](2026-07-11-nonpd/graph-detail-single-error-after-fix.png)；
  前后矩阵：[graph-detail-failure-matrix.json](2026-07-11-nonpd/graph-detail-failure-matrix.json)。
- 取证后隔离 Server 已重新以前台持久会话启动。

### non-PD portal 与会话恢复补充 finding

- Server 重启后保留浏览器 Hubble session，但旧 Server token 失效；Graph Management 主区为空，并
  重复出现 2 条 `Authentication failed`。重新登录能恢复页面，但重复 raw feedback 不符合验收，已加入
  `H2-P1-05`，下一批由 Graph 页面单一错误 owner 处理。
- 英文 Graph Management 打开 `Edit` portal 后，标题、Graph ID/Name、placeholder 全为中文。此前
  main-only 中文扫描未覆盖 React portal；已修正验收方法为同时扫描 body/dialog，并将 Graph create/
  edit/schema/clone/delete/default 全部纳入 `H2-P2-02` 批次。

### non-PD Chrome Clear Data 真实 mutation

- 在英文 Graph Management 打开 `Clear Data`：对话框显示 GraphSpace/Graph、`graph data only` 范围、
  不可逆警告，以及“当前没有已验证可保证 schema 保留的 Server 专用操作”；必须准确输入图名后
  `Clear graph` 才可用。
- 对本轮独立 fixture `hugegraph` 输入确认并执行；页面仅出现一次 `Operation succeeded`。
- 使用 Server admin fixture token 直查真实后端状态（curl `--compressed`）：vertices=0、
  propertykeys=0、vertexlabels=0、edgelabels=0。最新 master `99936be5` 仍将 data-only facade 与完整
  `clearGraph` 等价处理，无法保留 schema。
- 结论：`H2-P0-03` 作为已核验外部缺失能力收口。Hubble 当前 FE `api/manage.js` 与 BE
  `GraphsService.java` 的准确调用点均有代码 TODO，注明原因、Server 依赖与解除条件；UI 不虚假承诺。

### non-PD Graph 批次 run7 Chrome 复验

- 源码身份：Toolchain committed HEAD `cc0c58c072d3700fecfc0b3d4e7c22438d6479f2`，叠加本轮未提交
  Graph/Meta/error 批次；Server `99936be5f41fccd193f120e01206e3cf3c73a050`。Hubble 发布包 SHA-256：
  `45d2f41fb54a5de0e5cf83d7bdec5590293021a4d2cc126f29cdf63c8380a79b`。
- 批次门禁：针对性 Jest 5 suites/19 tests；完整 Jest 25 suites/86 tests；全 JS/JSX ESLint；i18n
  1548/1548、999 static keys；`CI=true npm run build` 均 exit 0。直接对含 TypeScript 的整个 `src`
  运行 ESLint 会命中既有 `dataImportUpload.ts` parserServices 配置缺口，未将该工具配置失败伪报为通过。
- 发布包：JDK 21 首次运行因旧 Lombok/JDK module export 不兼容真实失败；固定 Temurin 11.0.22 后执行
  `mvn package -DskipTests -ntp`，三模块 SUCCESS，耗时 1m59s；distribution check 为 392 JAR、275
  license、43 FE license、10 native-bearing JAR。运行目录：
  `/private/tmp/hubble2-hardening/nonpd-99936be5-run2/hubble-runtime-run7/apache-hugegraph-hubble-1.8.0`，
  监听 `127.0.0.1:18088`，复用隔离 H2 fixture。
- Graph 英文与 mutation：真实 Chrome 打开 Edit portal，标题、Graph ID、Display Name、placeholder、
  Cancel/OK 均为英文；将 nickname 从 `hugegraph` 更新为 `hubble_test`，页面显示单次 `Graph updated`，
  随后使用独立真实 Hubble 登录会话 GET graph list，响应 nickname=`hubble_test`；再通过 Chrome 恢复
  为 `hugegraph`。截图：[graph-edit-english-run7.png](2026-07-11-nonpd/graph-edit-english-run7.png)。
- 认证失败：保持 Hubble 浏览器 session，重启隔离 Server 使旧 Server token 失效；刷新 Graph
  Management 后页面不空白，仅出现一次 `Graphs are unavailable. Check the server connection and retry.`
  inline Alert；raw `Authentication failed`=0、toast=0。重新登录后列表恢复。截图：
  [graph-stale-token-single-inline-run7.png](2026-07-11-nonpd/graph-stale-token-single-inline-run7.png)。
- 浏览器为用户 Chrome，英文 locale；本次页面无意外 404、致命 console error 或渲染失败。第一次手工
  输入的 `/graphspace/DEFAULT/graph` 并非产品可达路由，产生的 404 不计 route matrix，之后仅按真实入口
  `/graphspace/DEFAULT` 验收。
- 后续动态页面盘点发现 `/task/detail/1` 在英文 locale 下仍显示 `执行实例 ID`、`导入条数`、`平均速率`、
  `导入时长`、`其它` 与 `条 / s`；已先登记 `H2-P2-02`，再作为 Task 低风险 i18n 批次集中修复。
- Graph menu 的 View Schema、Edit、Delete confirmation 均完成动态 portal 英文盘点，产品可控中文为 0；
  non-PD 菜单没有 create/default/clone 入口，最终 mutation matrix 将其明确标为产品模式不可达。
- Meta Property 真实 mutation：Chrome 创建 `chrome_name` 后显示一次 `Added successfully`，独立 Hubble
  登录会话 GET propertykeys 返回该记录；Chrome 再经英文确认框删除，显示一次 `Deleted successfully`，
  相同 API 返回 `records=[]`。该 fixture 已清理，未影响非隔离数据。

### Gremlin 历史与 TaskDetail 后续实现批次

- Gremlin 同步失败历史新增受控 `GREMLIN_EXECUTION_FAILED` reason code；不持久化 Throwable、Groovy
  signature、堆栈或原始响应。新库 schema 增加 nullable `failure_reason VARCHAR(64)`；旧 H2/MySQL/
  MariaDB 由元数据检测执行幂等迁移。前端仅对已知 FAILED reason 显示一次本地化可行动文案，SUCCESS
  与未知值不展示。异步入口已注明 Server Task DTO 的原因、依赖和解除条件，未伪造外部能力。
- 主线程 GREEN：Temurin 11 targeted Maven `FileMappingSchemaTest,GremlinHistoryFailureTest` 4/4；FE
  ExecuteLog 与 Task i18n 合并 targeted 2 suites/5 tests；scoped ESLint；i18n 1558/1558、1006 static keys；
  `git diff --check` 全部通过。
- Task/TaskDetail 静态 RED 首先准确定位 6 行中文；集中改为 9 个 `task.detail.*` 双语 key，包含所有表头、
  速率和秒单位。此两项改变发布包 UI/数据库，按执行规范等待当前小问题批次冻结后统一重建一次并用
  Chrome 验收，不复用 run7 作为完成证据。

### non-PD destructive failure 补充

- 在 run7 中先用 Chrome 创建隔离 Property `fail_prop`，随后停止隔离 Server，再点击该行 Delete。
  Property 使用检查在确认框之前失败：页面没有可行动 inline 反馈，同时出现 2 条完全相同的原始
  `Failed to connect to /127.0.0.1:8080` toast，违反单次、可行动和不暴露底层连接文本门禁。
- 已按 `H2-P1-05` 先登记，再进入 Meta destructive precheck/operation error-owner 小批次；fixture 将在
  Server 恢复后删除。该失败不作为通过证据，修复后须由下一发布包重放同一 Chrome 场景。

### non-PD run8 统一 Chrome 复验

- 批次完整门禁：Temurin 11 后端 unit profile exit 0；前端 29 suites/100 tests、全 JS/JSX ESLint、
  i18n 1559/1559（1007 static keys）、`CI=true npm run build` 全部 exit 0。发布包 JDK 11 构建三模块
  SUCCESS，distribution check 392 JAR、275 license、43 FE license、10 native-bearing JAR；tarball
  SHA-256 `4878c712989bfc4a78c998099c55ee0d95af45b5a401587f23b347b1fd1dad8a`。
- 运行目录：`/private/tmp/hubble2-hardening/nonpd-99936be5-run2/hubble-runtime-run8/`；复制 run7 的隔离
  H2 fixture 后启动，日志明确出现 `Added execute_history.failure_reason VARCHAR(64)`，证明旧库迁移执行。
- TaskDetail：`/task/detail/1` 表头为 Execution Instance ID、Imported Records、Created At、Average Rate、
  Import Duration、Status、Details，单位为 `records/s` 与 `s`，产品可控中文=0。截图：
  [task-detail-english-run8.png](2026-07-11-nonpd/task-detail-english-run8.png)。
- Gremlin history：Chrome 执行 `g.V().thisMethodDoesNotExist()`，结果区只有一次可行动 fallback；新 FAILED
  history 行同时显示一次 `Query failed. Review the statement and try again.`，SUCCESS 和迁移前旧行没有
  伪造详情，raw Groovy signature/stack=0。截图：
  [gremlin-failure-history-run8.png](2026-07-11-nonpd/gremlin-failure-history-run8.png)。
- IconSelect：Vertex Type Create 的 Vertex Style portal 实际显示英文 `Please select`，图标选择 tooltip
  正常打开，产品可控中文=0。截图：[icon-select-english-run8.png](2026-07-11-nonpd/icon-select-english-run8.png)。
- Meta destructive failure：先创建隔离 `fail_prop`，停止 Server 后点击 Delete；run8 仅显示一次
  `Delete failed. Check the server connection and retry.`，raw connection=0、重复 toast=0，页面仍可用。
  恢复 Server、重新登录后通过 Chrome 删除 fixture。截图：
  [meta-delete-single-error-run8.png](2026-07-11-nonpd/meta-delete-single-error-run8.png)。
- checkpoint 提交并推送：`bf4e66a15a2feb77ae7bddcf421c9d4b8ddc4bf6`
  (`fix(hubble): harden non-pd workflows`)；`git push hugegraph hubble2` 成功，远端从 `cc0c58c0` 前进至
  `bf4e66a1`。远端提示按用户授权 bypass 了“必须经 PR”分支规则，但未创建/合并 PR、未发布。

## PD/HStore 环境建立与英文基线

- 2026-07-11 使用 Server/PD/Store master `99936be5f41fccd193f120e01206e3cf3c73a050`，在
  `/private/tmp/hubble2-hardening/pd-99936be5-prep` 建立隔离 HStore 环境。PD gRPC/REST/Raft 为
  `18686/18620/18610`，Store 为 `18500/18520/18510`，Server REST/Gremlin 为 `18080/18182`，
  Hubble 为 `28088`；完整快照见 [environment.json](2026-07-11-pd/environment.json)。
- 首次 HStore Server 服务发现使用默认 `127.0.0.1:8686`，根因为 `rest-server.properties` 未显式设置
  `pd.peers`；在隔离运行副本补为 `127.0.0.1:18686` 后成功连接并注册 PD。随后一次误用 JDK 21 导致
  Groovy 2.5.14 报 `Unsupported class file major version 65`，改用 Temurin 11.0.29 后启动通过；两次失败
  均保留为真实环境诊断记录，没有修改 Server/PD 仓库。
- 最终运行配置启用 `StandardAuthenticator`、`auth.admin_pa=pa`、`auth.graph_store=hugegraph`；Server
  日志确认 `PDClient connect ... 18686 success`、`Success to register service to pd`、REST started 与
  Gremlin `18182` started。Chrome 使用隔离 admin fixture 真实登录 Hubble 成功，PD 管理导航和
  `/graphspace` 正常渲染。
- 切换英文后，Navigation 导航已英文，但 Server 返回的管理员 nickname `超级管理员` 仍是外部内容；
  `/graphspace` 页面存在大面积产品硬编码中文，Account 与 Schema Template 静态审计确认同类问题。
  已登记为 P1 i18n 批次，修复前截图：
  [graphspace-english-before.png](2026-07-11-pd/screenshots/graphspace-english-before.png)。

### PD 页面 i18n 与错误 owner 批次

- 按 `EXECUTION.md` 的微小问题批处理规范，将 GraphSpace、Schema Template、Account 三组同根因硬编码
  中文合并处理。三条实现 lane 均先增加静态残留/资源对称测试；RED 分别识别 104、22、41 个中文源码
  位置，修复后生产源码 Han=0。PD route guard 同批补充 `/graphspace`、`/account`、
  `/graphspace/:graphspace/schema` 的 PD 可达与 non-PD fallback，共 12 个 route tests 通过。
- 统一定向验证：Account/GraphSpace/Schema i18n、Card、route、manage/auth request contract、error-owner、
  production Router、Dropdown API 与 algorithm DOM props 共 11 suites/50 tests 通过；scoped ESLint、
  `git diff --check` 和 i18n 1581/1581（1105 static keys）
  全部 exit 0。Jest 仅保留 Testing Library 依赖产生的 `ReactDOMTestUtils.act` deprecation warning。
- 交叉检查发现这些页面的 mutation 原先会同时由 request interceptor 和页面提示错误。新增 API config
  forwarding contract RED（11 failures），随后所有页面自有请求传入
  `suppressBusinessErrorToast: true`，业务失败与 transport rejection 统一只显示一次本地化、可行动 fallback，
  不再展示 raw `res.message`；Modal destructive `onOk` 返回真实 Promise。最终仍须在新发布包上用 Chrome
  强制失败复验。

### warning 分级

- 应用可控项已按小批处理：6 处 AntD Dropdown `overlay` 全部迁移至 `menu` API；生产 BrowserRouter 显式
  启用已在 route tests 使用的两项 v7 future flags；51 个算法 leaf 移除向 `Collapse.Panel` 透传已消费的
  业务 props。三项均有 RED→GREEN 静态回归、scoped ESLint 与全源码搜索证据；最终双模式 Chrome 仍须
  确认 fatal console=0、应用逻辑 warning=0。
- 上游依赖/工具链存量：`@antv/x6-react-components` / `dagre-compound` source-map 缺源文件、AntD/rc 的
  `defaultProps`/`findDOMNode`、Testing Library 的旧 `act`、Browserslist 数据陈旧。解除条件分别是升级到
  修复后的上游包并完整回归，禁止以关闭 source map、`CI=false` 或忽略退出码隐藏。
- 性能存量：主 bundle gzip 约 2.15 MB；后续需定义预算并做 route-level lazy loading/chunk split 后以真实
  浏览器 LCP/交互证据收口，不通过调高阈值消除 warning。

## Server PR #3008 外部前置解除

- 2026-07-11 再次执行
  `gh pr view 3008 -R apache/hugegraph --json state,mergedAt,headRefOid,url,statusCheckRollup,reviews`，
  head 仍为 `3bd990d8b58e81cb61e3b85c287d34243836f181`，状态已从早期 OPEN/BLOCKED 变为
  MERGED（`2026-07-10T18:52:38Z`）且存在 APPROVED review。
- dependency、license、Server RocksDB/HBase、PD、Store、HStore、CodeQL 等 15 个 checks 均为
  `COMPLETED/SUCCESS`；外部前置不再阻断 Hubble 双模式验收。本轮只读取 GitHub 状态，未修改
  Server/PD 仓库。

## PD run10 Schema 业务失败与 Dashboard 边界批次

- Chrome 在 PD/HStore run9 发布包（tar SHA-256
  `c46fb14b034b5959fc47363dd919b7c8e6c5f659783d86ead31c7cd8e3d09b86`）创建隔离 Schema Template
  `h2dup20260711`，再次提交同名模板后弹窗和输入保留，但唯一提示为
  `Operation failed. Check the server connection and retry.`。Hubble 日志同时确认真实 Server 原因为
  `Cannot create schema template since it has been created`，因此该提示虽不重复但错误地把业务冲突描述为
  连接故障。新发现先登记 `H2-P1-05`，修复后才进入验证；最后通过 Chrome 删除该 fixture，页面为
  `No data` 且只显示一次 `Deleted successfully`。
- Schema 页面继续作为单一 error owner：已知 Server 同名响应映射为带模板名、要求更换名称的本地化
  文案；其他 create/update 业务失败使用检查输入后重试的提示；仅 transport rejection 使用连接失败
  fallback。失败保持 Modal 与字段，成功才关闭并刷新。同期修复 `request.put/delete` 丢失第三参数配置，
  GraphSpace、Schema、Account 与 Meta destructive 路径的 suppress 配置现在真实到达 axios request。
- Dashboard 稳定契约回填 design：`dashboard.address` 仅为 host/port，协议复用 `server.protocol`；
  `GET /dashboard` 只返回两项配置且不出站。浏览器页面加载只解析配置，用户明确点击运维入口后才执行
  3 秒、无 credential/referrer 的 `no-cors` 探测；成功打开目标，失败只显示一次本地化可行动提示。
  utility 与组件测试证明非法协议/路径/凭据/query 被拒绝，且 render 后 fetch=0、click 后 fetch=1。
- 本批次定向验证：前端 request/manage/auth/Schema/Dashboard 共 49 tests 通过，随后完整 Jest 为
  40 suites/146 tests；全 JS/JSX ESLint exit 0；i18n `1584/1584`、1105 个静态 key，均使用 Node
  `18.20.8`。Java 使用 Temurin `11.0.29` 与 `mvnd 1.0.2`，`DashboardControllerTest` 最终
  1 test/0 failure/error/skip、BUILD SUCCESS；首次 Mockito `verifyNoMoreInteractions` 前遗漏合法读取
  verify 导致 RED，修正测试后 GREEN，没有弱化产品断言。
- 续跑性能核验发现普通一次性命令会话结束时平台同时回收 mvnd daemon，尽管日志已进入 Idle；因此为
  后续 Java 门禁建立持久 shell session `65077`，固定同一 Java 11、mvnd 可执行文件与仓库目录，后续
  package/测试在该会话内连续执行并用 `mvnd --status` 核验真实 daemon 复用。
- 完整批次门禁最终为 Jest 40 suites/146 tests、全 JS/JSX ESLint、i18n 1584/1584（1105 static keys）
  和 `CI=true yarn build` 全部 exit 0。持久 session 内 `mvnd package -DskipTests -ntp` 三模块 SUCCESS，
  1m59s；distribution check 为 392 JAR、275 license、43 FE license、10 native-bearing JAR。tarball
  SHA-256 `df94a648448ec3b65f631c811a4f2ab83bf67de357f0aab3c49ac545f5227e14`，主资源
  `main.2872755f.js`；构建后 daemon `43659201` / PID `91368` 仍为 Idle、Java 11.0.29，证明持久会话
  保留成功。
- 新包解压至 `/private/tmp/hubble2-hardening/pd-hubble-run10b/apache-hugegraph-hubble-1.8.0`，复用已正常
  停机的 run9 隔离 H2 与配置，PID `96798`、端口 `28088`；Server/PD/Store 仍为固定 master
  `99936be5f41fccd193f120e01206e3cf3c73a050`，未修改其仓库。
- Dashboard Chrome：在 `127.0.0.1:8092` 启动隔离 Python HTTP stub。首次加载 `/navigation` 后 stub
  请求为 0，四个 Operations 按钮因配置有效而可点击；点击 Cluster Management 后 stub 记录探测 GET，
  Chrome 打开 `http://127.0.0.1:8092/`。停止 stub 再点击，Hubble 保持页面且精确出现 1 条
  `Dashboard is unavailable. Check dashboard.address and service health.`，raw/stack=0；结果记录于
  [PD failure matrix](2026-07-11-pd/chrome-failure-english-matrix.json)。随后恢复隔离 stub，环境保持可复验。
- Schema Chrome：创建 `h2dup_run10` 后再次提交同名，精确出现 1 条
  `A schema template named h2dup_run10 already exists. Choose another name.`；旧连接误报=0，Create Modal
  和 name/schema 输入均保留；结果记录于
  [PD failure matrix](2026-07-11-pd/chrome-failure-english-matrix.json)。
  取消后经英文确认框删除，最终 `No data` 且 `Deleted successfully`=1，fixture 已清理。
- 最新包重新重放原 PD 18 路由（包含 Meta/GraphDetail 和三条 scoped Gremlin/Algorithms/AsyncTasks）；
  render failure、意外 404、Operation failed 均为 0。测试过程中三条误写成
  `/graphspace/.../{gremlin,algorithms,asyncTasks}` 的非产品路径确实返回 404，立即从矩阵排除并改用路由
  定义中的 `/gremlin|algorithms|asyncTasks/DEFAULT/hugegraph` 重放，未把错误输入伪记为产品失败。
- PD Meta/Gremlin mutation：Chrome 创建 Property `h2_p10_name` 和 CUSTOMIZE_STRING Vertex Type
  `h2_p10_vertex`；Gremlin 创建 `h2_p10_v1` 并成功 count，Server REST 直查返回 HTTP 200、相同 id/label。
  首次组合 `drop(); count()` 被真实拒绝并保留 Failed history，不计为成功；拆为 standalone drop 后 history
  为 Success，Server REST 独立直查 HTTP 404。随后 Chrome 删除 Vertex Type 和 Property，等待异步任务并
  Refresh 后两 tab 均为 No data；结果记录于
  [PD mutation matrix](2026-07-11-pd/chrome-mutation-matrix.json)，fixture 全部清理。

## non-PD run10b Datasource / Task import 闭环

- 运行环境为最新发布包 tar SHA-256
  `df94a648448ec3b65f631c811a4f2ab83bf67de357f0aab3c49ac545f5227e14`，Hubble
  `127.0.0.1:18088`、RocksDB Server `127.0.0.1:8080`，Server master
  `99936be5f41fccd193f120e01206e3cf3c73a050`。Chrome 使用英文界面和隔离账号，不涉及生产或非隔离数据。
- Chrome 上传 `/private/tmp/hubble2-hardening/nonpd-99936be5-run2/chrome-datasource.csv` 创建 Local Upload
  数据源 `h2_file_run10`；创建 Property `h2_import_name` 和 CUSTOMIZE_STRING Vertex Type
  `h2_import_vertex`。随后创建 run-once Task `h2_import_run10`，选择 `col-1` 为 ID、`col-2` 映射到
  `h2_import_name`，执行实例 `33` 最终为 `SUCCEED`，详情显示 Imported Records `2`、耗时 `0.155 s`。
  截图见 [task-import-success-run10.jpg](2026-07-11-nonpd/screenshots/task-import-success-run10.jpg)。
- Toast 不作为成功依据：独立 Server REST
  `GET /graphs/hugegraph/graph/vertices/%221001%22` 返回 HTTP 200，实体为 id `1001`、label
  `h2_import_vertex`、`h2_import_name=chrome-fixture`。随后在 Hubble Gremlin 页面执行
  `g.V('1001').drop().iterate()`，Execution Records 为 `Success`；同一 REST 查询再返回 HTTP 404 和
  `Vertex '1001' does not exist`。
- Chrome 继续删除 `h2_import_vertex`、`h2_import_name` 和 `h2_file_run10`，刷新后 Meta 两 tab 与
  Datasource 均为 `No data`。完成态 run-once Task 的灰色 delete 图标没有产品 click handler，因此只保留
  隔离执行历史，不绕过页面直接删除 H2 metadata；真实图数据、schema、上传 datasource 均已清理。
- 本轮统一矩阵写入
  [chrome-mutation-matrix.json](2026-07-11-nonpd/chrome-mutation-matrix.json)：non-PD Graph
  create/default/delete 按实际 UI 标为 `PRODUCT_UNREACHABLE`，clear 两模式按已验证 Server 合同记录为
  `EXTERNAL_CAPABILITY_LIMIT`，没有以直接 API mutation 冒充 Chrome 操作。

## non-PD run10b 精准 API / statistics failure

- 为避免用整站断连冒充 statistics-only，先在正常 GraphDetail reload 上通过 Chrome CDP Network 识别出
  四个真实请求：`auth/status`、graphspace metadata、graph metadata `*/graphs/hugegraph/get` 和
  `*/statistics`。请求拦截只匹配 XHR，不修改服务端或页面代码，完成后立即清空 Fetch patterns。
- general API 场景只失败 `*/graphs/hugegraph/get`；同一 reload 的 graphspace metadata 与 statistics 均
  HTTP 200。页面完整保留标题，只出现 1 条
  `Graph details are unavailable. Check the server and retry.`，raw stack/Exception=0；恢复正常请求后 Alert
  为 0。截图：[graph-api-single-error-run10.jpg](2026-07-11-nonpd/screenshots/graph-api-single-error-run10.jpg)。
- statistics-only 场景只失败 `*/statistics`；同一 reload 的 graphspace metadata 与 graph metadata 均
  HTTP 200。页面保留 graph metadata 和空统计表，只出现 1 条
  `Statistics are unavailable. Retry later.`，raw stack/Exception=0；清空拦截后正常 reload 显示新的
  Last Updated 且 Alert=0。截图：
  [statistics-only-single-error-run10.jpg](2026-07-11-nonpd/screenshots/statistics-only-single-error-run10.jpg)。
- 结构化汇总见
  [chrome-failure-english-matrix.json](2026-07-11-nonpd/chrome-failure-english-matrix.json)。run7/run8 已有
  stale-session、Meta destructive、Gremlin failure 和英文 portal 证据，但按最终包门禁保留为待重放，
  未提前勾选 `H2-NONPD-04`。

## non-PD run10b 最终失败与英文收口

- 最终包登录使用错误隔离密码时仅显示 1 条 `Failed to login HugeGraph Server`，raw stack=0；使用正常
  `admin/pa` 随即恢复。另分别让 `auth/status` 和已认证 graph API 精确返回 401，均只跳转一次
  `/login?redirect=...`，不叠加 toast，恢复登录后返回原 GraphDetail。
- Gremlin 最终包再次执行 `g.V().thisMethodDoesNotExist()`：当前结果只有 1 个 failure marker，global
  toast=0，raw Groovy signature/stack=0；Execution Records 保留历史失败详情，不把审计历史误计为重复
  prompt。截图：
  [gremlin-failure-final-package-run10.jpg](2026-07-11-nonpd/screenshots/gremlin-failure-final-package-run10.jpg)。
- Meta 创建隔离 Property `fail_prop_run10b`，只失败真实 destructive precheck
  `POST */schema/propertykeys/check_using`；页面精确 1 条
  `Delete failed. Check the server connection and retry.`，raw connection/stack=0。清空拦截后从 Chrome
  正常删除 fixture，Refresh 后 `No data`。截图：
  [meta-delete-single-error-final-package-run10.jpg](2026-07-11-nonpd/screenshots/meta-delete-single-error-final-package-run10.jpg)。
- 最终包顺序重放 17 条 route：Navigation、Graph、GraphDetail、Meta、Datasource、Task、Gremlin、
  Algorithms、AsyncTasks、My、legacy guard 和 scoped routes。意外 404、render failure、产品可控中文均
  为 0。CDP 捕获的 8 条 exception 全部精确为 Chrome extension transport
  `Could not establish connection. Receiving end does not exist.`，无应用 URL/stack；应用 exception=0。
  结构化结果见
  [chrome-route-final-package-run10.json](2026-07-11-nonpd/chrome-route-final-package-run10.json) 和
  [chrome-failure-english-matrix.json](2026-07-11-nonpd/chrome-failure-english-matrix.json)。至此
  `H2-NONPD-04` 勾选。

## PD run10b Datasource / Task import 闭环

- 在同一最新发布包 `df94a648448ec3b65f631c811a4f2ab83bf67de357f0aab3c49ac545f5227e14`
  与隔离 PD/HStore 环境中，Chrome 创建 Property `h2_pd_import_name`、CUSTOMIZE_STRING Vertex Type
  `h2_pd_import_vertex` 和 Local Upload Datasource `h2_pd_file_run10`。上传文件仍为隔离
  `chrome-datasource.csv`，未接触非隔离数据。
- Chrome 创建 run-once Task `h2_pd_import_run10`，将 `col-1` 映射为 ID、`col-2` 映射为
  `h2_pd_import_name`。执行实例 `1` 最终为 `SUCCEED`，详情为 Imported Records `2`、Average Rate
  `2 records/s`、Import Duration `0.159 s`；动态 wizard、mapping、schedule 和 detail 均为英文。截图：
  [task-import-success-run10.jpg](2026-07-11-pd/screenshots/task-import-success-run10.jpg)。
- 独立 Server `127.0.0.1:18080` REST 直查 `%221001%22` 返回 HTTP 200，id `1001`、label
  `h2_pd_import_vertex`、`h2_pd_import_name=chrome-fixture`。Chrome Gremlin 执行
  `g.V('1001').drop().iterate()` 后 history 为 Success；同一 REST 再查 HTTP 404，确认 HStore 状态真实变化。
- Chrome 随后删除 Vertex Type、Property 和 Datasource，三个页面刷新均为 `No data`；完成态 Task 作为
  产品执行历史保留。结构化证据追加至
  [chrome-mutation-matrix.json](2026-07-11-pd/chrome-mutation-matrix.json)，`H2-PD-03` 至此勾选。

## PD run10b 最终失败矩阵收口

- GraphDetail 使用与 non-PD 相同的单 XHR 注入：只失败 graph metadata 时 graphspace/statistics control
  均 HTTP 200，页面 1 条 `Graph details are unavailable. Check the server and retry.`；只失败 statistics
  时 graphspace/graph metadata 均 HTTP 200，页面 1 条 `Statistics are unavailable. Retry later.`。两项
  raw stack=0，清空 Fetch patterns 后正常恢复。截图分别为
  [graph-api-single-error-run10.jpg](2026-07-11-pd/screenshots/graph-api-single-error-run10.jpg) 和
  [statistics-only-single-error-run10.jpg](2026-07-11-pd/screenshots/statistics-only-single-error-run10.jpg)。
- 精确让 `auth/status` 返回 401 后只跳转一次保留 redirect 的 login route；提交错误隔离密码精确显示
  1 条 `Authentication failed`，raw stack=0，正常 `admin/pa` 后恢复原 GraphDetail。截图：
  [login-single-error-run10.jpg](2026-07-11-pd/screenshots/login-single-error-run10.jpg)。
- Gremlin 非法语句的当前结果 marker=1、global toast=0、raw Groovy signature/stack=0；截图：
  [gremlin-failure-run10.jpg](2026-07-11-pd/screenshots/gremlin-failure-run10.jpg)。
- Meta 创建 `h2_pd_fail_run10`，仅失败 destructive precheck `POST */check_using`，页面精确 1 条
  `Delete failed. Check the server connection and retry.`，raw connection/stack=0。恢复后 Chrome 删除
  fixture，Property tab 回到 `No data`；截图：
  [meta-delete-single-error-run10.jpg](2026-07-11-pd/screenshots/meta-delete-single-error-run10.jpg)。
- 结合 Account 整体断连、Dashboard、Schema duplicate 与既有 18-route/portal 英文扫描，结构化矩阵
  [chrome-failure-english-matrix.json](2026-07-11-pd/chrome-failure-english-matrix.json) 已覆盖与 non-PD
  同等标准；产品可控中文=0，`超级管理员`/`默认图空间` 明确为 Server/PD 外部 nickname。`H2-PD-04`
  至此勾选。

## 最终提交前状态码与 Dashboard 窗口收口

- 提交前只读审计发现 `RestClient.checkStatus()` 的 HTTP 200 兼容逻辑未限定 request method，会让非
  DELETE 合同意外接受 200。已收窄为仅 DELETE request 可接受 Server 的 200 兼容响应；新增 DELETE
  正例与 POST 负例，`mvnd test -pl hugegraph-client
  -Dtest=RestClientStatusTest,GraphsAPITest -ntp` 为 7 tests、0 failure/error，BUILD SUCCESS、5.544s。
- 同一审计指出 Dashboard 在异步 probe 后才调用 `window.open()`，慢响应可能丢失浏览器 user
  activation。已改为点击同步创建 `about:blank`，主动断开 `opener`，probe 成功后再 `location.replace()`；
  不可达时关闭临时窗口，popup 被拦截时不探测并给出准确提示。Dashboard 定向 Jest 2 suites/14 tests、
  scoped ESLint、i18n 1585/1585（1106 static keys）均 exit 0。
- Java 11 持久 `mvnd` daemon `43659201` / PID `91368` 重建最终 Hubble package，BUILD SUCCESS、1m47s；
  distribution check 仍为 392 JAR、275 license、43 FE license、10 native-bearing JAR。最终 tar SHA-256
  `7f735a2c6a20223a89534ba1fe8c06006fbdf9f3c8dd521daf3d25f5f5653368`，FE asset
  `main.e159790d.js`。
- 从该 tar 解压的隔离 PD Hubble `127.0.0.1:38089` 连接原隔离 PD/Store/Server master
  `99936be5f41fccd193f120e01206e3cf3c73a050`，真实 Chrome 英文界面验证三条路径：可达时新标签跳转
  `8092`；不可达时临时标签关闭且只有一条 actionable message；tab-scoped CDP 临时令 `window.open`
  返回 null 时 Dashboard probe=0，只有一条 popup-blocked message，随后立即恢复原始 `open/fetch`。
  证据见 [Dashboard matrix](2026-07-11-final-dashboard/chrome-dashboard-window-matrix.json)。
