# Hubble2 Code Quality Progress

## 当前 checkpoint

- 更新时间：2026-07-12 00:10 +08:00
- 当前阶段：Phase 6 独立审查与延迟 CI 取证
- 当前分支：`hubble2`
- 目标状态：active
- 唯一实时状态源：[`todo.md`](todo.md)
- 执行合同：[`plan.md`](plan.md)
- 完整本地门禁覆盖的产品实现 SHA：`10c0e8fa4f18694f8f848212ed1da52ef36d80a4`
- 独立 review checkpoint / PR 观测 SHA：`03be1bd7b9e2cbdbac90499821bbf4ce147124ac`；相对实现 SHA 仅新增 SOT/evidence 文档
- 当前工作树：clean（生成物均 ignored）

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

## 工作树边界

- fresh audit 基线：审计前已有 26 个 Hubble BE Java 修改（`+150/-57`），已保留并纳入 scoped verification。
- 当前：工作树 clean；本轮提交覆盖 BE 依赖/风格门禁、FE lint/React 稳定批次、Schema 最小合同测试、精确 Server cache 和 CI 可诊断性。未改变公开 API 或业务控制流。

## 最近验证

- FE `yarn lint`：exit 0，4.94s，330 files 0 warning/error；Jest：40 suites / 148 tests，exit 0，6.50s，console warning 0。
- FE `CI=true yarn build`：exit 0，47.47s；仅保留已接受的 52 条第三方 source-map warning，以及已转未来任务的 Browserslist/bundle 提示。
- BE clean compile：成功，265 source files；本项目 unchecked/deprecation warning 为 0，剩余输出为外部发布 POM/仓库元数据 warning。
- BE clean unit：121 tests，0 failure/error/skip，exit 0，31.40s；checkstyle 0，JaCoCo 分析 407 classes。
- Hubble checkstyle info 级门禁已证明临时违规 exit 1、恢复后 exit 0；全仓 15 条既有 debt 不扩入本目标。
- Client/Loader linked install：exit 0，49.26s；shade 的历史依赖重复 warning 不属于本轮 Hubble 源码 warning。
- Hubble package：exit 0，126.73s；distribution audit 423 JAR / 275 license / 43 FE license / 10 native-bearing。
- CI 等价 sidecar audit：SHA-512、临时 GPG detached signature、`--require-sidecars` 均 exit 0。
- PR #4 在 review checkpoint `03be1bd7`：部分轻量 job 已通过，Hubble 等 job 因 GitHub quota 仍 pending；最终完成以届时实际 PR head 的真实 CI 为准。

## 阻塞与决策

- server-core 发布 POM 阻塞已用 Hubble 最小排除解除；精确 Server package、Hubble package 与完整本地 acceptance 已证明运行兼容，仅最终 HEAD 真实 CI 待补。
- DEC-FE-01/02 已获用户批准：本轮精确 pin React/ReactDOM 18.2.0、恢复 `CI=true`，并以限定来源/数量接受 52 条第三方 source-map warning。
- Antd/X6/Graphin/G6/Dagre 等核心可视化栈现代化已登记为未来任务，明确不属于当前 task/goal；稳定版本冻结后再积极迁移和重构。
- 当前唯一构建执行器为 Yarn；删除无消费路径且与精确 pin 冲突的 `package-lock.json`。既有 peer-dependency、Browserslist 与 bundle 债务纳入未来现代化任务，本轮不补包、不升级核心图组件。
- React/ReactDOM 18.2 稳定批次已完成：lint 0、Jest 148/148 且控制台 warning 归零、`CI=true` build 通过、浏览器登录页正常、Maven package/distribution audit 通过。
- 精确 PR #3008 Server SHA 隔离构建通过；完整 runtime/UI acceptance 在一次环境生命周期恢复后通过，未发现 server-core 运行兼容错误。
- Server cache 本地 before/after 已收口：165.26s cold vs 0.40s hit 中位数，约节省 99.76%，未削弱 SHA 校验或 miss 源码构建。
- 合并重复 FE build/release scan 属门禁编排重构（DEC-CI-02），未实施。

## 下一动作

1. 冻结实现后执行独立 review，修复 actionable findings 并 re-review。
2. 用 reflection candidate-only 模式收口 lessons 与最终 progress。
3. GitHub quota 恢复后补最终 HEAD Hubble CI 与 cache 交叉验证；不作为本地推进或 review blocker。

## 恢复入口

续跑时先读取 `plan.md`、`todo.md`、`progress.md`，核对当前 HEAD/工作树是否变化，然后从 `todo.md` 第一个未完成项继续；不要创建重复目标或重新打开 hardening TODO。
