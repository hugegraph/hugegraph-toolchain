# 2026-07-11 Fresh Audit

## 审计边界

- 日期/时区：2026-07-11，Asia/Shanghai
- 分支/HEAD：`hubble2` / `b66848d308d58e6899cab7eb92589f1789b62259`
- 工作树基线：26 个已修改 Hubble BE Java 文件，`150 insertions / 57 deletions`；均为 wildcard import 展开、未使用 import 删除、空格/换行等 code-style 修改，fresh audit 前已存在，本轮保留并按“既有未提交质量修复”管理。
- 审计前 source-of-truth：25 个 TODO；状态为“初始化，未执行”。
- 历史目录：`.codex-task/hubble2-hardening/` 只用于定位命令和已知风险，未把其中结果当成当前证据。

## 工具链

| 工具 | 当前值 | 结论 |
|---|---:|---|
| 默认 Java | 21.0.2 | 与 CI Java 11 不同，不用于最终 Java 门禁 |
| `j11` | Temurin 11.0.22 | 可用 |
| Maven | 3.9.11 | Java 11 原生 Maven 审计入口 |
| mvnd | shell 直接调用为 1.0.2；`zsh -lc` 解析为 2.0.0-rc-3 | 2.0 agent class version 61，不能在 Java 11 启动；计划中的 Java 11 daemon 复用假设失效，暂用原生 Maven |
| Node | 25.6.0 | 与 CI 18.20.8 不同；现有 FE 结果仅为本地审计，不代替 CI |
| Yarn | 1.22.22 | CI workflow 固定 Node 18.20.8，但未固定系统 Yarn 与 dist plugin 的 1.22.21 差异 |

## Fresh gate 输出

### Frontend

- `CI=true yarn build`：exit 0，wall `51.49s`；i18n `1587/1587`、静态 key `1108` 通过。
- build 不是无 warning：`@antv/x6-react-components`、`dagre-compound` 和 antd source map 缺失/不支持；Browserslist 数据 6 个月未更新；主 bundle gzip `2.14 MB`。
- `yarn test --watchAll=false --runInBand`：exit 0，wall `6.07s`；40 suites / 148 tests 全过。
- Jest 仍输出 ReactDOM test-utils `act` 弃用、React Router v7 future flags、第三方 `defaultProps` warning。先区分本项目测试可修复项与第三方 warning，禁止用静默/忽略换绿。
- 当前仓库没有独立 `lint` script；CRA production build 是 JS/JSX lint 真值入口，TypeScript 需单独盘点 lint/typecheck 可执行面。

### Backend

- `zsh -lc 'j11; mvnd ...'`：exit 1；mvnd 2.0.0-rc-3 agent 以 Java 17 编译，Java 11 仅支持 class version 55。
- 按计划回退 `zsh -lc 'j11; mvn -e compile ...'`：exit 1，`6.932s`；依赖解析失败，尚未进入 javac。
- `mvn test -P unit-test -pl hugegraph-hubble/hubble-be`：exit 1，`4.57s`；同一依赖解析失败，未执行测试。
- `mvn checkstyle:check -pl hugegraph-hubble/hubble-be -am`：exit 0，但插件输出大量具体 style 提示后仍报告 `0 violations`，说明当前配置不是可靠失败门禁。Hubble BE 明确包含 line length、indentation、operator wrap、empty-line、whitespace 等问题；既有 dirty diff 尚未覆盖全部问题。
- Maven effective-model/依赖 warning 还包括旧 `compilerArguments`、blocked HTTP metadata、过期证书 metadata 和 HugeGraph parent model 读取失败，需按“仓库可修 / 外部依赖”分类。

## server-core 兼容故障

- 当前稳定症状：`hugegraph-core:1.7.0` 传递依赖 `hugegraph-struct:1.7.0` 的 artifact descriptor 无法读取。
- 精确根因：本地解析到的官方 `hugegraph-struct-1.7.0.pom` 父坐标版本仍为 `${revision}`，Maven 继而请求 `org.apache.hugegraph:hugegraph:pom:${revision}`；腾讯镜像返回 HTTP 400。
- 当前层级：发布 POM/依赖元数据兼容问题，不是 Hubble Java 源码编译错误。
- 旧 GitHub run `26214055519` 只有 run 元数据，log 已不可获取，不能作为当前根因证据；当前 HEAD 尚无真实 Hubble CI run。
- 修复边界：不得直接升级/降级依赖版本；先验证能否通过 Hubble 范围内的显式依赖/排除或正确发布坐标恢复，若涉及版本变更则登记用户决策。

