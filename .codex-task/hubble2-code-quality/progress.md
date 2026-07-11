# Hubble2 Code Quality Progress

## 当前 checkpoint

- 更新时间：2026-07-11 23:20 +08:00
- 当前阶段：Phase 3/5 收口；等待 FE 第三方 warning 决策
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
- Maven FE package 仍显式 `CI=false`；临时 `CI=true` package/audit 已通过，但 warning 不会自动变成失败。恢复真实配置及 pin React 待 DEC-FE-01。
- 52 条 source-map warning 已证明无法由兼容世代的最小升级清除，待 DEC-FE-02 明确接受或另行授权高风险方案。
- 合并重复 FE build/release scan 属门禁编排重构（DEC-CI-02），未实施。

## 下一动作

1. 监控 `66e36594` 的真实 Hubble CI，保存 cache miss、package/audit/API 证据。
2. 根据 DEC-FE-01 决策恢复 `CI=true` production build，并完成 FE/package 回归。
3. 获取 3 次 warm-cache CI 中位数，冻结实现后执行独立 review/re-review。

## 恢复入口

续跑时先读取 `plan.md`、`todo.md`、`progress.md`，核对当前 HEAD/工作树是否变化，然后从 `todo.md` 第一个未完成项继续；不要创建重复目标或重新打开 hardening TODO。
