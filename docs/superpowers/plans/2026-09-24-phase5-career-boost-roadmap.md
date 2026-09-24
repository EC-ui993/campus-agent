# 能力补强系列计划（阶段 5~8 + 并行档）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
> 依据：`docs\interview\能力补强路线.md`（15 份 JD 频率统计）。本系列多数是新栈学习，执行时**以官方教程 + flash 导师讲解为主**，不追求逐行手写（与 1~4 阶段"项目内编码"节奏不同）。

**总目标**：4 周内补齐 JD 高频差距——Docker 部署（7/15）、AI 工具沉淀（6/15）、MySQL（5/15）、Python（4/15）、Agent 工具调用（6/15）、前端 Vue（6/15）。

**执行顺序**：阶段 5 → 6 → 7 → 8 串行；并行档 E 贯穿全程。每阶段完成回 v4-pro 汇报后再进下一阶段。

---

# 阶段 5（A 档）：部署与工具（Task 1~4，约 3 天）

## Task 1: AI 编程工具工作流切换（A1）

- [ ] 安装 Cursor 与 Claude Code（二者选一为主，建议 Cursor 上手快）
- [ ] 在本项目根目录建 `.cursorrules`（或 CLAUDE.md）：写清项目约定——"Java 17、先懂再写、TDD、Conventional Commits、不提交密钥文件"
- [ ] 用 Cursor 完成本阶段 Task 2/3 的开发，全程记录：上下文怎么组织、AI 生成比例、踩过的能力边界
- [ ] 产出 `docs\interview\AI辅助开发实践笔记.md`（注意：docs/interview/ 已被 gitignore，属个人档案不入库）
- 验收：笔记成文 + 后续所有任务默认用 AI 工具执行

## Task 2: Docker 打包本项目（A2）

- [ ] 学概念：镜像/容器/Dockerfile/多阶段构建（flash 讲 + 官方 Getting Started）
- [ ] 装 Docker Desktop（Windows）
- [ ] 写 Dockerfile（多阶段：maven:3.9-eclipse-temurin-17 构建 → eclipse-temurin:17-jre 运行；`COPY target/*.jar`）
- [ ] `docker build -t campus-agent .` → `docker run -p 8080:8080 campus-agent`
- [ ] 验证：容器内 http://localhost:8080 聊天/早报可用；注意 `data/agent.db` 用 volume 挂载（`-v agent-data:/data`），数据不随容器销毁
- [ ] 记录踩坑（Windows 路径、镜像加速器）进阶段笔记
- 验收：一条 `docker run` 命令拉起完整服务，手机同 WiFi 可访问

## Task 3: 量化指标（A4）

- [ ] `cloc` 或 IDEA 统计代码行数；测试数取 `mvn test` 汇总（80+）
- [ ] 抽取准确率：取 10 条真实老师通知，人工标注期望结果 → 逐个跑抽取 → 算准确率（写"小样本 N/10"）
- [ ] 接口延迟：早报生成、/api/chat 各测 3 次取均值（curl 计时或浏览器 DevTools）
- [ ] README 增加「数据」小节（测试数/行数/准确率/延迟）
- 验收：README 有数据节，面试叙事可引用

## Task 4: GitHub 收尾（A3，若未完成）

- [ ] 按交接文档「简历化任务：GitHub 发布」步骤完成（建仓 → remote → push → Description/Topics → 复核）
- 验收：Public 仓库可访问、README 渲染正常、无密钥文件

**阶段 5 验收**：Docker 一条命令可跑 + AI 工具笔记成文 + README 数据节 + GitHub 仓库在线。

---

# 阶段 6（B 档）：MySQL 与第二语言（Task 1~3，约 1 周）

## Task 1: 本项目 SQLite → MySQL 迁移（B1a）

- [ ] 装 MySQL（本地或 Docker：`docker run mysql:8`）；学建库/用户/权限基本命令
- [ ] schema.sql 适配 MySQL：`INTEGER PRIMARY KEY AUTOINCREMENT` → `BIGINT AUTO_INCREMENT PRIMARY KEY`；`datetime('now','localtime')` → `CURRENT_TIMESTAMP`；TEXT 兼容
- [ ] pom 加 mysql-connector-j；application.yml 数据源切 MySQL；建 `agent` 库
- [ ] 全量测试适配（@DynamicPropertySource 指向临时 MySQL？——个人机用独立测试库 `agent_test`，或保留 SQLite 跑单测 + 手动验 MySQL；flash 与学员商量，推荐测试库方案）
- [ ] 练习索引与 EXPLAIN：给 assignments.due_date、courses.weekday 建索引，`EXPLAIN SELECT ...` 讲执行计划
- 验收：项目在 MySQL 上全功能跑通 + 能讲清 2 条 EXPLAIN 输出

## Task 2: Docker Compose 全家桶（B1b）

- [ ] 学概念：compose 服务编排、depends_on、健康检查、数据卷
- [ ] 写 docker-compose.yml：`app`（阶段 5 镜像）+ `mysql:8`（数据卷 + 环境变量建库）+ `nginx`（反代 80→app:8080）
- [ ] `docker compose up -d` 一键起全栈 → 浏览器 http://localhost 访问
- [ ] 产出 `docs\部署文档.md`（步骤 + 踩坑 + 架构图文字版）
- 验收：一条 `docker compose up -d` 拉起三服务，手机可访问

