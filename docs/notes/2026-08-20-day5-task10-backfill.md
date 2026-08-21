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
