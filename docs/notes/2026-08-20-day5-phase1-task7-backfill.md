# 第 5 天总结：理解回填 · Task 7 Prompts + AssistantService（2026-08-20）

> 今日状态：Task 7 完成并提交，全量测试 22/22 绿；已补做逐行理解回填

## 今日范围

- `src/main/java/com/campus/agent/Prompts.java`
- `src/main/java/com/campus/agent/service/AssistantService.java`
- `src/test/java/com/campus/agent/PromptsTest.java`
- `src/test/java/com/campus/agent/service/AssistantServiceTest.java`

## 今日知识点

### 1. Prompts 工具类

- `final class`：防止被继承。
- `private` 构造方法：防止外部 `new`。
- 作用：只放静态常量和方法，是一个纯工具类。
- 成员：
  - `CLASSIFY`：意图分类提示词。
  - `EXTRACT_TEMPLATE`：抽取模板，私有，因为里面有 `{today}` 占位符。
  - `extract()`：公开，把 `{today}` 替换成当天日期后返回。
  - `CORRECT`：纠错提示词。
  - `ANSWER`：问答提示词。

### 2. extract() 与 LocalDate

- `LocalDate.now()` 返回当天日期对象。
- `.toString()` 得到 `yyyy-MM-dd` 格式，例如 `2026-08-20`。
- `replace("{today}", 日期字符串)` 把模板里的占位符替换成真实日期。

### 3. AssistantService 核心成员

- `llm`：大模型客户端。
- `repo`：数据读写仓库。
- `db`：数据库连接（本阶段暂未使用，预留给阶段 2）。
- `lastInsertedId` / `lastInsertedType`：记录最近一次成功录入的 id 和 type，供纠正时定位原记录。

### 4. handle() 主流程

1. 把用户消息存入 `messages`，拿到 `messageId`。
2. 调用 `classify(input)` 得到意图。
3. 根据意图走分支：
   - `question` → `answer`
   - `correction` → `correct`
   - 其他 → `record`

### 5. classify()

- 调用 `llm.chatJson(Prompts.CLASSIFY, input)`，让 LLM 返回包含 `intent` 的 JSON。
- 解析 JSON 取出 `intent`，取不到默认 `"record"`。
- 分类失败也返回 `"record"`，保证有兜底。

### 6. record()

- 最多尝试 2 次抽取。
- 每次成功条件：
  1. JSON 能解析成 `ExtractedItem`；
  2. `validate()` 错误列表为空；
  3. `repo.insert(...)` 入库成功。
- 成功后记录 `lastInsertedId` / `lastInsertedType`，返回 `✅ 已记录：...`。
- 两次失败后：
  1. 原文存入 `raw_inbox`；
  2. 提示语作为 assistant 消息存入 `messages`；
  3. 返回失败提示。

### 7. answer()

- 取出所有业务记录，转成 `List<Map<String, Object>>`。
- 序列化成 JSON 作为上下文，拼给 LLM。
- LLM 返回答案后，把 assistant 回复存入 `messages`，再返回。

### 8. correct()

- 如果没有上一条记录，直接提示先录入。
- 让 LLM 输出纠正后的 JSON。
- 根据 `lastInsertedType` 和 `lastInsertedId` 查出原记录。
- 用 `pick(新值, 原值)` 合并：有纠正值用纠正值，没有保留原值。
- 校验合并结果，通过后 `updateById`，返回 `✅ 已更新：...`。

### 9. pick / ack / summarize / typeLabel

- `pick(newValue, oldValue)`：非空用新值，否则保留旧值。
- `ack(item)`：返回 `✅ 已记录：` + 摘要。
- `summarize(item)`：把字段拼成人类可读摘要。
- `typeLabel(type)`：把英文类型转成中文（作业/考试/待办/课程/日程）。

### 10. 测试要点

- `PromptsTest`：验证 `extract()` 返回的提示词包含 `yyyy-MM-dd` 和真实日期。
- `AssistantServiceTest` 5 个测试：
  - 记录流程成功入库并返回 ✅
  - 抽取失败进 raw_inbox
  - 提问流程返回 LLM 答案
  - 纠正流程更新 dueDate、保留 title
  - 未知意图回退到 record

### 11. 本次排障：Windows 临时目录删不掉

- 现象：`AssistantServiceTest` 5 个测试都报 IO Failed to delete temp directory。
- 根因：测试里创建 `Database` 后没有关闭连接，Windows 下 SQLite 的 `s.db-shm` / `s.db-wal` 被占用。
- 修复：每个测试用 `try (Database db = new Database(...))` 包住，确保连接关闭。
- 教训：用完数据库连接必须关闭，尤其在 Windows + @TempDir 场景。

## 文件与方法链路

### 文件职责

- `Prompts`：提供所有提示词模板。
- `AssistantService`：核心编排，意图分类后走记录/提问/纠正。
- `PromptsTest` / `AssistantServiceTest`：验证提示词注入和三条业务流。

### 依赖关系

```text
用户输入
   ↓
AssistantService.handle
   ├─→ repo.insertMessage
   ├─→ classify → llm.chatJson(Prompts.CLASSIFY)
   ├─→ record → llm.chatJson(Prompts.extract) → ExtractedItem → repo.insert
   ├─→ answer → repo.allItems → llm.chat(Prompts.ANSWER)
   └─→ correct → llm.chatJson(Prompts.CORRECT) → repo.getById → repo.updateById
```

### 主要方法链路

- `handle`：保存消息 → classify → switch 分支
- `classify`：LLM 分类 JSON → 取 intent → 失败默认 record
- `record`：最多两次抽取 → 校验 → insert → ack；失败 → raw_inbox
- `answer`：allItems → toMap → JSON 上下文 → LLM → 保存回复
- `correct`：查上一条 → LLM 纠正 JSON → pick 合并 → 校验 → updateById

## 今天答错的点（复习重点）

1. **`"user"` 是用户名？** 错。是 `role`，表示消息角色。
2. **根据“用户输入文本类型”分支？** 不准确。是根据 `classify` 得到的 `intent`。
3. **default 是“先记录后面再处理”？** 错。是立刻走 `record()`。
4. **`answer()` 取出“语句记录”？** 不准确。是业务记录（作业/考试/待办/课程/日程）。
5. **只有失败信息才保存 assistant 回复？** 不准确。成功路径通常也会保存。
6. **`correct()` 查询失败返回提示？** 不准确。没找到原记录返回提示；数据库异常抛 RuntimeException。
7. **`assertTrue(条件, 消息)` 失败时返回第二个参数？** 错。失败时显示第二个参数作为提示，不是返回。

## 明日接续点

- Task 8 已完成（AppConfig + Main），笔记待整理。
- Task 9：真实 API 冒烟，需要本机创建 config.properties 并填入真实 Key。
