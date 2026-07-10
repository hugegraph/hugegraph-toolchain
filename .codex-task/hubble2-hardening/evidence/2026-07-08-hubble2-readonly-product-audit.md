# Hubble 2.0 read-only 产品探测审计

> 这是 2026-07-08 的只读证据快照，不表示当前任务状态。当前状态只看
> [`TODOs.md`](../TODOs.md)；本文件仅保留当时的环境、复现步骤和观察结果。

时间：2026-07-08 20:45 CST

## 范围与结论

- 审计对象：Hubble FE `http://localhost:3000`，Hubble BE PD mode `http://127.0.0.1:8088`，Server PR #3008 `http://127.0.0.1:18080`。
- 登录账号：`admin / pa`。
- 当前分支：`hubble2`。
- 本次只读探测：未修改源码、未提交、未推送；仅新增本报告和截图/JSON evidence。
- 截图与页面状态目录：`.codex-task/hubble2-hardening/evidence/screenshots/2026-07-08-hubble2-readonly-product-audit/`
- 总体结论：核心登录、图管理、数据源、数据导入、Gremlin 查询、算法页、异步任务页可打开；P0 未发现。仍有导航首页系统管理死链、dashboard 外链不可达、图详情统计失败空详情，以及英文模式中文残留。

## Route / 功能矩阵

| 路由/入口 | 结果 | 证据 |
| --- | --- | --- |
| `/navigation` | 可打开；导航首页仍展示系统/运维中文入口 | `01-after-login-navigation.png`, `route-matrix-a.json` |
| `/graphspace` | 可打开；显示 DEFAULT 图空间 | `nav-01-graphspace.png`, `route-matrix-a.json` |
| `/source` | 可打开；空列表正常显示 | `nav-02-source.png`, `route-matrix-a.json` |
| `/task` | 可打开；空任务列表正常显示 | `nav-03-task.png`, `route-matrix-a.json` |
| `/gremlin` | 自动跳到 `/gremlin/DEFAULT/hugegraph`，可打开 | `route-matrix-b.json` |
| `/gremlin/DEFAULT/hugegraph` | 可打开；只读查询可执行 | `gremlin-ui-count-query.png`, `gremlin-ui-invalid-query.png` |
| `/algorithms` | 自动跳到 `/algorithms/DEFAULT/hugegraph`，可打开 | `route-matrix-b.json` |
| `/algorithms/DEFAULT/hugegraph` | 可打开；表单可见，未执行 mutation/耗时算法 | `route-matrix-b.json` |
| `/asyncTasks` | 自动跳到 `/asyncTasks/DEFAULT/hugegraph`，可打开 | `route-matrix-b.json` |
| `/asyncTasks/DEFAULT/hugegraph` | 可打开；空列表正常显示 | `route-matrix-b.json` |
| `/my` | 可打开 | `route-matrix-a.json` |
| `/account` | 可打开；账号列表可见 | `route-matrix-a.json` |
| `/role/graphspace/DEFAULT/admin` | 可打开；但根 `/role` 不可打开 | `route-matrix-c.json` |
| `/super` | 404 | `issue-super-404.png` |
| `/resource` | 404 | `issue-resource-404.png` |
| `/role` | 404 | `issue-role-404.png` |
| `/graphspace/DEFAULT/graph/hugegraph/detail` | 可打开，但统计区弹出空详情失败 | `issue-graph-detail-empty-gremlin-error.png` |

## P0

未发现 P0。登录、主导航、核心 Gremlin 查询、图管理、数据源、数据导入、算法页、任务页均可进入。

## P1

### P1-1：导航首页系统管理暴露未注册路由

- 路由/入口：`/navigation` -> `系统管理` -> `超管管理`、`资源管理`、`角色管理`。
- 复现步骤：
  1. 登录 `admin / pa`。
  2. 打开 `/navigation`。
  3. 点击 `超管管理`、`资源管理`、`角色管理`。
- 期望结果：入口不可用时隐藏/禁用，或有可用页面/明确提示。
- 实际结果：分别进入 `/super`、`/resource`、`/role`，均显示 `404 页面不存在`。
- 证据：
  - `issue-super-404.png` / `issue-super-404.json`
  - `issue-resource-404.png` / `issue-resource-404.json`
  - `issue-role-404.png` / `issue-role-404.json`
  - route 表：`hugegraph-hubble/hubble-fe/src/routes/index.js:152-157` 中 `/role`、`/resource` 根路由被注释，未注册 `/super`。
