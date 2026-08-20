# 第 3 天总结：理解回填 · ExtractedItem + Database（2026-08-17）

> 今日状态：暂停写新代码，完成 `ExtractedItem.java` / `ExtractedItemTest.java` / `Database.java` / `DatabaseTest.java` 的逐行理解回填；测试仍 8/8 绿

## 今日范围

- ① `src\main\java\com\campus\agent\model\ExtractedItem.java`
- ② `src\test\java\com\campus\agent\model\ExtractedItemTest.java`
- ③ `src\main\java\com\campus\agent\store\Database.java`
- ④ `src\test\java\com\campus\agent\store\DatabaseTest.java`

## 今日知识点

### 1. record（今天最重要的概念）

- record 是一种专门表示“数据”的类。
- 自动生成：构造方法、取值方法、`equals`、`hashCode`、`toString`。
- **取值方法命名是 `item.type()`，不是 `getType()`。**
- **没有 setter**：record 的数据创建后不可变。
- 适用场景：像 `ExtractedItem` 这种“从 JSON 解析出来后不再修改”的数据模型。

### 2. package / import

- `package` 声明类所在的包，类似“家庭住址”。
- `import` 引入其他包里的类，后面才能直接用短名字。
- 常见导入：`JsonNode`、`ObjectMapper`、`List`、`Set`、`ArrayList`。

### 3. static 与 static final

- `static` 成员属于“类本身”，不属于某个对象；不需要创建对象就能用，如 `ExtractedItem.VALID_TYPES`。
- `final` 表示引用不能被重新赋值。
- `private static final ObjectMapper MAPPER`：整个类共享同一个 Jackson 解析器，没必要每个对象 new 一个。

### 4. Set.of(...)

- 创建一个“不重复、不可变”的集合。
- 不能对它 `add` / `remove`。
- `VALID_TYPES` 就是“合法 type 白名单”。

### 5. 静态工厂方法 `fromJson`

- `public static ExtractedItem fromJson(String json)` 不依赖对象，直接用 `ExtractedItem.fromJson(...)` 调用。
- 它和 `new ExtractedItem(...)` 的区别：`new` 只能按已有字段构造对象；`fromJson` 还要先解析 JSON、处理空字段。
- 流程：JSON 字符串 → `ObjectMapper.readTree` 解析成 JSON 树 → `path("字段")` 取节点 → `asText("")` 转字符串 → `blankToNull` 清洁 → `new ExtractedItem(...)`。

### 6. 异常处理：受检转非受检

- `readTree` 解析失败会抛 `IOException`（受检异常），Java 强制处理。
- 这里用 `try-catch` 接住后转成 `RuntimeException` 抛出。
- 目的：LLM 返回坏 JSON 时快速失败、让错误明显；调用方不用到处写 `catch`。

### 7. JsonNode 的 path / asText

- `n.path("type")`：取字段节点；字段不存在时返回“空节点”，不崩。
- `.asText("")`：把节点转成字符串；空节点时返回默认值 `""`。
- 注意链路：**空节点 → `""` → `blankToNull` 再变成 `null`。**

### 8. blankToNull

- 输入是 `asText("")` 出来的字符串。
- 如果 `s == null` 或 `s.isBlank()`（空串、纯空格），返回 `null`。
- 否则返回 `s.trim()`，去掉首尾空格。
- 例：`"  高数  "` → `"高数"`；`"   "` → `null`。

### 9. validate() 的校验规则

- 是**实例方法**，不需要参数，直接读自己身上的字段。
- 返回 `List<String>`：空列表 = 合法；非空 = 每个元素是一条错误。
- 必填字段 `type` / `title`：`null` 或空/纯空格都不合法，要报错。
- 选填字段 `dueDate` / `dueTime`：`null` 合法，表示没有该信息；有值时才检查格式。
- 正则只查格式，不查日期是否真实存在：`2026-99-99` 也能通过格式检查。

### 10. String.matches 与正则转义

- `matches` 要求**整个字符串完全匹配**，不是部分匹配。
- Java 字符串里写 `\\d`，实际代表正则里的 `\d`（一个数字）。
- `\\d{4}-\\d{2}-\\d{2}` 能匹配 `2026-11-20`，不能匹配 `2026-11-2`。

### 11. 测试基础

