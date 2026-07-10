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
