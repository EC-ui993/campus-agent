# Task 9 总结：按记录 id 纠正（无状态化）+ GET /api/items（2026-08-26）

> 今日状态：Task 9 完成，全量 34 个测试绿，已提交

## 今日范围

- `Prompts.java`：CORRECT 提示词改为基于数据库记录输出 type + id
- `AssistantService.java`：去掉 lastInsertedId/lastInsertedType，改为无状态纠正
- `ChatController.java`：新增 GET /api/items
- `static/index.html`：新增“查看记录”面板
- `AssistantServiceTest.java`：纠正测试改为按 id，新增 id 不存在分支

## 今日知识点

### 1. 共享可变状态 vs 无状态

- 阶段 1 用 `lastInsertedId` / `lastInsertedType` 记住“上一次记录”。
- 命令行单用户没问题，但网页多请求并发时会互相踩。
- 无状态设计：纠正时不靠记忆，而是从 LLM 输出里拿到 `type` 和 `id`。

### 2. 新的纠正流程

```text
用户说“把作业第3条的日期改成11月12日”
   ↓
服务把数据库所有记录序列化成 JSON
   ↓
替换到 Prompts.CORRECT 的 {records}
   ↓
LLM 输出 {"type":"assignment","id":3,"dueDate":"2026-11-12",...}
   ↓
服务按 type + id 查出原记录
   ↓
合并字段
   ↓
updateById
```

### 3. GET /api/items

- 返回所有业务记录，转成 `List<Map<String, Object>>`。
- 前端可以调用它展示记录列表，方便用户照着说“第几条”。

### 4. 前端记录面板

- 标题栏加“查看记录”按钮。
- 点击后 fetch `/api/items`，带 `X-Access-Token`。
- 渲染每条：类型、id、标题、日期/时间。

### 5. SQLite AUTOINCREMENT 不因 DELETE 重置

- `AUTOINCREMENT` 会记住 `sqlite_sequence`。
- 即使清空表，下一条 id 也会继续增加。
- 测试里应该用“查出来的真实 id”，不能假设是 1。

## 文件与方法链路

```text
前端“查看记录”
   ↓ GET /api/items（带 token）
ChatController.items()
   ↓ repo.allItems()
StoredItem 列表
   ↓ toMap()
JSON 数组
   ↓
前端渲染
```

```text
用户纠正
   ↓ AssistantService.correct()
repo.allItems() → recordsJson
   ↓ Prompts.CORRECT.replace("{records}", ...)
LLM 返回 type + id
   ↓ repo.getById(type, id)
StoredItem
   ↓ 合并
updateById
```

## 今天踩坑记录

1. 纠正测试假设 id=1，但 SQLite 自增不清零，导致“没找到编号为 1”。
   修复：先查真实 id，再动态构造纠正 JSON。

## 下一步

- Task 10：端到端验收 + 数据连续性 + 笔记归档。