## CI 基线

- 当前 `.github/workflows/hubble-ci.yml` 已增加 Node 18、Yarn/Playwright cache、FE i18n、release package audit、BE unit test 与 issue-694 API acceptance。
- 最近可读取的成功 run `28464206368` 总时长约 17m15s，但对应其他分支/旧 workflow：Compile `7m07s`、Prepare env/service `8m15s`、Unit `24s`、API `66s`；只能用作历史对照，不能作为当前 HEAD 基线。
- 当前 workflow 仍在 Compile 先 install Client/Loader，Prepare 阶段再次 `mvn package`；是否存在可消除的重复构建必须用当前 HEAD run 的 step 数据验证后再改。

## 核心 API 测试覆盖初表

| 核心路径 | 当前成功合同 | 当前失败/权限/边界合同 | fresh audit 结论 |
|---|---|---|---|
| 认证/会话/拦截器 | `AuthSecurityTest` 覆盖 authenticated client 与 token/space/graph 传递 | 401、未登录、PD capability unavailable | 覆盖较强，需以真实执行确认 |
| Graph/GraphSpace | `GraphServiceImportTest` 覆盖导入成功，`Graphs*Test` 覆盖默认图 | 缺字段、错误数组、重复 ID、边不匹配 | service 边界较强；高频 controller 写路径仍需盘点 |
| Schema | controller security/route tests | 权限与路由注册 | CRUD 成功合同覆盖偏弱 |
| Gremlin/Query | `QueryServiceTest`、history failure、client test | 外部异常、history failure | 有 service failure；核心 controller 成功/非法请求需盘点 |
| Datasource/Loader/Task | upload、load task、job manager success/failure | 空值、重复/恢复、失败状态 | 局部较强，需确认真实 suite 是否收录 |

## 审计后计划变化

- 不再假定 Java 11 + mvnd 可用；增加 mvnd launcher/version 一致性诊断，当前统一回退 Maven 3.9.11。
- 将“FE lint”拆为源码 lint、测试 warning、第三方 build warning；第三方 warning 不通过修改依赖来擅自处理。
- 将 server-core 作为 Phase 1 前置阻塞：依赖未解析前无法获得有效 BE compile/test/耗时基线。
- 将 checkstyle “exit 0 但报告问题”登记为 CI 可诊断性缺口；是否强化为失败门禁属于门禁语义变化，先仅修明确问题并提交独立决策。
- CI 性能基线必须通过当前 HEAD 的真实 run 重采，不复用旧 run 数字。

## Phase 1 实施与验证

- 最小修复：在 Hubble 的 `hugegraph-core:1.7.0` 依赖上排除未被 Hubble 源码使用的 `hg-store-common`，从而避免其破损的 `hugegraph-struct:1.7.0` 发布 POM；依赖版本保持不变。
- `mvn dependency:tree -pl hugegraph-hubble/hubble-be -Dincludes=org.apache.hugegraph` 显示 server-core 相关直接依赖仅为 `hg-pd-client:1.7.0` 与 `hugegraph-core:1.7.0`，不再包含 `hg-store-common/hugegraph-struct`。
- 首次进入 javac 后暴露既有 import 清理造成的 16 个错误；修复错误包的 `Autowired`、漏掉的 JDK/实体/Elasticsearch imports，并将 `SingleSourceShortestPathEntity` 指向 Hubble 自身 request entity，未改变接口或控制流。
- BE compile：exit 0，265 source files；cold `11.17s`，并行采样的后续 run `18.28s` 受同时运行 unit test 干扰，不能作为有效 warm 对比，需串行重采。
- BE unit test：exit 0，119 tests / 0 failures / 0 errors / 0 skipped，wall `22.58s`。
- 新发现：JaCoCo 报告提示 execution data 与 `AppName` class 不匹配；需清理旧 `jacoco.exec` 后新鲜复验，不能把当前 coverage report 当作最终证据。
- code-style 精确盘点显示 Hubble BE 仍有约百项 checkstyle 提示，插件仍 exit 0；按文件/根因分批修复并在每批后 compile/test。
