# 阶段 4：实习信息管理 + 求职成长引擎 · 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 求职主战场能力：① 粘贴实习 JD → 结构化入库（公司/岗位/城市/薪资/截止/JD原文/链接），可查、可删；② 求职档案（技能/目标岗位/城市/年级）单行配置；③ 每日学习打卡入库；④ 三个按需能力：匹配度分析、根据 JD 生成学习计划、每周复盘（均流式输出）。

**Architecture:** 沿用既有模式三件套（新表 + 实体/Mapper + 独立抽取 record + engine 分支）。`internships`/`profile`(单行)/`study_progress` 三张新表；`ExtractedInternship`/`ExtractedProfile`/`ExtractedStudyLog` 三个新 record；CLASSIFY 增加 match_analysis/study_plan/weekly_review 三个意图走流式回答。v1 不做：定时搜集、任务清单式计划表（study_plan 表后置）、状态标记、JD 去重。

**Tech Stack:** 不变。

## 执行纪律

- 概念先行 + 费曼复述；代码为导师参考实现；TDD 红→绿→提交→笔记。
- 本阶段学员已写过 5 个同模式 record——Task 3/4/5 的三个新 record **由学员仿写，导师核对**，不再给完整代码。

## 文件结构总览

```
修改/新增:
├─ src/main/resources/schema.sql                +internships/profile/study_progress
├─ src/main/java/com/campus/agent/store/Database.java    SCHEMA 同步
├─ src/main/java/com/campus/agent/store/entity/  Internship.java/Profile.java/StudyProgress.java   新
├─ src/main/java/com/campus/agent/store/mapper/  InternshipMapper/ProfileMapper/StudyProgressMapper 新
├─ src/main/java/com/campus/agent/store/ItemRepository.java    +实习/档案/打卡读写、findInternship/deleteInternship
├─ src/main/java/com/campus/agent/store/InternshipItem.java / ProfileItem.java / StudyLogItem.java   新读模型
├─ src/main/java/com/campus/agent/model/ExtractedInternship.java / ExtractedProfile.java / ExtractedStudyLog.java   新
├─ src/main/java/com/campus/agent/model/ExtractedItem.java    VALID_TYPES +3
├─ src/main/java/com/campus/agent/Prompts.java    EXTRACT +3 类型；CLASSIFY +3 意图；新增 3 个求职提示词
├─ src/main/java/com/campus/agent/service/ExtractionEngine.java     +3 分支
├─ src/main/java/com/campus/agent/service/AssistantService.java     +3 意图处理（流式）、删除支持 internship
├─ src/main/java/com/campus/agent/web/ChatController.java    +GET /api/internships
├─ src/main/resources/static/index.html      +实习列表面板
测试: 各层对应新增；既有 64 测试保持绿
```

---

## Task 1: 概念启蒙（无代码）

- [ ] **Step 1: 概念清单（学员复述过关）**
  - **求职域数据建模**：JD 要拆哪些字段（company/position/city/salary/deadline/jd/link）——拆得越结构化，后面"按城市筛""按截止排序"越省事
  - **"什么存表、什么只生成文本"的边界**：会反复查询/需要状态流转的（实习、打卡）→ 存表；一次性生成品（匹配度报告、学习路线文本、周复盘）→ 只存 messages 聊天记录。**这是本阶段最重要的设计判断**
  - **单行档案 profile**：同 semester 的"单行配置表"模式（id=1 + INSERT OR REPLACE）
  - **打卡 vs 任务清单**：用户选了打卡式（轻量、像日记）——任务清单体系（study_plan 表）后置，等打卡数据积累出真实需求再加（YAGNI）
  - **状态字段预留**：internships.status 先只有默认值 new，标记"已投/收藏"留到小修阶段——建表时留列比后加列便宜（阶段 2.5 的教训）

---

## Task 2: 三张新表 + 读写（TDD）

**Files:** schema.sql、Database.java、entity×3、mapper×3、ItemRepository、读模型×3、store/InternshipRepoTest（或并入 ItemRepositoryTest 增补）

- [ ] **Step 1: schema**（三个 CREATE 追加；courses 的教训：新表用 CREATE IF NOT EXISTS 即可，老库无需 ALTER）

```sql
CREATE TABLE IF NOT EXISTS internships(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  company TEXT NOT NULL,
  position TEXT NOT NULL,
  city TEXT,
  salary TEXT,
  deadline TEXT,
  jd TEXT,
  link TEXT,
  status TEXT NOT NULL DEFAULT 'new',
  source_message_id INTEGER,
  created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')));
CREATE TABLE IF NOT EXISTS profile(
  id INTEGER PRIMARY KEY,
  skills TEXT,
  target_role TEXT,
  target_city TEXT,
  grade TEXT,
  note TEXT,
  updated_at TEXT NOT NULL DEFAULT (datetime('now','localtime')));
CREATE TABLE IF NOT EXISTS study_progress(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  study_date TEXT NOT NULL,
  content TEXT NOT NULL,
  source_message_id INTEGER,
  created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')));
```

