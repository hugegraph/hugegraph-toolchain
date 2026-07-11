# Hubble2 Code Quality Progress

## 当前 checkpoint

- 更新时间：2026-07-12 02:00 +08:00
- 当前阶段：Phase 6B review remediation 最终门禁
- 当前分支：`hubble2`
- 目标状态：active
- 唯一实时状态源：[`todo.md`](todo.md)
- 执行合同：[`plan.md`](plan.md)
- 完整本地门禁覆盖的产品实现 SHA：`10c0e8fa4f18694f8f848212ed1da52ef36d80a4`
- 独立整体 review checkpoint：`03be1bd7b9e2cbdbac90499821bbf4ce147124ac`
- 最新已推送代码 checkpoint：`e76544c70055f7fcb97c1c499f7ceee463e77696`
- 最终候选/真实 CI SHA：`a56fb6c1ebb27c717568bf1e0baba1cb94afa3c0`
- 当前工作树：旧 review remediation 的 FE/BE/CI/测试和 SOT 修改，尚未提交；无用户未确认的 API/依赖升级混入

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

- 2026-07-12 remediation 稳定工作树：JDK 11 Hubble BE unit 133/133，checkstyle 0，JaCoCo 无 class mismatch，18.14s。
- FE full Jest 41 suites / 153 tests，lint 0 warning，i18n zh/en 各 1584；smoke Python regression 5/5；actionlint 1.7.12 和 `git diff --check` 通过。
- `e76544c7` 上一阶段真实 PR checks 全部通过：Hubble、Client、Loader、Tools、Spark、Go、license header/dependency、triage。

- FE `yarn lint`：exit 0，4.94s，330 files 0 warning/error；Jest：40 suites / 148 tests，exit 0，6.50s，console warning 0。
- FE `CI=true yarn build`：exit 0，47.47s；仅保留已接受的 52 条第三方 source-map warning，以及已转未来任务的 Browserslist/bundle 提示。
- BE clean compile：成功，265 source files；本项目 unchecked/deprecation warning 为 0，剩余输出为外部发布 POM/仓库元数据 warning。
- BE clean unit：121 tests，0 failure/error/skip，exit 0，31.40s；checkstyle 0，JaCoCo 分析 407 classes。
- Hubble checkstyle info 级门禁已证明临时违规 exit 1、恢复后 exit 0；全仓 15 条既有 debt 不扩入本目标。
- Client/Loader linked install：exit 0，49.26s；shade 的历史依赖重复 warning 不属于本轮 Hubble 源码 warning。
- Hubble package：exit 0，126.73s；distribution audit 423 JAR / 275 license / 43 FE license / 10 native-bearing。
- CI 等价 sidecar audit：SHA-512、临时 GPG detached signature、`--require-sidecars` 均 exit 0。
- PR #4 最终候选 `a56fb6c1`：全部 checks 通过。Hubble 11m12s；dependency license 12m28s；Client 12m13s；Loader 6m26s；Tools 4m57s；Spark 4m59s；Go 4m48s。
- Hubble 真实 CI：Compile 3m23s、Prepare 4m13s、release audit 1m、unit 28s、API 30s；Server SHA `3bd990d8...` cache 2.7s 命中，121 tests 与 runtime/UI acceptance 全部通过。

## 阻塞与决策

- 历史 review 已在 `e76544c7` 完整复核：清图/认证/API/readiness/algorithm/artifact findings 仍真实并已按用户 D1=A、D2=B、D3=A、D4=A、D5=A 实施；TrueLicense、Server tree 不一致和 legacy 残留结论已证明过期。
- 当前清图只提供诚实的 Schema+数据操作；data-only 对用户有高价值，FE/BE TODO 与 FUTURE-SERVER-01 明确要求 Server 支持后第一时间恢复。
- Server CI 已固定 master 合入 commit `99936be5...`；其 tree 与此前验证的 PR head 相同。

- server-core 发布 POM 阻塞已用 Hubble 最小排除解除；精确 Server package、Hubble package 与完整本地 acceptance 已证明运行兼容，仅最终 HEAD 真实 CI 待补。
- DEC-FE-01/02 已获用户批准：本轮精确 pin React/ReactDOM 18.2.0、恢复 `CI=true`，并以限定来源/数量接受 52 条第三方 source-map warning。
- Antd/X6/Graphin/G6/Dagre 等核心可视化栈现代化已登记为未来任务，明确不属于当前 task/goal；稳定版本冻结后再积极迁移和重构。
- 当前唯一构建执行器为 Yarn；删除无消费路径且与精确 pin 冲突的 `package-lock.json`。既有 peer-dependency、Browserslist 与 bundle 债务纳入未来现代化任务，本轮不补包、不升级核心图组件。
- React/ReactDOM 18.2 稳定批次已完成：lint 0、Jest 148/148 且控制台 warning 归零、`CI=true` build 通过、浏览器登录页正常、Maven package/distribution audit 通过。
- 精确 PR #3008 Server SHA 隔离构建通过；完整 runtime/UI acceptance 在一次环境生命周期恢复后通过，未发现 server-core 运行兼容错误。
- Server cache 本地 before/after 已收口：165.26s cold vs 0.40s hit 中位数，约节省 99.76%，未削弱 SHA 校验或 miss 源码构建。
- 合并重复 FE build/release scan 属门禁编排重构（DEC-CI-02），未实施。
- `check-dependency-license` 失败根因为受控 third-party inventory 落后于当前 runtime snapshot；使用仓库生成器得到 516 项并更新（31 add/2 remove），同一检查脚本本地 exit 0。该提交未改依赖声明或版本。
- 独立 reviewer `/root/final_reviewer` 完整覆盖 `b66848d..03be1bd7`；唯一 Important 为 SOT SHA 语义，修复后 re-review resolved。后续依赖清单提交亦 re-review，无新增 finding。
- 最终绿色 CI 有两类非失败 annotation 待 DEC-CI-03：GitHub Actions Node 20 runtime 弃用，以及 SkyWalking Eyes 内部 setup-go cache path restore。消除前者需多项 action major 升级；后者需第三方 pin/替换/fork 调查，均不得未经批准实施。

## 下一动作

1. 运行 Client/Loader 联动、Hubble production build/package/distribution audit 与必要 live acceptance。
2. 提交并推送代码阶段，等待新 HEAD 真实 CI；quota 排队期间继续证据整理。
3. 冻结最终 diff，创建未参与实现的独立 reviewer；修复后 re-review，再收口 progress/lessons。

## 恢复入口

续跑时先读取 `plan.md`、`todo.md`、`progress.md`，核对当前 HEAD/工作树是否变化，然后从 `todo.md` 第一个未完成项继续；不要创建重复目标或重新打开 hardening TODO。
