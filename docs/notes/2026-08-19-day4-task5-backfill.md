# 第 4 天总结：理解回填 · Task 5 StoredItem + ItemRepository（2026-08-19）

> 今日状态：Task 5 已跑绿并提交，之后补做了三个文件的逐行理解回填；全量测试 BUILD SUCCESS

## 今日范围

- `src/main/java/com/campus/agent/store/StoredItem.java`
- `src/main/java/com/campus/agent/store/ItemRepository.java`
- `src/test/java/com/campus/agent/store/ItemRepositoryTest.java`

## 今日知识点

### 1. StoredItem

- 是一个 `record`，表示“数据库查询出来的一行记录”。
- 字段：`id, type, title, course, teacher, content, location, dueDate, dueTime, status`
- 和 `ExtractedItem` 比：多了 `id` 和 `status`，其余字段基本对应。
- 它**不做 JSON 解析、不做校验**，因为数据已经由数据库/上层处理好了，它只是查询结果的容器。
- `toMap()`：把记录转成 `Map<String, Object>`，供后面拼 LLM 上下文用。
- 用 `LinkedHashMap` 是为了**保持插入顺序**，不是方便查找。

### 2. ItemRepository 的定位

- 内部保存 `Database db`，通过 `db.conn()` 获取连接。
- 它不实现 `AutoCloseable`，因为它不负责建立/关闭连接。
- 职责：基于已有连接做业务数据读写。
- 关系：Database 管连接/建表，ItemRepository 管业务 CRUD。

### 3. switch 表达式选择 SQL

- `insert` 根据 `item.type()` 选择不同的 INSERT SQL。
- 每种 type 对应一张业务表，表结构不同，所以字段列表不同。
- `default` 分支抛出 `IllegalArgumentException("未知类型: ...")`。

### 4. PreparedStatement

- 是 JDBC 里执行 SQL 的“传话人”，比 `Statement` 多做了**预编译**。
- SQL 里的 `?` 是占位符，先用 `ps.setXxx()` 填入具体值。
- 好处：
  - 同一句 SQL 多次执行效率更高；
  - 值作为参数传入，能**防止 SQL 注入**。

### 5. RETURN_GENERATED_KEYS

- `prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)` 告诉数据库：执行 INSERT 后返回自增主键。
- 通过 `ps.getGeneratedKeys()` 拿到结果集，`keys.next()` 移到第一行，`keys.getLong(1)` 取第 1 列的值。

### 6. setParams 可变参数

- `Object... params` 是可变参数，方法内部当作数组用。
- 在 `insert` 中实际传入的参数**不固定**，取决于 `item.type()` 对应的表需要哪些字段：
  - `assignment`：6 个 String（或 null）+ 1 个 long = 7 个参数
  - `exam`：7 个 String（或 null）+ 1 个 long = 8 个参数
  - `todo`：4 个 String（或 null）+ 1 个 long = 5 个参数
  - `course`：3 个 String（或 null）+ 1 个 long = 4 个参数
  - `event`：5 个 String（或 null）+ 1 个 long = 6 个参数
- 循环里 `i + 1` 对应 SQL 中第几个 `?` 占位符（数组下标从 0 开始）。
- 三种处理：
  - `null` → `ps.setObject(i + 1, null)`
  - `Long` → `ps.setLong(i + 1, l)`
  - 其他 → `ps.setString(i + 1, params[i].toString())`
- `params[i] instanceof Long l` 是 pattern matching for instanceof：判断类型并自动转换。

### 7. updateById

- 返回 `boolean`。
- `ps.executeUpdate()` 返回受影响的行数。
- 因为按 id 更新一行，成功时应返回 1，所以用 `== 1` 判断。

### 8. allItems 与 queryInto