- 初步归因：
  - `hugegraph-hubble/hubble-fe/src/modules/navigation/AdminItem/index.js:29-45` 硬编码展示 `/super`、`/resource`、`/role`。
  - `hugegraph-hubble/hubble-fe/src/components/Sidebar/index.ant.js:41-48` 侧边栏已临时隐藏 Resource/Role，但导航首页未同步。
- 建议处理：easy-first，先隐藏或禁用未接入入口；如必须保留，补 route 或禁用态说明。

### P1-2：运维管理入口拼接 dashboard 外链，但目标不可达

- 路由/入口：`/navigation` -> `运维管理` -> `集群管理`、`监控管理`、`运维管理`、`报警管理`。
- 复现步骤：
  1. 打开 `/navigation`。
  2. 读取 `GET /api/v1.3/dashboard`。
  3. 访问返回地址及子路径。
- 期望结果：dashboard 未配置或不可达时隐藏/禁用入口，或给出明确配置提示。
- 实际结果：
  - `/dashboard` 返回 `{"status":200,"data":{"address":"127.0.0.1:8092"}}`。
  - `http://127.0.0.1:8092/`、`/monitor/machine`、`/operate/node`、`/alert/rule` 均连接失败：`Failed to connect to 127.0.0.1 port 8092`。
- 证据：
  - `01-after-login-navigation.png` 展示运维入口。
  - API/外链摘要保存在本报告；终端探测为 `curl --max-time 3 http://127.0.0.1:8092/...`。
- 初步归因：
  - `hugegraph-hubble/hubble-fe/src/modules/navigation/ConsoleItem/index.js:47-51` 直接把 dashboard address 拼成外链。
  - `hugegraph-hubble/hubble-fe/src/modules/navigation/ConsoleItem/index.js:57-73` 无可达性检查或禁用态。
- 建议处理：先禁用/隐藏不可达 dashboard 入口；后续单独 goal 做 dashboard 配置、健康检查和跳转策略。

### P1-3：图详情页统计接口失败时展示空详情错误

- 路由/入口：`/graphspace/DEFAULT/graph/hugegraph/detail`。
- 复现步骤：
  1. 打开 `/graphspace/DEFAULT/graph/hugegraph/detail`。
  2. 等待统计区域加载。
  3. 观察页面错误提示，并直接请求统计 API。
- 期望结果：统计失败时有明确错误原因；如果统计不可用，应显示降级空态，不重复弹空详情。
- 实际结果：
  - 页面出现 `Gremlin 执行失败，详细信息:` 两次，详情为空。
  - `GET /api/v1.3/graphspaces/DEFAULT/graphs/hugegraph/statistics` 返回 HTTP 200，但 body 为 `{"status":400,"data":null,"message":"Gremlin 执行失败，详细信息: ","cause":": "}`。
- 证据：
  - `issue-graph-detail-empty-gremlin-error.png`
  - `issue-graph-detail-empty-gremlin-error.json`
  - API 摘要见上。
- 初步归因：
  - 前端 `hugegraph-hubble/hubble-fe/src/pages/GraphDetail/index.js:84-90` 对 statistics 非 200 直接 `message.error(res.message)`。
  - API 入口 `hugegraph-hubble/hubble-fe/src/api/manage.js:135-140`。
  - 根因更可能在 BE statistics/Gremlin 统计实现返回空 cause；前端也缺少空 message 兜底。
- 建议处理：先补后端 statistics 错误 message/cause；前端补空 message fallback 和去重提示。可单独作为查询/统计失败态 goal。

## P2

### P2-1：英文模式仍有明显中文残留

- 路由/入口：`/navigation`、`/account`、`/graphspace/DEFAULT/graph/hugegraph/detail`。
- 复现步骤：
  1. 通过顶部语言下拉切到 `English`。
  2. 打开上述页面。
- 期望结果：英文模式下可见主流程文案使用英文；中文只保留为业务数据。
- 实际结果：
  - `/navigation` 主导航大部分变英文，但 `系统管理`、`超管管理`、`资源管理`、`角色管理`、`运维管理`、`集群管理` 等仍为中文。
  - `/account` 页面标题、按钮、表头、操作项仍为中文。
  - `/graphspace/DEFAULT/graph/hugegraph/detail` 标题和统计标签仍为中文。
