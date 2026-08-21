# 第 5 天总结：理解回填 · Task 8 AppConfig + Main（2026-08-20）

> 今日状态：Task 8 完成并提交，编译与全量测试通过；已补做逐行理解回填

## 今日范围

- `src/main/java/com/campus/agent/AppConfig.java`
- `src/main/java/com/campus/agent/Main.java`

## 今日知识点

### 1. AppConfig record

- 是一个 record，字段：
  - `apiKey`：DeepSeek API 密钥
  - `model`：模型名
  - `baseUrl`：API 地址
  - `dbPath`：数据库文件路径
- `load()` 是静态方法，外部直接用 `AppConfig.load()` 调用。

### 2. Properties 与 config.properties

- `Properties` 是 Java 读写 `key=value` 配置文件的类。
- `Path.of("config.properties")` 定位配置文件。
- `Files.exists(f)` 判断文件是否存在：
  - 不存在：跳过读取，不报错；
  - 存在：用 `Files.newBufferedReader` 读进来，`p.load(reader)` 解析键值对。
- 读取失败：抛 `IllegalStateException("读取 config.properties 失败: ...")`。

### 3. API Key 优先级

- 先读环境变量 `DEEPSEEK_API_KEY`。
- 如果环境变量没有或为空，才读 `config.properties` 里的 `api.key`。
- 如果两者都没有，抛 `IllegalStateException` 提示缺少 Key。
- `key.trim()` 去掉首尾空格。

### 4. 默认值

- `p.getProperty("model", "deepseek-chat")`：
  如果配置文件没有 `model`，就返回默认值 `"deepseek-chat"`。
- 同理：
  - `base.url` 默认 `https://api.deepseek.com`
  - `db.path` 默认 `data/agent.db`

### 5. Main 入口

- `main` 先 `AppConfig.load()` 加载配置。
- 组装对象：
  - `Database db`：数据库连接管理
  - `ItemRepository repo`：数据读写
  - `LlmClient llm`：DeepSeekClient
  - `AssistantService service`：核心编排
- `try (Database db = ...)` 确保退出时关闭连接。

### 6. 冒烟模式 vs 交互 REPL

- `args.length > 0`：冒烟模式。
  - 每个命令行参数当作一条用户消息；
  - 打印 `你 > ...` 和 `助手 > ...`；
  - 处理完直接退出。
- 不带参数：交互 REPL。
  - 出现 `你 >` 提示符；
  - 支持 `/quit`、`/exit`、`/help`、`/list`；
  - 其他输入交给 `service.handle(line)`。

### 7. Scanner

- `Scanner(System.in, StandardCharsets.UTF_8)` 从键盘读输入。
- `in.hasNextLine()` 判断是否还有下一行。
- `line.trim()` 去掉首尾空格。
- `line.isEmpty()` 时空行跳过，不进入 switch。

### 8. listAll

- 调用 `repo.allItems()` 获取所有业务记录。
- 为空：输出“（数据库还没有记录）”。
- 不为空：用 `printf` 逐条输出：
  - `[类型] #编号 《标题》 时间:日期或- 状态:状态`
- `typeLabel(type)` 把英文类型转成中文。

## 文件与方法链路

### 文件职责

- `AppConfig`：加载配置，提供 apiKey / model / baseUrl / dbPath。
- `Main`：程序入口，负责组装对象、冒烟模式、交互 REPL、列表展示。

### 依赖关系

```text
Main
   ↓ AppConfig.load()
AppConfig
   ↓
Database / ItemRepository / DeepSeekClient / AssistantService
   ↓
用户输入 → service.handle → 回复
```

### 主要方法链路

- `AppConfig.load()`：
  读 config.properties → 读环境变量 Key → 返回 AppConfig
- `Main.main(args)`：
  load 配置 → 组装对象 → args 判断冒烟/REPL
- `Main.listAll(repo)`：
  allItems → 空提示 / 逐条 printf
- `Main.printHelp()`：打印帮助文本
- `Main.typeLabel(type)`：类型转中文

## 今天答错的点（复习重点）

1. **`dbPath` 是“agent 的路径”？** 错。是数据库文件路径。
2. **API Key 优先从 config.properties 读？** 错。优先环境变量，没有再读 config.properties。
3. **会读 config.properties.example？** 错。example 只是模板，运行时不会读它。
4. **`Properties` 是“找 api.key”？** 不准确。它是 key=value 配置文件读写类，`getProperty` 才是取值。
5. **`config.properties` 不存在会报错？** 错。会跳过读取，不报错。
6. **`Database` 是传话人？** 不准确。它是连接管理对象；Statement/PreparedStatement 才是传话人。
7. **`listAll` 输出“时间（有的话）”** 不精确。没有日期时输出 `-`，有日期时可能带时间。

## 明日接续点

- Task 8 完成。
- Task 9：真实 API 冒烟，无新代码。
  - 创建 `config.properties` 并填入真实 Key；
  - 跑录入 → 提问 → 纠正；
  - 用 DB Browser 看数据。
