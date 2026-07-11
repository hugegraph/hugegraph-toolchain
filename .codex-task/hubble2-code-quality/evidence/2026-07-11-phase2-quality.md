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
- 改为 `CI=true` 会因上述锁定第三方 warning 失败；根治需要依赖/lockfile 升级，必须先获用户批准。
