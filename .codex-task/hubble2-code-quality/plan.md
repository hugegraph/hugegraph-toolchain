# Hubble2 Code Quality Implementation Plan

> **For agentic workers:** Execute coherent dependent tasks in the current session. Use parallel agents only for genuinely independent workstreams. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在保持 Hubble 现有业务语义、公开 API 和用户行为稳定的前提下，清理明确的前后端质量问题，定位并改善构建/CI 慢点，修复 server-core 升级故障，并为核心 API 补齐最小有效测试。

**Architecture:** 工作分为基线、低风险质量修复、CI/构建性能、server-core 兼容性、核心 API 测试与最终门禁六个阶段。`todo.md` 是唯一实时状态源，`progress.md` 保存跨会话 checkpoint，日期 evidence 保存命令、耗时、日志与 CI URL，`lessons.md` 只保存结束反思和待审批规则候选。

**Tech Stack:** Java 11、Maven/mvnd、Spring Boot、JUnit、React 18、TypeScript/JavaScript、ESLint、Jest、Yarn 1、GitHub Actions。

## Global Constraints

- 仅处理 Hubble 及为验证所必需的 Client/Loader、构建和 CI 文件；不修改 Server/PD 仓库。
- 保留当前工作树已有改动；开始实施前按文件归属记录基线，不覆盖、回退或混入无关修改。
- lint/code-style 修复优先采用不改变控制流、数据流、公开 API 和运行语义的最小改动。
- 禁止用 `CI=false`、禁用规则、跳过必需测试、弱化断言、忽略退出码或伪造响应获得绿色结果。
- 依赖升级、公开 API/序列化契约变化、CI 门禁删除或大范围工作流重构必须先记录方案、收益、风险和回滚方式，并暂停等待用户批准。
- 所有生产代码、测试逻辑、构建或 CI 改动在最终完成前必须由未参与实现的独立只读 reviewer 审查；修复 finding 后复审受影响范围。

---

## 文件与状态职责

- `.codex-task/hubble2-code-quality/plan.md`：范围、阶段、验证层级、性能方法、风险与恢复规则。
- `.codex-task/hubble2-code-quality/todo.md`：唯一实时状态源；只保存可检查事项、状态和 evidence 链接。
- `.codex-task/hubble2-code-quality/progress.md`：当前阶段、工作树状态、最近验证、阻塞、恢复入口和下一动作。
- `.codex-task/hubble2-code-quality/lessons.md`：结束时通过 `reflection` skill 生成的证据化经验和待审批推广候选。
- `.codex-task/hubble2-code-quality/evidence/YYYY-MM-DD-*.md`：环境、SHA、精确命令、退出码、分段耗时、日志摘要、CI run URL 和 before/after 对比。
- `.codex-task/hubble2-hardening/`：只读历史基线；不得重新启用其已完成 TODO。

## 复用的执行加速合同

1. **三层验证**
   - 批内反馈：只运行受影响的 Java test、JS/JSX ESLint、Jest 和 i18n。
   - 阶段门禁：一批改动冻结后运行完整 Hubble BE tests、完整 FE Jest/lint/i18n 与 `CI=true yarn build`。
   - 最终门禁：最终 diff 冻结后运行 Client/Loader 联动、Hubble package/audit、真实 CI 与独立 review；仅在用户可见行为变化时增加真实发布包 Chrome 验证。
2. **Daemon 与环境固定**
   - 本地固定 Java 11；fresh audit 已确认当前 `mvnd` 2.0.0-rc-3 agent 需要 Java 17，不能在 Java 11 启动，因此当前统一使用 Maven 3.9.11，避免混用 launcher/daemon 产生伪差异。
   - 仅在 launcher、agent 与 Java 11 兼容性重新验证后才恢复 `mvnd`；远程 CI 保持仓库原生命令语义。
3. **按共享验证面批处理**
   - 先盘点并按根因、目录或测试面归组，再集中修复；一批的上限是失败后仍能快速定位到具体文件或调用点。
   - 公共契约变化、难定位回归或风险升级时立即拆批，单独执行 RED/GREEN。
4. **慢任务并行**
   - 预计超过两分钟的 build/test/CI 等待期间，如有有意义且不冲突的工作，启动只读盘点、日志分析、证据整理或文件所有权隔离的独立 lane；禁止制造忙碌任务或让并行实现者编辑同一文件。
5. **失败与连续性**
   - 首次 CI/test/network 失败不是终止条件：保存证据、分类为确定性或瞬态、有限重试并继续其他可执行项。
   - 暂停或 quota 前更新 `todo.md` 与 `progress.md`，记录未提交状态、最近成功验证、失败证据、reset time/时区和下一条恢复命令；使用非阻塞等待并恢复同一目标。

## 性能分析方法