- [ ] **Step 2: 写失败测试**（SpringBootTest 环境，@DynamicPropertySource 临时库）

```java
@Test
void internshipRoundTrip() {
    long id = repo.insertInternship("字节跳动", "Java后端实习生", "北京", "200-300/天",
            "2026-09-15", "负责XX系统开发，要求熟悉Spring Boot…", "https://example.com/jd/1", 1);
    assertTrue(id > 0);
    List<InternshipItem> all = repo.allInternships();
    assertEquals(1, all.size());
    assertEquals("字节跳动", all.get(0).company());
    assertEquals("new", all.get(0).status());
    assertTrue(repo.findInternship(id).isPresent());
    assertTrue(repo.deleteInternship(id));
    assertTrue(repo.allInternships().isEmpty());
}

@Test
void profileUpsertSingleRow() {
    repo.setProfile(new ExtractedProfile("Java,SQL,Spring Boot", "Java后端实习生", "杭州", "大三", null));
    ProfileItem p = repo.profile().orElseThrow();
    assertEquals("Java后端实习生", p.targetRole());
    repo.setProfile(new ExtractedProfile("Java,SQL", "Java开发", "上海", "大三", null));
    assertEquals(1, repo.profileCountForTest(), "应始终只有一行");
    assertEquals("上海", repo.profile().orElseThrow().targetCity());
}

@Test
void studyLogRangeQuery() {
    LocalDate today = LocalDate.of(2026, 8, 31);
    repo.insertStudyLog(today.minusDays(1), "学了集合框架", 1);
    repo.insertStudyLog(today, "学了MyBatis-Plus", 2);
    repo.insertStudyLog(today.minusDays(10), "学了反射", 3);
    List<StudyLogItem> week = repo.studyLogsBetween(today.minusDays(7), today);
    assertEquals(2, week.size());
}
```

Run → 红。

- [ ] **Step 3: 实现**（实体三个 + Mapper 三个 + 读模型三个 + repo 方法：insertInternship/allInternships/findInternship/deleteInternship/setProfile(INSERT OR REPLACE 同 semester 模式)/profile/insertStudyLog/studyLogsBetween；`profileCountForTest` 若测试需要可换成公开的 `profileCount()` 或去掉该断言改为"查询后验证单行语义"——导师与学员商量）

- [ ] **Step 4: 验证 + Commit**：新测试绿 → 全量绿。Commit：`feat: internships, profile and study_progress tables with repository methods`

---

## Task 3: internship 摄入 + 删除支持

**Files:** ExtractedInternship.java、ExtractedItem.java（VALID_TYPES +"internship"）、Prompts.java（EXTRACT）、ExtractionEngine.java（分支）、AssistantService.java（DELETABLE_TYPES + delete 分支）、测试

- [ ] **Step 1: 讲概念**：JD 是一段长文本，抽取时 jd 字段存**原文**（提问/分析时用），其余字段是**索引**（列表/筛选用）——"原文+结构化索引"双轨，与 raw_inbox 的兜底思想同源。

- [ ] **Step 2: 学员仿写 ExtractedInternship**（照 ExtractedSemesterSetting 模式；字段 company/position/city/salary/deadline/jd/link；validate：company 与 position 必填，deadline 若存在须 yyyy-MM-dd，其余可空。导师核对）

- [ ] **Step 3: Prompts.EXTRACT 增补**

```
- type 取值增加 internship(实习/招聘信息：如岗位 JD)
- company: 公司名（仅 internship 用，必填）
- position: 岗位名（仅 internship 用，必填）
- city: 工作城市（仅 internship 用）
- salary: 薪资（仅 internship 用）
- deadline: 投递截止日期 yyyy-MM-dd（仅 internship 用）
- jd: 岗位要求原文（仅 internship 用，尽量完整保留）
- link: 招聘链接（仅 internship 用）
```

- [ ] **Step 4: engine 分支**（照 course_override 模式：item.type()=="internship" → ExtractedInternship.fromJson+validate → repo.insertInternship → Outcome ok，"实习：字节跳动 Java后端实习生"）

- [ ] **Step 5: 删除支持**：`DELETABLE_TYPES` 增加 "internship"；delete() 里对 internship 走 `repo.findInternship(id)`（回执"已删除：实习 字节跳动-Java后端实习生"）+ `repo.deleteInternship(id)`。

- [ ] **Step 6: 测试**：ExtractionEngineTest 增补 internship 分支；AssistantServiceTest 增补：粘贴 JD → ✅ 回执 + repo.allInternships 有 1 条；"删除实习第1条" → 🗑️ 回执 + 列表空。