## Task 3: Python + FastAPI 复刻最小闭环（B2）

- [ ] 学：Python 基础语法（你有 Java 功底，对照学：venv/pip、类型注解、异步）、FastAPI、openai SDK
- [ ] 做：独立小项目 `campus-agent-py`——POST /chat 收消息 → 调 DeepSeek（openai SDK）→ 结构化抽取 → SQLite 入库 → 问答。**只做最小闭环**，不复制全部功能
- [ ] 推 GitHub（独立仓库），README 说明"Java 版主项目 + Python 复刻版"的对照关系
- 验收：Python 版跑通录入+问答，GitHub 可访问

---

# 阶段 7（C 档）：Agent 与缓存（Task 1~3，约 1 周）

## Task 1: Function Calling 原理 + 最小 Demo（C1a）

- [ ] 学概念：工具调用全流程（模型输出 function+参数 JSON → 代码执行 → 结果回填 → 模型总结）；与你自己项目的"意图路由"对比（flash 引导学员讲出异同）
- [ ] 选框架：**Spring AI**（官方 Spring 生态）或 **LangChain4j**（社区大）；二选一，跟着官方 quickstart
- [ ] 做：`agent-demo` 独立小项目——2 个工具（如"查天气""算日期"）+ 多轮对话，跑通一次完整工具调用循环
- 验收：能画出工具调用时序图（文字版），能讲"框架替你做了什么、你手写时做过什么"

## Task 2: Demo 完善 + 开源（C1b）

- [ ] 工具从 2 个扩到 3-4 个（结合真实场景：查数据库/搜索/计算），加错误处理（工具执行失败回填错误信息给模型）
- [ ] 推 GitHub，README 含架构说明与工具调用时序
- 验收：可演示 + README 完整

## Task 3: 本项目加 Redis 缓存（C2，可选）

- [ ] 学概念：缓存穿透/击穿/雪崩（能讲清即可）；装 Redis（Docker）
- [ ] 做：早报查询加缓存（key=report:yyyy-MM-dd，一天一份天然适合）；Spring Data Redis 或手写 Jedis
- 验收：第二次请求命中缓存（日志/响应头可见），三个"缓存灾难"概念能口头解释

---

# 阶段 8（D 档）：前端 Vue3 化（Task 1~4，1~2 周）

## Task 1: Vue3 基础 + 工程搭建

- [ ] 学：Vue3 组合式 API（ref/reactive/computed/生命周期）、vite 工程、组件拆分、Axios
- [ ] 在项目里建 `frontend/` 子目录（Vite + Vue3 工程），dev 模式代理 /api 到 8080
- 验收：Hello 页面 + 一次 API 调用跑通

## Task 2: 聊天页迁移

- [ ] 重写聊天界面：消息列表组件、输入框、**SSE 流式渲染**（fetch ReadableStream 解析，沿用现有协议）
- [ ] 口令输入与 localStorage 逻辑迁移
- 验收：录入/提问/纠正/删除/标记全流程在 Vue 前端可用

## Task 3: 其余面板迁移

- [ ] 早报卡片（含折叠）、记录面板、实习列表面板、课表上传按钮
- 验收：与原生版功能对齐（对照清单逐项勾）

## Task 4: 端到端验收（压轴）

- [ ] 手机同 WiFi 回归 + 窄屏适配
- [ ] 打包进 Docker 镜像（Vite build 产物放 Spring Boot static/ 或 Nginx 托管，flash 与学员商量方案）
- [ ] 全量测试回归 + 笔记归档 + 交接更新
- 验收：Vue 前端替代原生版成为默认界面，Docker 镜像含前端

---

# 并行档 E（贯穿阶段 5~8，每天 1~2 小时）

## E1: 算法与八股日常

- [ ] 每天 LeetCode 1~2 题（数组/链表/哈希优先，热题 100 顺序）
- [ ] 每天 1 小时复习轮换：JUC（线程池/锁/AQS）→ JVM → 计网（TCP/HTTP）→ 设计模式（结合 Spring 源码：单例/工厂/策略/观察者）
- [ ] 产出 `docs\interview\面试问答笔记.md`（gitignore 内，个人档案），每掌握一个主题记一条"用自己的话"的答案

## E2: SpringCloud 概念层

- [ ] Nacos / OpenFeign / Gateway 各 1 小时官方概念 + 1 个最小 demo 跑通服务注册与调用
- [ ] 目标：能讲清"服务注册与发现、声明式调用、网关路由"三句话，不深入
- 验收：面试叙事里能各用 3 句话讲清

---

## 整体验收（阶段 5~8 全部完成时）

- [ ] Docker 一条命令起全栈（app+mysql+nginx+Vue 前端）
- [ ] GitHub 至少 3 个仓库：campus-agent（Java 主项目）/ campus-agent-py（Python 复刻）/ agent-demo（工具调用）
- [ ] AI 工具实践笔记 + 面试问答笔记 + 部署文档 三份成文
- [ ] 简历三个标签就位：Java 后端 + LLM 应用集成 + AI 工具协作
- [ ] 每阶段验收后投递一批岗位（边补边投，不等全部完成）
