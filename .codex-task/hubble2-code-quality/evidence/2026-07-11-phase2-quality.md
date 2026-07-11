# Phase 2 quality and performance evidence

- 日期：2026-07-11
- 基线 HEAD：`d8e27523ddf6d0e01dde72143fd464551075f146`
- 当前实现 HEAD：`66e36594ee279bd8063d0bf2dc338f8698e41f46`
- 工作树边界：本文件更新前产品代码已全部按批次提交并推送；本地仅 source-of-truth 文档待收口。

## FE warning 分类

- fresh `CI=true yarn build`：exit 0，56.80s；52 条第三方 source-map 缺源 warning，另有 Browserslist DB 陈旧和 2.14 MB bundle 提示。
- fresh Jest：40 suites / 148 tests，exit 0，6.49s；项目可控 React Router future warning 6 条。
- 最小修复：仅为 3 个测试夹具的 `MemoryRouter` 增加 React Router 已支持的 future flags，不改生产路由。
- scoped：3 suites / 6 tests，exit 0，3.30s；full：40 / 148，exit 0，4.57s；Router warning 6 -> 0。
- 剩余 `ReactDOMTestUtils.act`、`rc-table defaultProps`、source-map warning 均来自锁定依赖；不通过关闭 source map、过滤 console 或弱化 CI 处理。

## 核心 API 覆盖矩阵与最小增量

| 核心域 | 当前成功合同 | 当前重要失败/权限/边界合同 | 本轮结论 |
|---|---|---|---|
| 认证/授权 | 登录、会话、鉴权路由 | 401、503、无效 token/权限 | 已有覆盖充分，不重复增加 |
| Graph/GraphSpace | create/update/default、import | legacy payload、导入边界/失败 | 已有覆盖充分，不重复增加 |
| Gremlin/Query | escaping 与查询构造 | 非法 operator、结构化注入值、history failure | 已有覆盖充分，不重复增加 |
| Loader/Task | 路由、password/token | schema/file mapping 边界 | 已有覆盖满足最小合同 |
| Schema view | export header 安全 | 缺少 view 成功映射与引用不存在失败 | 新增 2 个最小合同测试 |

新增 `SchemaServiceViewTest`：

- 成功合同：vertex id、primary keys、property type 映射及空 edge 列表。
- 失败合同：label 引用不存在的 property key 时抛出现有 `InternalException`。
- 敏感性 RED：临时将真实属性类型映射替换为 `mutation`，2 tests 中成功合同按预期失败；该 mutation 随即恢复，未进入 diff。
- GREEN：targeted 2/2；完整 Hubble BE 121 tests，0 failure/error/skip，15.14s。

## CI 性能与可诊断性

现行 workflow 两个全 cache-hit 成功样本：run `29145996476` 约 9m30s；run `29154385676` 约 14m45s。runner 波动约 55%，后续 before/after 使用至少 3 个 warm run 的中位数与范围。

| 慢点 | 快样本 | 慢样本 | 占比/判断 |
|---|---:|---:|---|
| Compile | 1m58s | 3m19s | 包含第一次 FE build |
| Prepare env/service | 4m55s | 7m49s | 最大关键路径 |
| FE production build x2 | 2m08s | 3m41s | 总时长约 22-25% |
| Server source package | 2m19s | 3m36s | 总时长约 24% |
| BE unit + API | 41s | 60s | 非主要慢点 |

低风险优化 `66e36594`：复用 Loader CI 已验证的精确 Server commit SHA tarball cache。cache miss 仍 checkout 并校验实际 SHA、源码 package；cache hit 只复用同 SHA 目录。已验证 YAML 解析、shell syntax 与本地 cache-hit 分支。真实收益待本 HEAD 三次 warm CI 证据。

## 当前门禁冲突

- `hubble-dist/pom.xml` 的 frontend build 显式设置 `<CI>false</CI>`，生成的 `dist.sh` 也 export `CI=false`。
- workflow 的独立 FE step 未运行 production build；历史成功日志中两次 `Compiled with warnings` 仍绿。
- 因此当前真实 CI 即使通过，也只能证明现行宽松门禁，不满足本目标最终证据。
- 临时将两处环境改为 `CI=true` 后，完整 Maven package 仍 exit 0，117.73s，distribution audit 通过；52 条 source-map warning 仍存在。临时修改已恢复，未进入 diff。
- CRA 5 在本项目中不会将这些 warning 提升为失败，因此恢复 `CI=true` 可消除宽松配置，但不能技术性清零第三方制品 warning。

## Checkstyle 门禁 A/B