- [ ] **Step 7: 验证 + Commit**：全量绿。Commit：`feat: internship extraction type with delete support`

---

## Task 4: profile_setting 摄入（求职档案）

**Files:** ExtractedProfile.java、ExtractedItem.java（VALID_TYPES +"profile_setting"）、Prompts.java、ExtractionEngine.java、测试

- [ ] **Step 1: 讲概念**：档案为什么也走"聊天设置"（同 semester）——它是"关于你的事实"，用嘴说最自然："我的技能是Java和SQL，目标岗位Java后端实习生，想在杭州实习，大三"。

- [ ] **Step 2: 学员仿写 ExtractedProfile**（字段 skills/targetRole/targetCity/grade/note；validate：至少一个非空即可，不强求全填——档案可以分多次补充？v1 简单化：无必填，全空则失败进 raw_inbox。导师核对）

- [ ] **Step 3: Prompts.EXTRACT 增补**

```
- type 取值增加 profile_setting(设置求职档案：技能/目标岗位/城市/年级)
- skills: 技能列表（仅 profile_setting 用，如"Java、SQL、Spring Boot"）
- targetRole: 目标岗位（仅 profile_setting 用）
- targetCity: 目标城市（仅 profile_setting 用）
- grade: 年级（仅 profile_setting 用）
```

- [ ] **Step 4: engine 分支**：→ ExtractedProfile.fromJson+validate → repo.setProfile（单行覆盖）→ Outcome ok，"求职档案已更新"。

- [ ] **Step 5: 测试 + Commit**：engine/服务测试（设置档案 → repo.profile() 有值且单行）。Commit：`feat: profile_setting extraction type with single-row upsert`

---

## Task 5: study_log 摄入（每日打卡）

**Files:** ExtractedStudyLog.java、ExtractedItem.java（VALID_TYPES +"study_log"）、Prompts.java、ExtractionEngine.java、测试

- [ ] **Step 1: 讲概念**：打卡日期**默认今天**（"今天学了XX"没说日期 → study_date=今天；说"昨天学了XX"→ LLM 换算成日期）。日期默认值是产品友好性，不是技术技巧。

- [ ] **Step 2: 学员仿写 ExtractedStudyLog**（字段 studyDate/content；validate：content 必填；studyDate 若空→允许，engine 分支里用 LocalDate.now() 兜底；若有值须 yyyy-MM-dd。导师核对）

- [ ] **Step 3: Prompts.EXTRACT 增补**

```
- type 取值增加 study_log(学习打卡：如"今天学了集合框架一小时")
- studyDate: 学习日期 yyyy-MM-dd（仅 study_log 用；"今天/昨天"换算成日期；没提日期留空，系统按今天记）
- content: 学习内容（仅 study_log 用，必填）
```

- [ ] **Step 4: engine 分支**：→ ExtractedStudyLog.fromJson+validate → `repo.insertStudyLog(studyDate 空 ? LocalDate.now() : parse, content, msgId)` → Outcome ok，"打卡：2026-08-31 学了集合框架"。

- [ ] **Step 5: 测试 + Commit**：含"没提日期 → 记今天"的用例（用 FakeLlm 返回 studyDate 空的 JSON，断言 study_date=今天）。Commit：`feat: study_log extraction type with today-default date`

---

## Task 6: 三个按需求职能力（流式）

**Files:** Prompts.java（CLASSIFY + 3 个新提示词）、AssistantService.java（意图分支 + 流式）、测试

- [ ] **Step 1: 讲概念**：这三个能力都是"**把对的数据喂给 LLM，让它生成**"——数据组装在服务端（哪条 JD、哪份档案、哪段打卡），生成交给模型；都走流式（输出可能很长，打字机体验）。

- [ ] **Step 2: CLASSIFY 增加意图**

```
- match_analysis：让我分析某条实习与我的匹配度（如"分析匹配度""这个岗位适合我吗"）
- study_plan：让我根据岗位 JD 制定学习计划（如"根据这个JD制定学习计划"）
- weekly_review：复盘/总结我的学习（如"复盘这周""本周总结"）
```

- [ ] **Step 3: 三个提示词**（Prompts 新增；`{data}` 由服务端替换）

