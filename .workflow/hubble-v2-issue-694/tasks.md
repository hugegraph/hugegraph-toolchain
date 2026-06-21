# 实现计划: Hubble V2 issue 694 验证与修复

- [x] 1. **研究与准备** `[优先级: 高]`

  - [x] 1.1. 在现有代码库中确认 Hubble BE controller/service、FE i18n、dist 打包、
    启动脚本、Dockerfile 和测试 profile 的实现入口。 `(关联需求: U9, U11)`
  - [x] 1.2. 对照飞书父文档和 5 个子文档，确认当前 issue #694 的 gate 覆盖
    build、runtime、frontend/backend、GraphServer/Loader、i18n、binary 和 API
    boundary。 `(关联需求: U10, X8)` `(依赖于: 1.1)`
  - [x] 1.3. 记录当前分支、起始 commit、工作区变更和候选产物命名规则，避免使用
    未绑定 baseline 的本地结果。 `(关联需求: U2, U4)` `(依赖于: 1.2)`

- [x] 2. **后端构建与核心轻量修复** `[优先级: 高]`

  - [x] 2.1. **TDD**: 编写或补齐 Hubble BE 单元测试，覆盖 `shortestPath`
    graph view 中 path 顶点/边保留逻辑。 `(关联需求: E10, X2)` `(依赖于: 1.3)`
  - [x] 2.2. 修改 `OltpAlgoService`，确保 `shortestPath` 返回的 `GraphView`
    包含 path 中的顶点和边，并保持 json/table 结果可用。 `(关联需求: E10, X2)`
    `(依赖于: 2.1)`
  - [x] 2.3. **TDD**: 编写或补齐 Hubble upload option 单元测试，覆盖默认上传格式
    白名单包含 issue 验证所需文本数据格式。 `(关联需求: E9, X2)` `(依赖于: 1.3)`
  - [x] 2.4. 修改 `HubbleOptions` 默认上传格式白名单，使 Hubble data import smoke
    可使用 `csv` 和 `txt` 文件。 `(关联需求: E9, X2)` `(依赖于: 2.3)`
  - [x] 2.5. 调整 Hubble BE `unit-test` profile 和 `UnitTestSuite`，确保
    `mvn test -P unit-test -pl hugegraph-hubble/hubble-be -ntp` 只跑无需外部
    Server 的单测集合。 `(关联需求: U1, X1)` `(依赖于: 2.1, 2.3)`
  - [x] 2.6. 升级或调整构建依赖，使 Hubble BE 在当前验证 JDK 上可编译，不引入
    与 issue 无关的依赖升级。 `(关联需求: U1, X1)` `(依赖于: 2.5)`
  - [x] 2.7. **TDD**: 补齐 Hubble algorithm controller 边界测试，并为前端实际
    调用的 `shortpath` slug 增加后端兼容 alias，避免 FE shortest-path 页面请求
    404/405。 `(关联需求: E6, U5, X5)` `(依赖于: 2.2)`

- [x] 3. **Hubble dist、启动脚本与容器入口修复** `[优先级: 高]`

  - [x] 3.1. 修改 `start-hubble.sh`，使 `-f` / foreground 模式无需额外布尔参数，
    并以前台 `exec` 方式运行。 `(关联需求: E2, E3)` `(依赖于: 1.3)`
  - [x] 3.2. 修改 Dockerfile entrypoint 使用 Hubble 前台模式，避免容器入口脚本
    退出。 `(关联需求: E3)` `(依赖于: 3.1)`
  - [x] 3.3. 修改 travis/static 启动脚本，使本地验证脚本与正式 binary 启动语义
    一致。 `(关联需求: E2, E4)` `(依赖于: 3.1)`
  - [x] 3.4. 修改 Hubble dist assembly/pom，使 binary candidate 包含根级
    `LICENSE`、`NOTICE` 和 `licenses/**`，并沿用当前 FE build 到 `ui/` 的
    打包链路。 `(关联需求: U6, U7)` `(依赖于: 1.3)`

- [x] 4. **可复现验证脚本补齐** `[优先级: 中]`

  - [x] 4.1. 编写 i18n 静态检查脚本，校验 `zh-CN` 与 `en-US` key 对称、无空值、
    无明显 raw key/占位风险。 `(关联需求: U8, X7)` `(依赖于: 1.3)`
  - [x] 4.2. 编写 binary candidate 检查脚本，校验 tarball 根结构、legal 文件、
    runtime residue、Node/Yarn 缓存和未知外层二进制。 `(关联需求: U6, U7)`
    `(依赖于: 3.4)`
  - [x] 4.3. 编写 issue #694 live smoke 脚本入口，支持对指定 Hubble tarball 和
    HugeGraph Server URL 执行 health、UI root、核心 API 路由和 Server 连接检查。
    `(关联需求: E2, E4, E5, E7, X4)` `(依赖于: 3.1, 3.4)`
  - [x] 4.4. 在验证脚本输出中显式区分 static route fallback、Hubble backend API
    可达、GraphServer direct 可达和未完成 gate。 `(关联需求: U3, U4, U5, X3)`
    `(依赖于: 4.3)`
  - [x] 4.5. 编写 API boundary inventory 脚本，自动对比 FE algorithm slug 与
    Hubble BE 暴露的 algorithm endpoint，输出 JSON/Markdown 证据。 `(关联需求:
    U5, X5, X6)` `(依赖于: 4.4)`
  - [x] 4.6. 编写 browser/UI smoke、full acceptance 和 runtime i18n switch 脚本，
    要求 Playwright 环境存在时采集核心路由截图和网络请求证据。 `(关联需求:
    E5, E6, E11, X3, X7)` `(依赖于: 4.4)`
  - [x] 4.7. 扩展 binary candidate 检查脚本，输出 jar 数量、FE license 数量、
    source map 缺失检查、未知外层 binary/archive 检查和 native-bearing jar 清单。
    `(关联需求: U6, U7)` `(依赖于: 4.2)`

