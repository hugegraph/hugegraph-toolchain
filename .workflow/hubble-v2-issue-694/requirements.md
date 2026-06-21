# 需求文档: Hubble V2 issue 694 验证与修复

## 1. 介绍

本需求文档用于规范 Apache HugeGraph Toolchain 中 GitHub issue #694
“New Graph-UI(hubble) backend module issues”的修复和验证工作。目标是在
Hubble V2 前后端、打包产物、启动脚本、GraphServer 访问和 Loader 导入链路上
建立可复现的验证闭环，并修复阻塞 Hubble 正常构建、启动或核心功能使用的轻量
问题。

本需求面向 Hubble 维护者、Reviewers 和发布验证人员。成功状态不是“本地看起来
能跑”，而是同一代码基线下可以通过明确命令构建、测试、启动、访问 UI，并能说明
哪些能力已验证、哪些仍属于 GraphServer/Loader 或未验证边界。

## 2. 需求列表

### 2.1 构建 Hubble 前后端与分发包

- **用户故事**: 作为一名 **Hubble 维护者**, 我希望 **能在当前
  toolchain 分支上构建 Hubble 后端、前端和分发包**, 以便 **确认 issue #694
  涉及的新 Graph-UI/Hubble 模块具备可验证的构建基线**。
- **验收标准 (EARS 格式)**:
  - **U1**: The **Hubble 构建流程** shall **使用仓库内 Maven 多模块依赖顺序，
    先构建 `hugegraph-client` 与 `hugegraph-loader`，再构建
    `hugegraph-hubble` 分发包**。
  - **U2**: The **Hubble 构建流程** shall **记录完整命令、当前分支、提交或
    起始基线、JDK、Maven、Node/Yarn 版本和产物名称**。
  - **E1**: WHEN **执行 Hubble package 命令**, the **构建流程** shall
    **生成包含后端、前端静态资源和启动脚本的 Hubble 分发 tarball**。
  - **X1**: IF **构建失败**, THEN the **实现记录** shall **标明首个失败模块、
    失败命令和可定位的根因，不得把失败后的本地残留产物作为成功证据**。

### 2.2 修复阻塞正常运行的 Hubble 轻量问题

- **用户故事**: 作为一名 **Hubble 使用者**, 我希望 **Hubble 分发包可以按文档
  启动并通过基本 API/UI 访问**, 以便 **正常进入新 Graph-UI 的使用路径**。
- **验收标准 (EARS 格式)**:
  - **E2**: WHEN **使用分发包中的 `bin/start-hubble.sh` 启动 Hubble**, the
    **Hubble 进程** shall **能以前台或后台模式启动，并对
    `/actuator/health` 返回可用状态**。
  - **E3**: WHEN **Hubble 以容器入口脚本启动**, the **容器进程** shall **以前台
    模式运行，避免入口命令退出导致容器生命周期异常**。
  - **E4**: WHEN **访问 Hubble 根路径 `/`**, the **Hubble 后端** shall
    **返回前端 React 应用入口并能加载打包后的静态资源**。
  - **X2**: IF **发现轻量运行缺陷阻塞启动、健康检查、前端入口或核心数据格式
    使用**, THEN the **实现** shall **优先采用局部修复并补充对应测试或脚本验证**。

### 2.3 验证前端与 Hubble 后端集成

- **用户故事**: 作为一名 **Reviewer**, 我希望 **看到 Hubble 前端页面实际请求
  Hubble 后端接口的证据**, 以便 **判断 issue #694 中“通过前端访问新图后端”
  是否被满足**。
- **验收标准 (EARS 格式)**:
  - **U3**: The **验证报告** shall **区分静态路由可访问、后端 API 可访问、浏览器
    页面实际发起网络请求这三类证据**。
  - **E5**: WHEN **访问核心前端路由**, the **验证流程** shall **覆盖图管理、
    元数据配置、数据导入、数据分析和异步任务等 issue 相关页面路径**。
  - **E6**: WHEN **浏览器加载核心前端页面**, the **验证流程** shall **记录截图或
    网络请求证据，证明页面请求到 Hubble 后端而非只命中静态资源 fallback**。
  - **X3**: IF **某个前端路径只完成静态路由 fallback 而没有后端请求证据**, THEN
    the **验证报告** shall **将其标记为“部分验证”，不得作为完整集成通过结论**。

### 2.4 验证 GraphServer 与 Loader 导入链路

