# 第 2 天总结（2026-08-16）

> 今日状态：Task 4 完成（含一次真正的 bug 修复），测试 8/8 绿，工作区干净

## 今日战果

- Task 4 完成：Database（建库建表）+ 2 个测试
- **人生第一次调试**：修复了"7 条建表语句只执行了 1 条"的 bug
- 提交记录：`c3db249 fix: execute schema statements one by one...`

## 今日知识点

### 调试方法论（最重要的一课）
- **读报错先找共同点**：两个测试的报错其实在说同一件事（只有 assignments 一张表）
- **证据链**：库里只有 `[assignments, sqlite_sequence]` → 只有第一条 CREATE 执行了
- **根因**：xerial sqlite-jdbc 的 `Statement.execute()` 一次只执行**一条** SQL（只 prepare 第一条，其余**静默丢弃**不报错）。官方 issue #277 证实
- **静默失败比报错阴险**——报错立刻知道，静默失败要等测试来抓
- 修复：`SCHEMA.split(";")` 切成单条 + 循环 execute + `isBlank()` 过滤空段
- 提交前缀 `fix:` 首次登场
- 教训：计划是人写的会出错；测试是机器跑的，把 bug 挡在了提交前

### 工程概念
- **幂等**（idempotent）：`IF NOT EXISTS` 让操作执行 N 遍结果一样——贯穿后端的概念
- **增量编译**：`Nothing to compile - all classes are up to date` = Maven 只重编改动过的代码
- **生命周期现场**（mvn test 完整输出）：resources → compile → testCompile → surefire
- `git add -A` 时机：一次工作单元完成、改动杂（增+删+改）时，commit 前执行；铁律是前面 `git status --short` 检查、后面 commit；不该在"要分批提交/没检查状态/有半成品"时用
- `git status` 干净 = 没有未提交改动，是"就绪"信号

## 文件与方法链路（Task 4）

### 文件职责

- `Database.java`：负责打开 SQLite 连接、初始化表结构、提供连接、关闭连接。
- `DatabaseTest.java`：验证建库建表成功、数据能持久化。

### 依赖关系

```text
DatabaseTest
   ↓ new Database(dbFile)
Database 构造方法
   ↓ 创建目录 + 打开连接
SQLite 文件
   ↓ 执行 PRAGMA + SCHEMA
7 张业务表
```

### 主要方法链路

- `Database(Path dbFile)`：
  `Files.createDirectories(父目录)` → `DriverManager.getConnection(...)` → 执行 PRAGMA → 逐条执行 SCHEMA
- `conn()`：把内部 `Connection` 交给外部使用
- `close()`：关闭 `Connection`
- `SCHEMA`：静态常量，存放 7 张表的建表 SQL

## 明天接续点

**Task 5：StoredItem + ItemRepository（第一套真 CRUD，任务量高峰）**

1. 先 `git status`：会看到今天的总结笔记 `??`（我故意没提交，留给你练 add/commit）
2. 按计划文档 Task 5 顺序：
   - 写 `ItemRepositoryTest.java`（4 个测试，src\test\java\com\campus\agent\store\）
   - `mvn -q -Dtest=ItemRepositoryTest test` 看红
   - 写 `StoredItem.java` + `ItemRepository.java`（src\main\java\com\campus\agent\store\）
   - 跑绿 → `git add -A` → 提交（feat: 前缀）
3. 预习三问（还没答）：
   - `PreparedStatement` vs 字符串拼接 SQL，区别？（安全相关，面试必考）
   - `Statement.RETURN_GENERATED_KEYS` 干嘛的？（自增 id 怎么拿回来）
   - `try (资源) { }` 叫什么语法？括号里放什么？（Database.java 里已用过）

## 明日节奏建议

- 代码量大，按测试方法拆小步：写一个测试方法 → 跑一次，比一次写完全部再跑更容易定位
- 卡住发报错原样；绿了发输出验收