- [x] 5. **构建、单测与静态验证执行** `[优先级: 高]`

  - [x] 5.1. 执行依赖模块构建：
    `mvn install -pl hugegraph-client,hugegraph-loader -am -DskipTests -Dmaven.javadoc.skip=true -ntp`。
    `(关联需求: U1, E1)` `(依赖于: 2.6)`
  - [x] 5.2. 执行 Hubble BE 单测：
    `mvn test -P unit-test -pl hugegraph-hubble/hubble-be -ntp`。 `(关联需求: U1, X1)`
    `(依赖于: 2.6)`
  - [x] 5.3. 执行 Hubble package：
    `cd hugegraph-hubble && mvn package -DskipTests -Dmaven.javadoc.skip=true -ntp`。
    `(关联需求: E1, U6)` `(依赖于: 3.4, 5.1)`
  - [x] 5.4. 执行 i18n 静态检查脚本。 `(关联需求: U8, X7)` `(依赖于: 4.1, 5.3)`
  - [x] 5.5. 执行 binary candidate 检查脚本。 `(关联需求: U6, U7)` `(依赖于: 4.2, 5.3)`
  - [x] 5.6. 执行 `git diff --check`，确认无空白格式问题。 `(关联需求: U11)`
    `(依赖于: 5.5)`

- [x] 6. **运行态和集成 gate 验证** `[优先级: 高]`

  - [x] 6.1. 从最终 Hubble tarball 解包并使用包内 `bin/start-hubble.sh` 做启动、
    health、UI root 和 stop smoke。 `(关联需求: E2, E4)` `(依赖于: 5.3)`
  - [x] 6.2. 若本地 HugeGraph Server 可用，执行 `verify-hubble-issue-694.sh`
    验证 Hubble API 与 Server 基础连接。 `(关联需求: E7, X4)` `(依赖于: 4.3, 6.1)`
  - [x] 6.3. 若 Server 和 Loader flow 可用，执行图连接、schema、上传、mapping、
    Loader-backed import、Gremlin count、shortestPath 的 live smoke。
    `(关联需求: E8, E9, E10)` `(依赖于: 6.2)`
  - [x] 6.4. 使用浏览器或 Playwright 验证核心前端路由实际请求 Hubble backend，
    并保存截图/网络请求证据。 `(关联需求: E5, E6, X3)` `(依赖于: 6.1)`
  - [x] 6.5. 使用浏览器或 Playwright 验证运行态语言切换和核心页面布局，无明显
    raw key 或英文溢出。 `(关联需求: E11, X7)` `(依赖于: 6.4)`

  **状态说明**: 6.1 已完成；核心 route fallback 已通过 `curl` 部分验证。
  6.2 已用用户启动的 `observability-hugegraph-alert-1` 容器完成，Server URL 为
  `http://127.0.0.1:18082`。6.3 已通过用户终端执行的 packaged Hubble
  live smoke，证据文件为 `evidence/live-loader-flow.json`；本次验证覆盖
  schema、CSV upload、file mapping、Loader task `SUCCEED`、job `SUCCESS`、
  Hubble/Server Gremlin count `3/2` 和 shortestPath graph view/direct
  Server 对比 `3/2`。
  6.4 和 6.5 已通过用户终端执行的 Playwright full acceptance，证据文件为
  `evidence/ui-full-acceptance.json`、`evidence/ui/ui-browser-smoke.json` 和
  `evidence/ui/ui-i18n-switch-smoke.json`。本次浏览器验证覆盖 5 个核心路由的
  后端 API 请求匹配、截图、无 raw i18n key、无 console error，以及 zh-CN/en-US
  运行态语言切换。
  详见 `logs/task-06-runtime-and-integration.md`。

- [x] 7. **执行日志与 review 收口** `[优先级: 高]`

  - [x] 7.1. 创建 `.workflow/hubble-v2-issue-694/logs/`，按任务记录命令、结果、
    输出摘要、失败和未完成 gate。 `(关联需求: U9, U11)` `(依赖于: 5.1)`
  - [x] 7.2. 更新或整理 issue #694 review 文档，明确当前分支证据已关闭的 gate
    和仍需作为产品/API 边界说明的范围项。 `(关联需求: U4, X4, X7)`
    `(依赖于: 6.5)`
  - [x] 7.3. 对 API boundary 进行最终分类，避免把 Server-only、未实现算法或
    static fallback 写成 Hubble 已支持能力。 `(关联需求: U5, X5, X6)` `(依赖于: 6.3, 6.4)`
  - [x] 7.4. 复查当前代码 diff 与飞书设计/本地 design/tasks 的覆盖关系，列出
    issue #694 是否满足，以及剩余产品/API 边界说明。 `(关联需求: U4, U11, X8)`
    `(依赖于: 7.2, 7.3)`