```java
public static final String MATCH_ANALYSIS = """
        你是求职顾问。下面是用户的求职档案和一条实习 JD。请输出：
        1) 匹配度评分（0-100）与一句话结论
        2) 已满足的要求（列表）
        3) 差距清单（列表，每条附一句补课建议）
        4) 接下来 2 周最该做的 3 件事
        用中文，简洁。档案：{profile}。JD：{jd}
        """;

public static final String STUDY_PLAN = """
        你是求职导师。根据 JD 要求与用户技能现状，制定一份 4 周学习计划：
        - 按周拆分（第1周/第2周/第3周/第4周），每周 3-5 个可执行任务
        - 任务要具体到"学什么、做什么练习"，不要空话
        - 优先补差距最大的技能
        - 每天预计投入 2 小时
        用中文。档案：{profile}。JD：{jd}
        """;

public static final String WEEKLY_REVIEW = """
        你是学习教练。下面是用户本周的学习打卡记录。请输出：
        1) 本周总结（学了什么、总量感）
        2) 亮点与不足
        3) 下周建议（具体任务）
        用中文，简洁。打卡记录：{logs}
        """;
```

- [ ] **Step 4: 服务端组装**（handleStreaming 里，intent 为三者时走专门的"数据组装 + chatStream"路径，与 question 共用流式出口）

```java
// match_analysis / study_plan：取最近一条实习 + 档案
Optional<InternshipItem> latest = repo.allInternships().stream()
        .max(Comparator.comparing(i -> i.id()));
if (latest.isEmpty()) return "还没有实习记录，先粘贴一条 JD 吧。";
String profileJson = repo.profile().map(ProfileItem::toMap)
        .map(this::toJson).orElse("（未设置求职档案，建议先说“我的技能是…”设置）");
// study_plan → Prompts.STUDY_PLAN；match_analysis → Prompts.MATCH_ANALYSIS（替换 {profile}/{jd} 占位）
// weekly_review：repo.studyLogsBetween(today-7, today) → JSON 数组（空则提示"本周还没有打卡"）
```

（三个意图的完整回复同样存 messages；流式回调与 question 一致。）

- [ ] **Step 5: 测试**：FakeLlm + 流式断言——①match_analysis：先插档案+实习，FakeLlm 返回流式分片，断言 handleStreaming 收到分片且 prompt（lastUserPrompt）包含公司名与技能；②weekly_review 无打卡 → 提示文案；③study_plan 无实习 → 提示文案。

- [ ] **Step 6: 验证 + Commit**：全量绿。Commit：`feat: match analysis, study plan and weekly review intents with streaming`

---

## Task 7: 实习列表面板 + 端点

**Files:** ChatController.java（+GET /api/internships）、static/index.html、web 测试

- [ ] **Step 1: 端点**

```java
@GetMapping("/internships")
public List<Map<String, Object>> internships() {
    return repo.allInternships().stream().map(InternshipItem::toMap).toList();
}
```

- [ ] **Step 2: 前端**：早报卡片下方加"💼 实习列表"折叠区：打开页面时 fetch `/api/internships`（带 token），渲染 `#序号 公司-岗位 | 城市 | 截止 | 状态`；供用户按编号提问/删除。

- [ ] **Step 3: 测试 + Commit**：MockMvc GET /api/internships 断言 JSON 数组。Commit：`feat: internships endpoint and panel section`

---

## Task 8: 阶段 4 端到端验收 + 文档（压轴）

- [ ] **Step 1: 全链路走查**（真实 LLM，浏览器）
  1. "我的技能是Java、SQL，目标岗位Java后端实习生，想在杭州，大三" → 档案回执
  2. 粘贴一条真实 Java 实习 JD → ✅ 回执 → 实习列表出现
  3. "分析匹配度" → 流式输出评分/差距/建议
  4. "根据这个JD制定学习计划" → 流式输出 4 周计划
  5. "今天学了MyBatis-Plus" → 打卡回执（日期=今天）；再补两条 → "复盘这周" → 流式周复盘
  6. "删除实习第1条" → 回执 + 列表消失
  7. 与既有功能回归：课表/作业/早报不受影响；`mvn test` 全量绿
- [ ] **Step 2: 文档**：设计文档更新（数据模型 +internships/profile/study_progress 细节，study_plan 标注后置；7 节补求职域流程；路线图阶段 4 行改为"粘贴 JD 为主"）；新增阶段 4 笔记（存表 vs 生成文本的边界、原文+索引双轨、日期默认值、流式长输出）；更新交接文档。提交：`docs: phase 4 notes and spec update`
- [ ] **Step 3: 回 v4-pro**：汇报验收，讨论下一步（3.5 OCR / 4.5 小修（状态标记、JD 去重）/ 阶段 5 可选演进）。

## 阶段 4 完成验收清单（Task 8 统一核对）

- [ ] `mvn test` 全绿
- [ ] JD 粘贴入库、实习列表可见、可删除
- [ ] 档案设置后，匹配度分析引用正确档案与最新 JD
- [ ] 学习计划按 JD 差距生成，流式输出
- [ ] 打卡默认今天；周复盘汇总 7 天内打卡；无打卡有提示
- [ ] 学员能复述：什么存表什么只生成文本；"原文+结构化索引"双轨；为什么状态列建表时就留；三个求职意图的数据组装各喂了什么
