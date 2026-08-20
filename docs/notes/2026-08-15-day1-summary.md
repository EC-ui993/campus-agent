# 第 1 天总结（2026-08-15）

> 项目：校园事务 + 求职成长双引擎助手（Java 17 目标 / JDK 21 编译 / Maven 3.9.9 / SQLite）
> 今日状态：Task 2、Task 3 完成，工作区干净，测试 6/6 绿

## 今日战果

- 环境：Maven 3.9.9 装好（winget 不行 → 手动装 D:\tools），JDK 21、git 已有
- Task 2 完成：pom.xml / .gitignore / config.properties.example，`mvn -q compile` 通过
- Task 3 完成：ExtractedItem record + 6 个单元测试，完整走了 TDD 红→绿
- 提交记录（git log --oneline 可查）：build 骨架、笔记×3、feat ExtractedItem

## 知识点清单

### Maven
- pom.xml = 身份证（GAV 坐标）+ 购物清单（dependencies）+ 施工说明（plugins）
- modelVersion 4.0.0 是 pom 格式版本，永远写死
- scope test = 库只在测试阶段用；生命周期 validate→compile→test→package
- 约定目录：src/main/java（生产）、src/test/java（测试）、target/（产物）
- `mvn -q` = 安静模式，成功无输出；`echo $LASTEXITCODE` 看退出码（0=成功）

### 配置与文件
- .properties 格式：每行 key=value，注释用 #，没有声明行
- .gitignore = 规则文件，规则先于事实（config.properties 出现前就埋伏好）
- .example 模板模式：模板进仓库，真配置（含 key）进黑名单

### Git
- 三区域：工作区 →（add）暂存区 →（commit）版本库
- 命令格式：git <子命令> [选项] [参数]；-m=--message；--global；-c 一次性覆盖
- 首次提交前设身份：git config --global user.name / user.email
- LF/CRLF 警告无害；Windows 设 core.autocrlf true
- Conventional Commits：feat/fix/docs/build/test/chore: 描述
- git status 状态码：A 已暂存、D 删除、?? 未跟踪、AD 混合
- git add -A = 让暂存区与磁盘完全同步
- IDEA 弹窗"添加到 git" = 自动执行 git add（学习期建议关闭，保持手感）

### Java 与工程
- 包名 = 目录路径；public 类文件名 = 类名
- record：只装数据的类（字段自动 getter）；fromJson 静态工厂；validate 返回错误列表
- 报错先读第一行：no POM in this directory = 跑错目录；找不到符号 = 类不存在

### TDD
- 节奏：红→绿→重构
- 先红的三个理由：①证明测试真在执行（防假绿）②逼你先定接口再写实现 ③防碰巧通过

### 工具
- Java 用 IDEA（学生邮箱免费领 Ultimate）；学习期坚持终端跑 mvn
- 防身命令：pwd / ls / chcp 65001

## 文件与方法链路（Task 2 + Task 3）

### Task 2：Maven 骨架三件套

- `pom.xml`：Maven 项目说明书（坐标 + 依赖 + 插件）
- `.gitignore`：git 黑名单（忽略 target/、data/、config.properties、.idea/ 等）
- `config.properties.example`：配置模板（真 key 只放本地 `config.properties`）

关系：`pom.xml` 决定怎么构建；`.gitignore` 保护不该进 git 的文件；`.example` 提供配置模板。

### Task 3：ExtractedItem

- `ExtractedItem.java`：LLM 抽取结果模型，`fromJson` 解析 JSON，`validate` 校验。
- `ExtractedItemTest.java`：6 个测试，验证解析、空字段、必填、类型、日期格式、坏 JSON。

链路：JSON → `ExtractedItem.fromJson()` → `ExtractedItem` → `validate()` → 错误列表

## 明天接续点

**从 Task 4 开始**（Database 建库建表 + DatabaseTest 2 个测试）。

1. 先跑 `git status`——应看到笔记文件 `docs/notes/2026-08-15-day1-summary.md` 显示 `??`，顺手提交它练手
2. 预习问题：
   - `CREATE TABLE IF NOT EXISTS` 中 IF NOT EXISTS 有什么用？
   - `PRAGMA journal_mode=WAL` 是干嘛的？（提示：设计文档第 8 节）
   - Java 里 `""" ... """` 三引号语法叫什么？
3. 按计划文档 Task 4 走 TDD：先写 DatabaseTest → 看红 → 写 Database → 看绿 → 提交

## 遇到的坑（都解决了）

| 坑 | 解法 |
|---|---|
| winget 找不到 Maven 包 | 手动下载 zip 解压 D:\tools + PATH |
| mvn 报 no POM | cd 到项目根目录 |
| 首次 commit 报身份未知 | git config --global user.name/email |
| 测试文件建成了带点号的长文件名 | 包名=目录，手动建目录+改名 |
| git 里出现 AD 幽灵条目 | git add -A 同步 |
