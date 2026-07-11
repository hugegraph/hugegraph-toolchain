# Hubble2 Code Quality Progress

## 当前 checkpoint

- 更新时间：2026-07-11 23:20 +08:00
- 当前阶段：Phase 6 前最终实现与验证
- 当前分支：`hubble2`
- 目标状态：active
- 唯一实时状态源：[`todo.md`](todo.md)
- 执行合同：[`plan.md`](plan.md)

## 已完成

- 创建新的 code-quality 状态空间，未重新启用已完成的 hardening TODO。
- 从 `.codex-task/hubble2-hardening/EXECUTION.md` 和最终 evidence 提炼三层验证、`mvnd` 复用、批处理、慢任务并行、有限重试和真实门禁经验。
- 在 plan 中增加 cold/warm、本地/CI 分段计时、cache hit/miss、重复构建和 before/after 性能证据方法。
- 在 HEAD `b66848d3` 完成 fresh audit，记录工具版本、工作树边界、FE build/Jest、BE compile/test/checkstyle、CI workflow/run 与核心 API 覆盖初表。
- 清除 Java 11 + mvnd 可复用的失效假设；当前固定 Java 11 + Maven 3.9.11。
- 将 TODO 从初始 25 项重排为审计后 30 项，增加依赖解析前置阶段、Jest/第三方 warning 分类与 checkstyle 可诊断性事项。
- 通过排除 Hubble 未使用的 `hugegraph-core -> hg-store-common` 传递依赖恢复解析；修复既有 import 清理暴露的 compile errors。
- BE compile 成功；Hubble BE 119 tests 全部通过；依赖树不再包含破损的 `hugegraph-struct` 链。
- 清理 stale JaCoCo 数据后重跑成功，报告分析 265 classes，无 class mismatch。
- Hubble BE checkstyle 从 128 项降至 0；clean javac unchecked warning 从 49 项降至 0，并清理本项目可控 deprecation warning。
- Client/Loader Java 11 联动 install 成功：41.04s（root 2.98s、client 1.87s、loader 36.06s）。
- EditorConfig 扫描排除通用生成物/二进制缓存，避免本地 ignored `node_modules`、`dist`、`.vinext`、`.wrangler`、DB、SVG/WOFF2 干扰真实门禁。
- FE Router warning 6 -> 0；full Jest 40 suites / 148 tests 通过；第三方 warning 已隔离。
- 核心 API 覆盖矩阵完成；新增 Schema view 成功/失败合同，mutation RED 有效，完整 BE 121 tests 通过。
- CI 性能慢点完成分段；实现精确 Server SHA tarball cache，最新产品 HEAD `66e36594` 已推送并触发 PR #4 CI。
- Hubble BE checkstyle 真实失败阈值已启用；临时违规 exit 1，恢复后 0 violations。
- FE 显式 lint 已恢复：JS/JSX 329 文件原本 clean；唯一 TS 文件从配置 exit 2、36 errors 修至全量 330 文件 0 warning/error，并接入 CI。

## 当前工作树边界

- fresh audit 前已有 26 个 Hubble BE Java 修改（`+150/-57`），内容为 import/style 清理；视为既有未提交质量修复，保留并纳入后续 scoped verification。
- 本轮新增产品改动为 `hubble-be/pom.xml` 的单一传递依赖排除及既有 dirty style/import 修复所需的漏 import 补全；未触及业务控制流和公开 API。

## 最近验证

- FE `CI=true yarn build` fresh：exit 0，56.80s；52 条第三方 source-map warning 及 Browserslist/bundle 提示待 DEC-FE-01。
- FE Jest：40 suites / 148 tests 通过，4.57s；项目 Router warning 已清零，剩余 act/defaultProps 为锁定依赖 warning。
- FE `yarn lint`：exit 0，4.36s；`CI=true yarn build`：exit 0，49.26s；Jest 40/148：exit 0，5.34s。
- BE clean compile：成功，265 source files；本项目 unchecked/deprecation warning 为 0，剩余输出为外部发布 POM/仓库元数据 warning。
- BE unit：119 tests，0 failure/error/skip；clean JaCoCo report 成功分析 265 classes。
- BE checkstyle：Hubble source diagnostics 128 -> 0；插件仍存在“报告问题但 exit 0”的全局可诊断性问题，保留 CQ-LINT-06。
- Client/Loader install：exit 0，41.04s；shade 的历史依赖重复 warning 不属于本轮 Hubble 源码 warning。
- BE unit：新增 API 合同后 121 tests，0 failure/error/skip，15.14s。
- PR #4 当前 HEAD `66e36594` 的 Hubble CI run `29157701832` 已触发。

## 阻塞与决策

- server-core 发布 POM 阻塞已用 Hubble 最小排除解除；尚需 package/真实 CI 证明运行兼容。
- DEC-FE-01/02 已获用户批准：本轮精确 pin React/ReactDOM 18.2.0、恢复 `CI=true`，并以限定来源/数量接受 52 条第三方 source-map warning。
- Antd/X6/Graphin/G6/Dagre 等核心可视化栈现代化已登记为未来任务，明确不属于当前 task/goal；稳定版本冻结后再积极迁移和重构。
- 当前唯一构建执行器为 Yarn；删除无消费路径且与精确 pin 冲突的 `package-lock.json`。既有 peer-dependency、Browserslist 与 bundle 债务纳入未来现代化任务，本轮不补包、不升级核心图组件。
- React/ReactDOM 18.2 稳定批次已完成：lint 0、Jest 148/148 且控制台 warning 归零、`CI=true` build 通过、浏览器登录页正常、Maven package/distribution audit 通过。
- 精确 PR #3008 Server SHA 隔离构建通过；完整 runtime/UI acceptance 在一次环境生命周期恢复后通过，未发现 server-core 运行兼容错误。
- Server cache 本地 before/after 已收口：165.26s cold vs 0.40s hit 中位数，约节省 99.76%，未削弱 SHA 校验或 miss 源码构建。
- 合并重复 FE build/release scan 属门禁编排重构（DEC-CI-02），未实施。

## 下一动作

1. 运行最终新鲜本地 FE/BE/联动/release sidecar 门禁并保存退出码。
2. GitHub quota 恢复后补最终 HEAD Hubble CI 与 cache 交叉验证；不作为当前本地推进 blocker。
3. 冻结实现后执行独立 review，修复 actionable findings 并 re-review。

## 恢复入口

续跑时先读取 `plan.md`、`todo.md`、`progress.md`，核对当前 HEAD/工作树是否变化，然后从 `todo.md` 第一个未完成项继续；不要创建重复目标或重新打开 hardening TODO。
