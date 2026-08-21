# 第 5 天总结：理解回填 · Task 9 真实 API 冒烟（2026-08-20）

> 今日状态：Task 9 完成，真实 API 冒烟通过；修复了 pom.xml 和 Prompts.java 两个问题

## 今日范围

- 无新代码
- 修复：
  - `pom.xml`：删除错误的 `<commandlineArgs>`
  - `Prompts.java`：给 `CORRECT` 提示词补日期格式要求

## 今日知识点

### 1. 冒烟模式 vs REPL

- **冒烟模式**：启动时带参数，程序把每个参数当作一条用户消息，处理完退出。
  ```powershell
  mvn -q exec:java "-Dexec.args=高数作业习题5.2，11月20日前交"
  ```
- **REPL**：交互模式，启动后循环等待输入，直到 `/quit` 退出。
  ```powershell
  mvn -q exec:java
  ```
- `你 >` 和 `助手 >` 只是显示用的说话人标记。

### 2. pom.xml 的坑

- 原配置：
  ```xml
  <commandlineArgs>-Dfile.encoding=UTF-8</commandlineArgs>
  ```
- 问题：这会把 `-Dfile.encoding=UTF-8` 当作 **Main 的程序参数**传给 `args`，导致冒烟模式的用户消息永远传不进去。
- 修复：删除 `<commandlineArgs>`。
- 正确做法：UTF-8 编码通过 `MAVEN_OPTS` 设置，不写在程序参数里。

### 3. 编码设置

- 统一 UTF-8：
  ```powershell
  chcp 65001
  [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
  $env:MAVEN_OPTS="-Dfile.encoding=UTF-8"
  ```
- 只执行 `chcp 65001` 不一定够，PowerShell 解码 Maven 输出时可能仍按旧编码。

### 4. 纠正功能依赖内存状态

- `lastInsertedId` 和 `lastInsertedType` 是 `AssistantService` 的**内存变量**。
- 每次 `mvn exec:java` 都是新进程，内存会重置。
- 所以“录入”和“纠正”必须在**同一次运行**里完成：
  ```powershell
  mvn -q exec:java "-Dexec.args=高数作业习题5.2，11月20日前交 不对，截止是11月21日"
  ```
- 或者进入 REPL 后连续输入。

### 5. LLM 返回格式可能不符合校验

- 冒烟时 LLM 把 `11月21日` 直接作为 dueDate，导致 `validate()` 报格式错误。
- 根因：`CORRECT` 提示词没有明确要求日期格式。
- 修复：在 `CORRECT` 中补上：
  - dueDate 必须是 `yyyy-MM-dd`
  - dueTime 必须是 `HH:mm`

### 6. 数据库持久化

- 数据保存在 `data/agent.db`，关掉控制台不会丢失。
- 测试用 JUnit 临时目录，不会污染真实数据库。
- 想清空可以删除 `data/agent.db`，下次启动会自动重建。

## 文件与方法链路

### 涉及文件

- `pom.xml`：Maven 配置，修复 exec 参数传递。
- `Prompts.java`：提示词模板，修复 CORRECT 日期格式。
- `Main.java`：命令行入口，冒烟/REPL 模式。
- `AssistantService.java`：核心编排，纠正依赖内存状态。

### 冒烟链路

```text
mvn exec:java -Dexec.args=...
   ↓
Main.main(args)
   ↓
AppConfig.load() → 组装对象
   ↓
service.handle(每条消息)
   ↓
录入/提问/纠正 → 数据库
```

## 今天答错的点（复习重点）

1. **`chcp 65001` 后中文仍然乱码？** 可能还需要设置 `[Console]::OutputEncoding` 和 `MAVEN_OPTS`。
2. **冒烟模式传参失败？** 根因是 pom.xml 的 `<commandlineArgs>` 把固定参数传给了 Main。
3. **单独跑纠正提示“还没有刚记录的信息”？** 不是 bug，是内存状态在每次新进程会重置，必须同一次运行里先录入再纠正。
4. **纠正失败 dueDate 格式错？** 是 LLM 返回了“11月21日”，需要提示词明确要求 `yyyy-MM-dd`。

## 明日接续点

- Task 10 已完成。
- 阶段 1 完成验收清单待做。
