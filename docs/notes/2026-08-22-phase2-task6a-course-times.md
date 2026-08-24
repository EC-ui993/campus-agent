# Task 6 补充总结：课程表增加起止时间（2026-08-22）

> 今日状态：补充任务完成，全量测试绿，已提交

## 今日范围

- `Database.java`：courses 表增加 `start_time`、`end_time`
- `ExtractedItem.java`：增加 `startTime`、`endTime`
- `StoredItem.java`：增加 `startTime`、`endTime`
- `ItemRepository.java`：course 插入/更新/查询支持起止时间
- `Prompts.java`：抽取提示词增加 `startTime`、`endTime`
- `AssistantService.java`：纠正合并时保留起止时间
- `ItemRepositoryTest.java`：构造参数更新

## 今日知识点

### 1. 问题背景

- 原来 `courses` 表没有时间字段。
- 录入“今天17:00-18:20有一节体育课”时，即使 LLM 抽出了时间，入库后时间也会丢失。
- 问答时只能查到课程名称，没有具体上课时间。

### 2. 解决方案

- `courses` 表新增：
  - `start_time TEXT`
  - `end_time TEXT`
- `ExtractedItem` 新增：
  - `startTime`
  - `endTime`
- `StoredItem` 新增：
  - `startTime`
  - `endTime`
- 这样课程信息可以完整保存“开始时间”和“结束时间”。

### 3. ItemRepository 改动

- course 插入 SQL：
  ```sql
  INSERT INTO courses(title,teacher,location,start_time,end_time,source_message_id)
  VALUES(?,?,?,?,?,?)
  ```
- course 更新 SQL 同样增加 `start_time`、`end_time`。
- 查询时：
  - courses 表直接查 `start_time,end_time`
  - 其他表用 `NULL AS start_time, NULL AS end_time` 占位，保证 `StoredItem` 统一形状。

### 4. Prompts 改动

- 抽取提示词增加：
  ```text
  - startTime: 课程/日程开始时间，格式 HH:mm（没有则留空）
  - endTime: 课程/日程结束时间，格式 HH:mm（没有则留空）
  ```

### 5. 纠正合并

- `AssistantService.correct()` 在合并时也要用 `pick` 保留/更新 `startTime` 和 `endTime`。

## 文件与方法链路

```text
用户输入“今天17:00-18:20有一节体育课”
   ↓ LLM 抽取
ExtractedItem(type=course, startTime=17:00, endTime=18:20)
   ↓ ItemRepository.insert
courses 表（start_time, end_time 已保存）
   ↓ allItems / getById
StoredItem(startTime, endTime)
   ↓ toMap
LLM 问答上下文
```

## 注意

- 所有 `new ExtractedItem(...)` 的旧调用都要补上 `startTime`、`endTime` 两个参数。
- 课程起止时间现在能存、能查、能带给 LLM 回答。

## 下一步

- Task 7：局域网访问 + 简单口令。