- **用户故事**: 作为一名 **发布验证人员**, 我希望 **通过 Hubble 连接实际
  HugeGraph Server 并完成 Loader 相关导入 smoke**, 以便 **确认完整系统
  GraphServer + GraphLoader 在 issue #694 范围内可用**。
- **验收标准 (EARS 格式)**:
  - **E7**: WHEN **配置 Hubble 连接专用测试 HugeGraph Server**, the
    **Hubble API** shall **能创建或读取图连接，并记录 Server 基线、地址和测试图名**。
  - **E8**: WHEN **通过 Hubble 或等价验证流程创建 schema**, the **直接
    GraphServer schema API** shall **能观察到相同 schema 结果**。
  - **E9**: WHEN **执行文件上传、映射和 Loader-backed 导入流程**, the
    **验证流程** shall **记录任务状态、导入结果和最终图数据计数**。
  - **E10**: WHEN **比较 Hubble 与直接 GraphServer 查询结果**, the **验证流程**
    shall **确认 Gremlin counts 与 shortestPath 相关图视图结果在验证数据范围内一致**。
  - **X4**: IF **本地没有可用 HugeGraph Server 或 Docker/Server 无法启动**, THEN
    the **验证报告** shall **明确标注 GraphServer + Loader gate 未完成，并保留可复现
    命令，不得声明 issue #694 完全解决**。

### 2.5 明确 API 边界与已知问题

- **用户故事**: 作为一名 **Reviewer**, 我希望 **区分 Hubble 已支持能力、
  GraphServer 直连能力和未实现/未验证能力**, 以便 **避免 review 或 release note
  中出现超范围承诺**。
- **验收标准 (EARS 格式)**:
  - **U4**: The **验证报告** shall **对每个通过的能力绑定具体命令、接口、页面或
    测试证据**。
  - **U5**: The **验证报告** shall **将 Server-only 能力标记为 GraphServer 能力，
    不得描述为 Hubble UI/backend 已支持能力**。
  - **X5**: IF **Hubble 路由或 API 返回 404/405/未实现**, THEN the
    **验证报告** shall **分类为 Hubble UI/API 集成缺口、产品边界或待补 issue**。
  - **X6**: IF **存在 release-impacting 已知问题**, THEN the **验证报告** shall
    **包含 issue 链接、影响范围、规避方式和建议 release note 文案**。

### 2.6 校验 i18n、UI 资源和二进制分发内容

- **用户故事**: 作为一名 **发布验证人员**, 我希望 **Hubble 分发包不缺法律文件、
  不携带运行残留，并且 UI 文案资源可用**, 以便 **减少发布和运行风险**。
- **验收标准 (EARS 格式)**:
  - **U6**: The **Hubble 分发包** shall **包含 `bin`、`conf`、`lib`、`ui`、
    `LICENSE`、`NOTICE` 和 `licenses` 等发布必需内容**。
  - **U7**: The **Hubble 分发包** shall **不包含 `node_modules`、npm/yarn 缓存、
    运行日志、pid、H2 数据库、upload-files 等运行残留或开发缓存**。
  - **U8**: The **i18n 静态校验** shall **确认 `zh-CN` 与 `en-US` 可见文案 key
    对称且没有空值**。
  - **E11**: WHEN **切换 UI 语言或访问核心页面**, the **运行时验证** shall
    **记录无明显 raw key、文案缺失或布局重叠的证据**。
  - **X7**: IF **只能完成静态 i18n 校验而没有浏览器运行时截图**, THEN the
    **验证报告** shall **标记 UI 运行时 gate 未完全关闭**。

### 2.7 保持可追溯的规则化工作流

- **用户故事**: 作为一名 **项目协作者**, 我希望 **issue #694 的修复遵循
  hugegraph-ai rules 中的需求、设计、计划、执行阶段**, 以便 **所有实现都有明确
  需求来源、设计依据、任务拆分和执行日志**。
- **验收标准 (EARS 格式)**:
  - **U9**: The **工作流文档** shall **存放在
    `.workflow/hubble-v2-issue-694/` 下，并至少包含 `requirements.md`、
    `design.md`、`tasks.md` 和执行日志目录**。
  - **U10**: The **工作流推进** shall **在 requirements、design、tasks 每个阶段
    完成后等待用户明确批准，再进入下一阶段**。
  - **U11**: The **实现任务** shall **按 `tasks.md` 中定义的原子任务逐一执行，并在
    执行日志中记录任务、命令、结果和未完成 gate**。
  - **X8**: IF **实现过程中发现足以改变需求或设计的新事实**, THEN the
    **工作流** shall **暂停执行并回到 requirements 或 design 阶段修订文档**。
