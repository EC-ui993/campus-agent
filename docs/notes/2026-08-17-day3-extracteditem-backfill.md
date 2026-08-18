# 第 3 天总结：理解回填 · ExtractedItem（2026-08-17）

> 今日状态：暂停写新代码，完成 `ExtractedItem.java` 和 `ExtractedItemTest.java` 的逐行理解回填；测试仍 8/8 绿

## 今日范围

- ① `src\main\java\com\campus\agent\model\ExtractedItem.java`
- ② `src\test\java\com\campus\agent\model\ExtractedItemTest.java`

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

## 今天答错的点（复习重点）

1. **record 有 setter？** 错。record 没有 setter，创建后不可变；取值方法叫 `type()` 不叫 `getType()`。
2. **正则“都能匹配”？** 错。`2026-11-2` 不能匹配，因为最后一段要求恰好 2 位数字。
3. **`validate()` 接收八个字段？** 错。它是实例方法，不接收参数，检查自己身上的字段。
4. **选填项“为空是否合法”？** 不够精确。选填项为 `null` 合法；有值时才检查格式。
5. **`assertEquals` 参数顺序**：第一个是期望值，第二个是实际值。

## 明日接续点

- 下一步按交接文档：进入 `Database.java`，先讲 try-with-resources 和异常；JDBC/SQL 细节留到“数据库”专题。
- 也可以先自己把这份笔记再过一遍，能用自己的话讲出“JSON → ExtractedItem → validate()”全流程，再开始 Database。
- 已知测试覆盖小缺口：目前没有专门测“JSON 缺失 type 字段”的用例；功能上会报错，后续可按 TDD 补。