- 证据：
  - `language-switch-english.png`
  - `language-switch-english.json`
  - `english-mode-route-sample.json`
  - `english-mode-graph-detail.png`
- 初步归因：
  - `AdminItem` / `ConsoleItem` / `GraphDetail` 仍有硬编码中文。
  - Account/Role/Resource/GraphDetail 属于既有暂停 i18n 范围。
- 建议处理：不插队当前 hardening；后续开 i18n 专项，按 route matrix 清理。

### P2-2：Gremlin 查询失败即时结果区清楚，但执行记录不展示失败详情

- 路由/入口：`/gremlin/DEFAULT/hugegraph`。
- 复现步骤：
  1. 输入 `g.V().badSyntax(`。
  2. 点击 `执行查询`。
  3. 查看即时结果区和执行记录。
- 期望结果：即时结果区和历史记录都能帮助用户理解失败原因，至少历史记录可展开/查看详情。
- 实际结果：
  - 即时结果区显示完整错误：`非法的 Gremlin 语句，详细信息: startup failed...`。
  - 执行记录列表仅显示 `失败`，未在当前列表中直接展示失败详情。
- 证据：
  - `gremlin-ui-invalid-query.png`
  - `gremlin-ui-invalid-query.json`
  - API 非法查询返回 `status=460`，message 含 Groovy 编译行列。
- 初步归因：
  - 查询执行链路本身可用；问题在执行记录列表的信息密度/详情入口。
- 建议处理：低优先级增强执行记录失败详情展示；不作为核心可用性 blocker。

### P2-3：开发控制台存在既有库兼容警告

- 路由/入口：多页面。
- 复现步骤：打开 `/navigation`、`/source`、`/task`、`/account` 等页面后读取 console warning/error。
- 期望结果：长期治理时减少框架升级噪音，避免掩盖真正错误。
- 实际结果：出现 Ant Design `Dropdown overlay is deprecated`、React Router v7 future flags、`findDOMNode`、Table defaultProps 等警告。
- 证据：`route-matrix-a.json`、`route-matrix-b.json`、`route-matrix-c.json` 中 `logs` 字段。
- 初步归因：Ant Design/React Router 版本兼容警告，非当前产品主流程阻断。
- 建议处理：后续依赖升级/前端治理专项处理；当前不插队。

### P2-4：数据管理 mutation 未执行，仅记录需要后续 smoke

- 路由/入口：图管理、数据源管理、数据导入、任务管理。
- 复现步骤：本次只打开列表页和只读查看，不创建/删除/导入。
- 期望结果：后续用隔离 fixture 执行创建、导入、删除、任务详情 smoke。
- 实际结果：只读页面可打开；mutation 未验证。
- 证据：`nav-01-graphspace.png`、`nav-02-source.png`、`nav-03-task.png`、`route-matrix-a.json`、`route-matrix-b.json`。
- 初步归因：按本次 goal 边界主动跳过 mutation。
- 建议处理：后续单独 goal 做数据导入 mutation smoke。

## 查询/API 摘要

- `GET /api/v1.3/graphspaces/DEFAULT/graphs/hugegraph/gremlin-query`：`status=200`，返回 `vertexcount=2`、`edgecount=1`。
- `POST /gremlin-query {"content":"g.V().limit(1)"}`：`status=200`，返回 1 个 `h2_algo_person` 顶点。
- `POST /gremlin-query {"content":"g.V().count()"}`：`status=200`，返回 `[2]`。
- `POST /gremlin-query {"content":"g.V().badSyntax("}`：`status=460`，message 含具体 Groovy 编译错误。
- `GET /async-tasks`：`status=200`，空列表。
- `GET /dashboard`：`status=200`，`address=127.0.0.1:8092`；该地址当前不可达。
- `GET /statistics`：HTTP 200，但 body `status=400`，message/cause 为空详情。

## Remote CI 异步参考

- 只做一次短查询，未等待远端队列。
- `gh pr checks 4 -R hugegraph/hugegraph-toolchain` 当前显示：`hubble-ci (11, 3.11)` fail；其余列出的 `check-dependency-license`、license header、client、client-go、loader、spark、tools、triage 为 pass，CodeRabbit skipped/pass。
- 本次没有继续拉取 CI log；按用户要求，remote CI 只作为异步参考。
