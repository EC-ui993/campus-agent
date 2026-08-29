# Task 3 总结：ExtractionEngine 重构（抽取逻辑共用）（2026-08-28）

> 今日状态：Task 3 完成，全量 54 个测试绿，已提交

## 今日范围

- `service/ExtractionEngine.java`：抽取引擎
- `service/AssistantService.java`：record() 改用 engine
- `config/AppBeans.java`：装配 ExtractionEngine
- `service/ExtractionEngineTest.java`：引擎测试

## 今日知识点

### 1. 重构

- 不改变外部行为，只改善内部结构
- 判断标准：既有测试保持绿

### 2. 为什么抽 ExtractionEngine

- 聊天录入和 Excel 导入都走同一套流程：
  ```text
  文本 → LLM 抽取 → 校验 → 按类型入库 → 失败兜底
  ```
- 抽成公共组件，避免写两套
- 只依赖 `LlmClient` + `ItemRepository`，不依赖 AssistantService

### 3. ExtractionEngine

- `Outcome(ok, summary, error)`：返回是否成功、摘要、错误
- `extractAndStore(input, sourceMessageId)`：
  - 最多尝试 2 次
  - 普通类型先 `item.validate()`
  - `course_override` / `semester_setting` 不走普通校验，直接走专门分支
  - 两次失败进 raw_inbox

### 4. AssistantService.record()

- 大幅简化：
  ```java
  ExtractionEngine.Outcome o = engine.extractAndStore(input, messageId);
  ```
- 成功返回 “✅ 已记录：...”
- 失败返回 “⚠️ 这条我没能解析...”

### 5. 文案变化

- semester_setting 回执从“已设置学期”变成“已记录：学期...”
- 行为等价，测试断言同步更新

### 6. 测试环境

- `@SpringBootTest` + `@DynamicPropertySource` 临时数据库
- `@BeforeEach` 清空所有表
- 测试辅助方法 `engine(FakeLlm)` 避免重复 new

## 文件与方法链路

```text
聊天文本 / Excel 行文本
   ↓ ExtractionEngine.extractAndStore
LLM 抽取
   ↓ ExtractedItem
特殊类型？ course_override / semester_setting
   ├─ 是 → 专门分支
   └─ 否 → item.validate()
   ↓
入库 / 失败兜底 raw_inbox
   ↓
Outcome(ok, summary, error)
```

## 今天踩坑记录

- `semester_setting` / `course_override` 被 `item.validate()` 拦截，因为 title 为空。
- 修复：特殊类型直接进入 `store()` 做专门校验，不先走普通 validate。
- semester_setting 回执文案变化导致测试失败，同步更新断言。

## 下一步

- Task 4：导入端点 POST /api/import/excel。
