# 校园助手 Campus Agent

一个跑在本机的私人 AI 助手：把课程表、作业、考试、实习 JD 等消息扔给它，它自动抽取成结构化数据存本地，随时回答你的问题，并每天主动汇报。从「你问它答」到「它主动找你」。

> Java 17 · Spring Boot 3 · MyBatis-Plus · SQLite · DeepSeek API · 原生 JS 前端 · 80 个自动化测试

## 功能一览

| 能力 | 说明 |
|---|---|
| 🗂️ 信息摄入 | 粘贴老师通知/作业/考试安排 → LLM 抽取为结构化 JSON → 校验重试 → 入库 → **回执确认**，说错随时可纠正（diff 回执显示"旧值 → 新值"） |
| 📚 课表问答 | 课程按周重复 + 单日临时变动（停课/调课自动恢复）；"今天有什么课"按**学期周次**过滤（支持 1-16 / 多段 / 单双周） |
| 📥 Excel 一键导入 | 网页上传课表 Excel，EasyExcel 读行 + LLM 抽取兜底，不挑列格式 |
| 📅 每日早报 | 每天 7:00 定时生成：今日课程、到期作业、临近考试、今日待办；电脑没开机时访问页面即时补生成（幂等） |
| 💼 求职引擎 | 粘贴岗位 JD → 结构化入库；求职档案；**匹配度分析**（评分+差距清单+补课建议）；**4 周学习计划**；每日打卡 + **周复盘** |
| 🔧 记录管理 | 对话式"纠正/删除"——"把作业第3条日期改成11-21"、"删除待办第5条"
| 📱 随处可用 | 电脑浏览器 + 手机同 WiFi 访问，口令保护；数据全在本机 SQLite，一个文件即可备份 |

## 架构

```
浏览器（电脑 / 手机同WiFi，原生 HTML/JS）
   │  REST + SSE（流式回复，打字机效果）
   ▼
Spring Boot 3
 ├─ ChatController / ImportController / ReportController
 ├─ AssistantService      意图路由：record/question/correct/delete
 │                         + match_analysis/study_plan/weekly_review
 ├─ ExtractionEngine      抽取引擎：LLM 抽取 → Schema 校验 → 重试 → 按类型入库
 ├─ DailyReportService    早报生成（纯模板，定时 + 懒生成兜底）
 └─ DeepSeekClient        chat / chatJson / chatStream（流式）
   │
   ▼
SQLite（HikariCP 连接池 + MyBatis-Plus）—— 13 张表，单文件 agent.db
```

## 快速开始

```bash
# 1. 环境：JDK 17+、Maven 3.9+
# 2. 准备 DeepSeek API Key（platform.deepseek.com）
# 3. 设置环境变量（或写进 config.properties，已被 gitignore）
$env:DEEPSEEK_API_KEY="sk-xxxx"

# 4. 启动
mvn spring-boot:run

# 5. 浏览器打开 http://localhost:8080，输入口令（application.yml 中 access-token）
```

然后试着说：

```
本学期从9月1日开始，共16周
高数 周一 8:00-9:40 A201，1-8周
高数作业：习题5.2，11月20日前交
今天有什么课？
```

## 设计亮点（面试可展开的点）

1. **人机协同，而非追求模型完美**：LLM 抽取必然有错，所以每层都有兜底——JSON Schema 校验 + 自动重试 → 失败原文进 `raw_inbox` 永不丢失 → 回执确认 → diff 纠正。可靠性的来源是架构，不是模型。
2. **能写规则的别指望模型自觉**：日期格式校验、课程名前缀剥离、记录序号映射等确定性逻辑全部在 Java 侧执行；few-shot 示例负责常见模式，规则负责兜底。
3. **无状态设计**：纠正/删除/标记通过「类型+编号」定位记录，不依赖"上一条"——CLI 迁 Web 时消灭了共享可变状态的并发隐患。
4. **重复事件 + 例外模型**：课程按周重复，单日变动（停课/调课）按日期作用域自然过期，零清理逻辑。
5. **定时任务的可靠性**：早报"定时生成 + 访问时懒生成兜底"双路径，幂等（一天一份），不依赖电脑恰好开机。
6. **数据模型的分寸**：会反复查询/需要状态流转的存表；一次性生成品（匹配度报告、学习计划）只存聊天记录——YAGNI 的实战。
7. **TDD 全流程**：80 个测试（单元 + MockMvc 集成 + 10 条真实消息回归集），先红后绿，重构以"测试保持绿"为证明。

## 技术栈与选型理由

| 选择 | 理由 |
|---|---|
| Java 17 / Spring Boot 3 | 企业 Java 后端事实标准；面向求职 |
| MyBatis-Plus | 国内招聘 JD 最高频 ORM；零 XML CRUD |
| SQLite（HikariCP） | 本地单用户零运维；数据源可平滑切 MySQL（演进路线） |
| EasyExcel | 国内主流 Excel 解析（阿里开源，POI 封装） |
| SSE 流式 | 长回答打字机体验；DeepSeek stream 模式 |
| 原生 HTML/JS | 前端刻意做薄，学习重心在 Java 后端 |

## 项目结构

```
src/main/java/com/campus/agent/
├─ web/          Controller + 口令 Filter
├─ service/      编排：AssistantService / ExtractionEngine / DailyReportService / ExcelReader
├─ llm/          DeepSeekClient（chat/chatJson/chatStream）
├─ model/        抽取结果 record（8 种类型 + 校验）
├─ store/        MyBatis-Plus：entity / mapper / ItemRepository / Database
└─ config/       Bean 装配 / 配置绑定
src/main/resources/
├─ static/index.html   单页聊天前端
├─ schema.sql          建表（13 张）
└─ application.yml
```

## 路线图

- [x] 阶段 1-4.6：命令行闭环 → 网页版 → 学期周次 → Excel+早报 → 求职引擎 → 抽取提准
- [ ] 状态标记与 JD 去重、图片 OCR、MySQL/Docker 演进（见 `docs/superpowers/` 计划文档）

## 许可与隐私

- 数据 100% 本地（SQLite 单文件），仅必要文本发送给模型 API
- 个人学习项目，MIT 可选