- `@Test`：标记一个方法是 JUnit 测试方法。
- `assertEquals(期望值, 实际值)`：**第一个参数是期望，第二个是实际**；失败说明解析结果不符合预期。
- 文本块 `"""..."""`：适合多行 JSON/SQL/HTML，省去大量 `\n` 和转义引号。
- `assertTrue(...)`：条件为 true 才通过。
- `errors.stream().anyMatch(e -> e.contains("title"))`：检查错误列表中是否存在至少一条包含 `"title"` 的错误。
- `assertThrows(异常类型, lambda)`：验证某段代码执行时必须抛出指定异常。

### 12. 为什么 assertThrows 要用 lambda

- 如果直接写 `assertThrows(..., ExtractedItem.fromJson("这不是JSON"))`，`fromJson` 会在传参前立刻执行，异常在 `assertThrows` 看到之前就抛了。
- `() -> ...` 把“执行动作”打包，让 `assertThrows` 在内部触发并捕获异常。

### 13. 字符串拼接 `+`

- 当 `+` 一边是字符串时，Java 会做字符串拼接，不是数学加法。
- 例：`"实际: " + errors` 把错误列表转成字符串拼到提示语后面。

### 14. JDBC vs SQLite

- **JDBC** 是 Java 提供的数据库访问接口/工具，比如 `Connection`、`Statement`、`DriverManager`。
- **SQLite** 是真正干活的数据库引擎，负责存储数据和执行 SQL。
- 关系：Java 代码用 JDBC API → 驱动 → 操作 SQLite。

### 15. 数据库连接 Connection

- `Connection` 是 Java 程序和数据库之间的通信通道/会话，像一条电话线。
- `DriverManager.getConnection("jdbc:sqlite:...")` 用来建立连接。
- 用完必须 `close()`，否则一直占用资源。
- SQLite 的特点是：如果数据库文件不存在，连接时会自动创建空文件。

### 16. AutoCloseable 与 try-with-resources

- `implements AutoCloseable` 表示这个类可以“自动关闭”。
- `try (Statement st = conn.createStatement()) { ... }` 是 try-with-resources。
- 括号里的资源必须实现 `AutoCloseable`；try 块结束后 Java 自动调用 `close()`，不用手写 finally。
- `Database` 实现 `AutoCloseable`，所以测试里能写 `try (Database db = new Database(dbFile)) { ... }`。

### 17. Statement 与三种执行方法

- `Statement` 是 JDBC 里执行 SQL 的“传话人”，通过 `conn.createStatement()` 创建。
- `execute(sql)`：适合建表、PRAGMA 等不一定返回结果集的 SQL。
- `executeQuery(sql)`：执行 SELECT，返回 `ResultSet` 结果集。
- `executeUpdate(sql)`：执行 INSERT/UPDATE/DELETE，返回受影响行数（int）。

### 18. Database 构造方法做的事

1. 如果有父目录，用 `Files.createDirectories` 确保目录存在；已存在会忽略。
2. 用 `DriverManager.getConnection` 打开 SQLite 连接，并赋给 `final Connection conn`。
3. 用 try-with-resources 创建 `Statement`，执行两条 PRAGMA。
4. 把 `SCHEMA` 按分号切分，逐条执行建表语句。

### 19. PRAGMA

- `PRAGMA journal_mode=WAL;`：设置 SQLite 日志模式为 WAL，提升并发读写性能。
- `PRAGMA busy_timeout=5000;`：数据库被其他连接占用时，最多等待 5 秒。

### 20. SCHEMA 与建表

- `SCHEMA` 是 `private static final String`，内部使用的建表 SQL 集合。
- `CREATE TABLE IF NOT EXISTS`：表不存在就创建，已存在就跳过，避免重复建表报错（幂等）。
- 必须按 `;` 切分后逐条执行，因为 SQLite 的 `execute()` 一次只执行第一条语句，后续会静默丢弃。

### 21. 7 张表的结构记忆

- `assignments`：id、title、course、teacher、content、due_date、due_time、status(默认 pending)、source_message_id、created_at
- `exams`：比 assignments 多 `location`，status 默认 `upcoming`
- `todos`：没有 course、teacher，status 默认 `pending`
- `courses`：id、title、teacher、location、source_message_id、created_at（没有 content/due/status）
- `events`：最接近 todos，多了 `location`，status 默认 `upcoming`
- `messages`：id、role、content、created_at；role 和 content 都是 NOT NULL
- `raw_inbox`：id、content、reason、created_at；content NOT NULL

### 22. @TempDir