- 根因：`tools/checkstyle.xml` 全局 severity 为 `info`，插件默认 `violationSeverity=error`，导致 diagnostics 打印但计数为 0。
- 全仓当前债务 15 条：Client 6、Loader 9、Hubble BE 0。部分 Client 项为公开 API 名称，不在本目标中重命名。
- `0fe6fd1f` 仅在 Hubble BE 将失败阈值设为 `info` 并保留 `failOnViolation=true`。
- 临时 117 字符行 mutation：exit 1、1 violation；恢复后 exit 0、0 violation。mutation 未进入 diff。

## FE 依赖决策矩阵

- React/Jest：声明 `^18.2.0` 实装 18.3.1；18.3 专门增加 React 19 迁移 warning。最低风险候选是精确 pin `react`/`react-dom` 18.2.0，可望清除 `act` 与 function `defaultProps` warning。
- X6 38 条：`@antv/x6-react-components` 1.1.20 已是最后 1.x；抽检 2.0.9/3.0.0 仍缺发布源码，major 升级不修根因。
- Dagre 10 条：传递依赖 0.0.11；越界 override 0.0.12/13 无清 warning 证据，且有布局行为风险。
- Antd 4 条：4.24.16 已是 4.x 末版；升级 5/6 才避开旧 map，但会改变 CSS/DOM/API/视觉，不符合稳定目标。
- 推荐决策：批准 React 18.2 精确 pin；52 条 source-map warning 登记为已诊断的外部发布制品缺陷并由用户明确接受。禁用 source map、过滤 loader、patch `node_modules` 均不采用。

## 用户决策后的稳定实现

- 用户批准精确 pin React/ReactDOM 18.2.0，并接受限定来源/数量的 52 条第三方 source-map warning。
- `dbf7090d`：package/lock 只改变 React、ReactDOM 与 scheduler 解析；删除仓库和 CI 无消费路径的 npm lockfile，Yarn 保持唯一构建执行器。
- 实装版本：React 18.2.0、ReactDOM 18.2.0；frozen offline install exit 0。
- `yarn lint`：330 files，0 warning/error，5.20s。
- Jest：40 suites / 148 tests，0 warning，6.09s；18.3 的 `act/defaultProps` warning 归零。
- `CI=true yarn build`：exit 0，47.73s；第三方 source-map warning 精确保持 X6 38、Dagre 10、Antd 4；主 bundle 2.14 MB（较前一构建 +53 B）。
- Browser：本地 1280px 登录页正常渲染，无横向溢出，用户名/密码/登录控件存在；浏览器控制台 0 error/warning。
- Maven `CI=true` package：exit 0，123.93s；distribution audit 通过（423 JAR、275 license files、43 FE license files、10 native-bearing JAR）。
- Antd/X6/Graphin/G6/Dagre、peer dependency、Browserslist/CRA 与 bundle 现代化全部登记为未来计划，明确不属于当前 task/goal。

## 精确 Server SHA 本地兼容与性能

- CI baseline：Apache HugeGraph Server PR #3008 head `3bd990d8b58e81cb61e3b85c287d34243836f181`；其 Git tree 与 merge commit `99936be5` 完全一致。
- 为保护 `/Users/imbajin/github/graph/server` 的既有脏工作树，从该精确 SHA `git archive` 到临时目录构建；43-module package exit 0，165.26s。
- Server tarball SHA-256：`1d76f3ab8afe45ccd36dcd2eb6fbc84e9d947c4db6bb7ef87291799a8a5757d9`。
- 同一 397 MB、同一 SHA cache 连续 hit：0.41s / 0.40s / 0.35s，中位数 0.40s；相对 cold build 165.26s 节省 164.86s（约 99.76%）。cache miss 仍执行源码构建与 checkout SHA 校验，未削弱兼容门禁。
- 首次 acceptance 因 daemon 随执行 session 退出导致 Server 8080 不再监听；Hubble health/静态路由 7 项已通过，但 login 连接拒绝。该失败分类为环境生命周期，不是产品断言失败。
- 有限恢复：使用同一 tarball foreground session 保持 Server 生命周期后重试一次，完整 acceptance 通过。证据目录：`evidence/live-acceptance-2026-07-11/`。
- Runtime：27 checks；登录/ADMIN、Server login、GraphSpace、Schema view、Job/Async、Property/Vertex/Edge schema、CSV upload/file mapping/loader、3 vertices/2 edges、direct shortest-path、Cypher/OLAP boundary 均符合合同。
- UI：GraphSpace、Gremlin、Algorithms、AsyncTasks API/页面检查通过，console errors 为空；中英文切换通过，无 raw i18n key/404。
- `hubble-shortestpath-string-id-followup` 仍按既有 acceptance 合同标记为已知 follow-up；本轮 direct shortest-path 数据合同已通过，该 follow-up 不由本稳定化 goal 扩展修复。
