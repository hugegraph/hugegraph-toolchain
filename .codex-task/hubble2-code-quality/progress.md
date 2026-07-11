# Hubble2 Code Quality Progress

## 当前 checkpoint

- 更新时间：2026-07-11 23:14 +08:00
- 当前阶段：Phase 2 FE lint/warning 分类
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

## 当前工作树边界

- fresh audit 前已有 26 个 Hubble BE Java 修改（`+150/-57`），内容为 import/style 清理；视为既有未提交质量修复，保留并纳入后续 scoped verification。
- 本轮新增产品改动为 `hubble-be/pom.xml` 的单一传递依赖排除及既有 dirty style/import 修复所需的漏 import 补全；未触及业务控制流和公开 API。

## 最近验证

- FE `CI=true yarn build`：exit 0，51.49s，但存在第三方 source-map/Browserslist warning。
- FE Jest：40 suites / 148 tests 通过，6.07s，但存在本项目可控与第三方 React warning。
- BE clean compile：成功，265 source files；本项目 unchecked/deprecation warning 为 0，剩余输出为外部发布 POM/仓库元数据 warning。
- BE unit：119 tests，0 failure/error/skip；clean JaCoCo report 成功分析 265 classes。
- BE checkstyle：Hubble source diagnostics 128 -> 0；插件仍存在“报告问题但 exit 0”的全局可诊断性问题，保留 CQ-LINT-06。
- Client/Loader install：exit 0，41.04s；shade 的历史依赖重复 warning 不属于本轮 Hubble 源码 warning。

## 阻塞与决策

- server-core 发布 POM 阻塞已用 Hubble 最小排除解除；尚需 package/真实 CI 证明运行兼容。
- 当前无需要用户批准的高风险性能方案。

## 下一动作

1. 分类并清理本项目可控 FE/Jest warning，保留第三方 warning 决策边界。
2. 补齐 package/cold-warm 分段数据并分析低风险 CI 性能优化。
3. 完成核心 API 覆盖矩阵，按 RED/GREEN 增补最小合同测试。

## 恢复入口

续跑时先读取 `plan.md`、`todo.md`、`progress.md`，核对当前 HEAD/工作树是否变化，然后从 `todo.md` 第一个未完成项继续；不要创建重复目标或重新打开 hardening TODO。