- JUnit 5 注解：测试前自动创建临时目录，测试后自动清理。
- `tmp.resolve("test.db")` 得到临时目录下的 `test.db` 路径。
- 好处：测试不会污染项目真实数据。

### 23. ResultSet

- `ResultSet` 是 SELECT 查询返回的结果集，像一张临时表格：有行有列。
- `rs.next()`：把当前行移到下一行；一开始指向第一行之前，所以要先调用它。
- `rs.getString("name")`：取当前行中名为 `name` 的列值，转成 String。
- `rs.getInt("c")`：取当前行中名为 `c` 的列值，类型是 int。

### 24. COUNT 与别名

- `SELECT COUNT(*) AS c FROM messages`：统计 messages 表行数，并把结果列命名为 `c`。
- 查询结果是一行一列：`c` 的值是行数。
- 如果插入 3 条记录，`rs.getInt("c")` 就返回 3。

### 25. 测试验证思路

- 第一个测试：建库后查 `sqlite_master`，用 `Set` 收集所有表名，再用 `containsAll` 验证 7 张表都在。
- 第二个测试：第一次打开插入一条数据，关闭后重新打开，用 `COUNT(*)` 验证数据还在，证明 SQLite 持久化。

## 文件与方法链路

### 文件职责

- `ExtractedItem.java`：LLM 抽取结果模型，负责 JSON 解析、空字段清洗、合法性校验。
- `ExtractedItemTest.java`：验证解析、空字段转 null、必填/格式校验、坏 JSON 抛异常。
- `Database.java`：打开 SQLite 连接、建目录、设置 PRAGMA、创建 7 张表、提供连接和关闭。
- `DatabaseTest.java`：验证建库建表和持久化。

### 依赖关系

```text
JSON 字符串
   ↓ ExtractedItem.fromJson()
ExtractedItem
   ↓ item.validate()
List<String> 错误列表 / 空列表
```

```text
DatabaseTest
   ↓ new Database(dbFile)
Database 构造方法
   ↓ DriverManager.getConnection
SQLite 连接
   ↓ conn() / Statement
SQL 执行
```

### 主要方法链路

- `ExtractedItem.fromJson(String)`：
  `MAPPER.readTree` → `path(...).asText("")` → `blankToNull` → `new ExtractedItem(...)`
- `ExtractedItem.validate()`：
  检查 type/title 必填，检查 dueDate/dueTime 格式 → 返回 `List<String>`
- `ExtractedItem.blankToNull(String)`：
  空/纯空格 → `null`；否则 `trim()`
- `Database(Path)`：
  创建父目录 → 打开连接 → 执行 PRAGMA → 逐条执行 SCHEMA
- `Database.conn()` / `close()`：
  提供连接 / 关闭连接

## 今天答错的点（复习重点）

1. **record 有 setter？** 错。record 没有 setter，创建后不可变；取值方法叫 `type()` 不叫 `getType()`。
2. **正则“都能匹配”？** 错。`2026-11-2` 不能匹配，因为最后一段要求恰好 2 位数字。
3. **`validate()` 接收八个字段？** 错。它是实例方法，不接收参数，检查自己身上的字段。
4. **选填项“为空是否合法”？** 不够精确。选填项为 `null` 合法；有值时才检查格式。
5. **`assertEquals` 参数顺序**：第一个是期望值，第二个是实际值。
6. **`courses` 没有 location？** 口误。`courses` 有 location，没有的是 content / due_date / due_time / status。
7. **`ResultSet` 是“Set 形式的结果”？** 不准确。`ResultSet` 是像临时表格一样的结果集，有行有列，不是 Java 的 `Set` 集合。
8. **`rs.getInt("c")` 是“取第 c 列”？** 不准确。`c` 是列名，`rs.getInt("c")` 是取出名为 `c` 的那一列的值。

## 明日接续点

- `ExtractedItem` 和 `Database` 的理解回填已完成；JDBC/SQL 细节留到“数据库”专题。
- 下一步候选：
  - 回 Task 5（StoredItem + ItemRepository，写新代码前可以先预习 `PreparedStatement`、`RETURN_GENERATED_KEYS`）；
  - 或继续把其他已写文件做理解回填（如 `Prompts`、`LlmClient` 等）。
- 已知测试覆盖小缺口：目前没有专门测“JSON 缺失 type 字段”的用例；功能上会报错，后续可按 TDD 补。