- 在相同 commit、Java/Node/Yarn 版本、runner 类型和命令参数下采集可比较样本；每条关键路径至少记录一次 cold run 和一次 warm run，不把网络首次下载与稳定执行混为一谈。
- 对 GitHub Actions 按 checkout、toolchain setup、dependency restore/install、frontend checks、Maven compile/package、服务准备、测试和 acceptance 分段记录 wall time；同时记录 cache key、cache hit/miss、下载量和重复构建点。
- 本地分别测量 FE install/lint/Jest/build、BE targeted/full test、Client+Loader install、Hubble compile/package；记录 `time`/Maven reactor summary、峰值内存或明确资源异常。
- 优化候选按“预计节省时间、正确性风险、维护成本、回滚难度”评估。只有不削弱门禁、契约清晰且可通过 before/after 证明收益的低风险项可直接实施。
- 不用单次偶然波动宣称性能提升；至少比较相同条件 before/after，若 CI 噪声较大则保留多次 run 范围或中位数，并明确推断边界。

## 实施阶段

### Phase 0：基线与所有权

- 记录当前 branch/HEAD、工作树已有修改及文件所有权，创建首份日期 evidence。
- 盘点 Hubble FE/BE lint、compile warning、现有测试入口、Hubble CI job/step 和 server-core 失败日志。
- 建立可枚举问题清单、性能测量矩阵和风险分级；不得在基线采集前开始大批修改。

### Phase 1：server-core 依赖解析前置恢复

- 以 `hugegraph-struct:1.7.0` 发布 POM 残留 `${revision}` 的当前复现为起点，确定 Hubble 范围内不改变版本/契约的最小恢复方案。
- 恢复依赖解析后立即补跑 BE compile/test cold/warm 基线；如只能通过依赖版本变化解决，停止并登记用户决策。

### Phase 2：前后端 lint 与 code-style

- 按根因和验证面拆分为 FE JS/JSX、FE TS、BE Java、构建配置批次。
- 每批采用最小语义保持修复，运行 scoped lint/test；阶段冻结后运行完整 FE/BE 门禁。
- 对需要改变 hooks 依赖、异步时序、异常处理或序列化行为的 warning 升级为行为改动，先添加测试并单独审查。

### Phase 3：本地构建与 CI 性能

- 完成本地 cold/warm 分段基线和 Hubble CI step 级基线，定位关键路径、重复工作、无效缓存和串行等待。
- 先实施低风险优化，例如缓存键/路径纠正、同一阶段的重复安装或重复构建消除、无依赖步骤并行化；不得删除覆盖面或改变发布包门禁含义。
- 高风险候选写入 evidence 的用户决策区后挂起，不阻塞其他阶段。

### Phase 4：server-core 运行兼容验证

- 从失败 CI 的精确 head、server-core 版本和完整日志建立稳定复现；区分编译、二进制/API 兼容、运行环境和测试 fixture 问题。
- 先写或定位能复现故障的最小验证，再实施兼容修复；验证 Hubble BE、必要 Client/Loader 联动与真实 Hubble CI。
- 若修复要求 Server/PD 变更，只记录准确调用点、外部依赖和解除条件，不越界修改其仓库。

### Phase 5：核心 API 最小有效测试

- 从 controller/service 调用图、现有测试和用户高频路径建立覆盖矩阵，优先认证/会话、GraphSpace/Graph、Schema、Gremlin、Datasource/Loader/Task 等核心读写链路。
- 每个入选路径至少覆盖一个关键成功合同和一个最重要失败/权限/边界合同；优先 service/controller 单元或窄集成测试，不复制已有 E2E 覆盖。
- 测试必须在修复前对目标缺口产生可解释失败，修复后稳定通过；不追求覆盖率数字或外围 CRUD 大而全。

### Phase 6：最终门禁与独立审查

- 冻结最终 diff，运行与改动风险匹配的完整前后端、联动、package/audit 和真实 CI 门禁，并将命令、退出码、耗时与 URL 写入 evidence。
- 由未参与实现的独立只读 reviewer 对最终 diff、目标覆盖、性能证据、兼容性、测试有效性、回归和遗漏进行审查。
- 修复全部 actionable findings；同一 reviewer 或另一独立 reviewer 复审受影响改动。无未解决高严重度 finding 后才可完成。
- 使用 `reflection` skill 的 candidate-only 模式更新 `lessons.md`；推广到 AGENTS.md 或 Memory 必须另获用户明确授权。

## 完成标准

- `todo.md` 全部范围内事项有可复核 evidence，或由用户明确接受为不实施的高风险候选。
- FE lint/Jest/i18n/`CI=true` build、BE compile/tests、必要 Client/Loader 联动和 Hubble package/audit 均真实通过。
- server-core 升级故障有稳定复现、根因、修复和真实 CI 证据。
- 性能报告包含相同条件 before/after、关键路径归因和未实施候选，不削弱任何必需门禁。
- 核心 API 覆盖矩阵与新增最小测试均有成功/失败合同证据。
- 独立 review/re-review 完成，无未解决高严重度 finding；`progress.md` 与 `lessons.md` 已收口。

## 停止条件

- 需求或稳定设计冲突，或继续会覆盖用户已有工作树修改。
- 需要改变公开 API、业务语义、数据兼容性、依赖版本或删除 CI 必需门禁。
- 经有限恢复后，必需外部环境仍不可用并阻止所有有意义的剩余工作。
- 无法创建独立只读 reviewer。