- `allItems()` 按顺序查询：assignments → exams → todos → courses → events。
- 每个表查询后 `ORDER BY id`，保证顺序稳定。
- `queryInto(list, type, sql)` 接收收集结果的 List、业务类型、SQL。
- 流程：`ResultSet` 逐行 `next()` → 每行 `map(rs)` 转成 `StoredItem` → 加入 list。
- 查询 SQL 里用 `'assignment' AS type` 给常量列起别名，`map` 里用 `rs.getString("type")` 取到该值。
- 对于没有某列的表，用 `NULL AS location` 占位，最终 `StoredItem` 对应字段为 null。

### 9. getById 与 Optional

- `getById(String type, long id)` 返回 `Optional<StoredItem>`。
- 因为可能查不到记录，用 Optional 明确告诉调用方“可能有值，可能没值”。
- 查到：`Optional.of(map(rs))`；没查到：`Optional.empty()`。
- `Optional.empty()` 是**创建一个空盒子**，不是清空已有盒子。

### 10. insertMessage 与 insertRaw

- `insertMessage` 固定往 `messages` 表插入 `role, content`。
- `insertRaw` 固定往 `raw_inbox` 表插入 `content, reason`。
- 它们不需要 switch，因为表是固定的。

### 11. ItemRepositoryTest 的 4 个测试

1. `insertAndListRoundTrip`：插入一条 assignment，`allItems()` 能查到 1 条，字段和默认 status 正确。
2. `updateByIdMergesFields`：插入 todo 后按 id 更新，`getById` 查到新 dueDate。
3. `insertMessageAndRawInbox`：插入 message 和 raw_inbox，验证 `allItems()` 不包含它们。
4. `allItemsCoversEveryType`：5 种类型各插一条，`allItems()` 返回 5 条。

### 12. Task 5 排障记录（重要）

- 报错：`ItemRepositoryTest.insertMessageAndRawInbox:60 expected: <1> but was: <0>`
- 根因：计划文档里第 60 行期望值写错。
- `allItems()` 只查 5 张业务表，不查 `messages` 和 `raw_inbox`。
- 该测试只插入了 message 和 raw_inbox，所以 `allItems().size()` 应为 `0`，不是 `1`。
- 修复：把断言改成 `assertEquals(0, repo.allItems().size())`。
- 教训：计划是人写的会出错，测试是机器跑的，能暴露问题。

## 今天答错的点（复习重点）

1. **Task 5 测试里是“输入 JSON”？** 错。是直接 `new ExtractedItem(...)` 构造对象，不是 JSON。
2. **`allItems()` 大小由“对象数量”决定？** 不准确。由“插入数据库的记录次数/条数”决定。
3. **`insert(..., 1)` 里的 `1` 是 status？** 错。是 `sourceMessageId`（来源消息 id）。
4. **`setParams` 传的都是 String？** 错。最后一个 `sourceMessageId` 是 long；而且 String 数量不固定，取决于 type 对应的表。
5. **笔记原句“7 个 String + 1 个 long”也不够准确？** 对。这只是 `exam` 的情况；`assignment` 是 6 个 String + 1 个 long，其他 type 又不同。
6. **`keys.getLong(1)` 的 `1` 是列名？** 错。是第 1 列，因为自增主键结果集通常只有一列。
7. **`i + 1` 对应自增 id？** 错。对应 SQL 里的 `?` 占位符编号。
8. **`Optional.empty()` 是清空？** 错。是创建一个空盒子。
9. **`PreparedStatement ps` 是“连接”？** 错。它是执行 SQL 的对象，连接是 `Connection`。
10. **`NULL AS location` 最终变成 status？** 错。变成 `StoredItem.location` 为 null。

## 明日接续点

- Task 5 理解回填完成。
- 下一步按计划是 Task 6：`LlmClient` 接口 + `DeepSeekClient`（TDD）。
- Task 6 会引入接口、HTTP 调用、Jackson 构建/解析 JSON，建议先读测试再动手。
- 若还要继续理解回填，可考虑 `Prompts`、`AssistantService` 等已写/后续文件。
