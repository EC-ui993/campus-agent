# 第 5 天总结：理解回填 · Task 10 回归测试（2026-08-20）

> 今日状态：Task 10 完成，全量测试 23/23 绿；已补做逐行理解回填

## 今日范围

- `src/test/java/com/campus/agent/SampleMessagesTest.java`

## 今日知识点

### 1. SampleMessagesTest 的作用

- 用 `FakeLlm` 驱动 10 条典型老师消息走完整流水线。
- 目的是回归测试：以后改代码时，确保“用户消息 → 意图分类 → 抽取 → 入库”这条管道不退化。

### 2. SAMPLES 数据结构

- 类型：`String[][]`（二维字符串数组）。
- 每个元素包含两个字符串：
  1. 用户原始消息
  2. 对应的抽取结果 JSON

### 3. 每个 sample 需要两个预设 JSON

- 第一次 `json("{\"intent\":\"record\"}")`：给 `classify()` 用，表示意图是记录。
- 第二次 `json(sample[1])`：给 `record()` 用，表示抽取结果。
- 所以循环里每个 sample 调两次 `json(...)`。

### 4. 测试主流程

1. 创建 `FakeLlm`，为 10 个 sample 按顺序预设响应。
2. 用 `@TempDir` 创建临时数据库。
3. 组装 `Database`、`ItemRepository`、`AssistantService`。
4. 对每条 sample 调用 `service.handle(...)`。
5. 断言回复以 `✅` 开头。
6. 最后 `allItems().size()` 等于 10，证明 10 条都入库。

### 5. @TempDir 与数据库关闭

- 测试使用临时目录，不会污染真实 `data/agent.db`。
- 使用 `try (Database db = ...)` 确保连接关闭，Windows 下才能正常删除临时目录。

## 文件与方法链路

### 文件职责

- `SampleMessagesTest`：阶段 1 的回归测试，覆盖 10 条典型消息。

### 依赖关系

```text
SampleMessagesTest
   ↓ FakeLlm 预设响应
AssistantService.handle
   ↓ classify / record
ItemRepository
   ↓ SQLite
```

### 主要方法链路

- `allTenSamplesFlowThroughPipeline`：
  预设 FakeLlm → 建临时库 → 组装 service → 逐条 handle → 断言 ✅ → 断言 10 条入库

## 今天答错的点（复习重点）

1. **每个 sample 调两次 json 是因为“记录会尝试两次”？** 错。第一次给 classify，第二次给 record。
2. **测试只验证“能抽取信息”？** 不完整。验证的是完整流水线能跑通，并且 10 条都入库。

## 明日接续点

- 阶段 1 完成验收清单：
  - 全量测试 23 个绿
  - 真实 API 冒烟通过
  - DB Browser 数据确认
  - 用自己的话讲清完整流水线


## 完整流水线（阶段 1 验收版）

### 一、总体流程

1. 用户输入：`String input`。
2. `handle(input)`：
   - 先以 `role=user` 把用户消息存入 `messages`，拿到 `messageId`。
   - 调用 `classify(input)` 得到 `intent`。
   - 根据 `intent` 走 `question` / `correction` / `record` 三条分支。
3. 最终返回给用户的回复是 `String`。

### 二、数据状态变化

```text
用户原始消息
   String
      ↓ repo.insertMessage("user", input)
messages 表里的一行（role=user, content=原文）
   DB Row
      ↓ classify(input)
llm.chatJson(...) 返回 JSON 字符串
   String JSON
      ↓ MAPPER.readTree
JsonNode
      ↓ path("intent").asText("record")
intent 字符串（record / question / correction）
   String
```

### 三、record 分支

```text
intent = record
   ↓ llm.chatJson(Prompts.extract(), input)
抽取结果 JSON 字符串
   String JSON
      ↓ ExtractedItem.fromJson(json)
ExtractedItem 对象
   ↓ item.validate()
错误列表为空？
   ├─ 是：repo.insert(item, messageId)
   │        ↓
   │     对应 type 表里的一行（assignments/exams/todos/courses/events）
   │        ↓
   │     返回 ✅ 已记录：...
   └─ 否：最多重试 2 次
           ↓ 仍失败
        原文存入 raw_inbox + assistant 提示存入 messages
        返回 ⚠️ 没能解析...
```

### 四、question 分支

```text
intent = question
   ↓ repo.allItems()
List<StoredItem>
   ↓ 每个 StoredItem.toMap()
List<Map<String, Object>>
   ↓ MAPPER.writeValueAsString(rows)
JSON 字符串（数据库上下文）
   ↓ llm.chat(Prompts.ANSWER, 上下文 + 用户问题)
String reply
   ↓ repo.insertMessage("assistant", reply)
messages 表里的一行（role=assistant）
   ↓
返回 String reply
```

### 五、correction 分支

```text
intent = correction
   ↓ 检查 lastInsertedId / lastInsertedType
没有上一条 → 返回提示
   ↓ llm.chatJson(Prompts.CORRECT, input)
纠正 JSON 字符串
   String JSON
      ↓ ExtractedItem.fromJson(json)
ExtractedItem corr
   ↓ repo.getById(lastType, lastId)
Optional<StoredItem> current
   ↓ current 存在
StoredItem cur
   ↓ pick(纠正值, 原值) 合并
ExtractedItem merged
   ↓ merged.validate()
错误列表为空？
   ├─ 是：repo.updateById(merged, lastId)
   │        ↓
   │     对应 type 表里的一行被更新
   │        ↓
   │     返回 ✅ 已更新：...
   └─ 否：返回 纠正失败：...
```

### 六、一句话总结

> 用户消息从 `String` 开始，先变成 `messages` 里的一行；再通过 LLM 变成 `JSON 字符串`，解析成 `ExtractedItem`；最后按 type 写入对应业务表；所有助手回复也作为 `role=assistant` 写回 `messages`。


## 遗留问题（待规划层决定）

- 当前纠正功能只能纠正“最近一次成功录入”的记录。
- 用户希望能通过 `/list` 后指定“第几条”来纠正，例如“把第3条中的日期改成11月12日”。
- 可能方向：
  - 支持 `/correct <id> ...` 命令；
  - 让 `correct()` 解析用户提到的编号；
  - 先选择记录，再纠正。
- 该问题不在当前阶段 1 最小闭环内，留给后续迭代/规划会话讨论。
