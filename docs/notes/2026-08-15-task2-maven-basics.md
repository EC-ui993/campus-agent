# 学习笔记 · Task 2：Maven 项目骨架三件套

> 日期：2026-08-15
> 用途：忘了随时回看。以后每个阶段都往这个目录加笔记，形成自己的知识库。

---

## 1. pom.xml —— Maven 的"项目说明书"

一句话：Maven 读它来知道三件事——**这项目是谁（坐标）、要用哪些库（依赖）、怎么编译测试（构建配置）**。

记忆锚点：`pom.xml = 身份证（坐标）+ 购物清单（依赖）+ 施工说明（插件）`

### 1.1 文件头（固定模板，照抄，不用背）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" ...>
```

- `encoding="UTF-8"`：保证中文不乱码
- `xmlns` 那一大串：XML 命名空间，声明这个 XML 按 Maven 格式校验

### 1.2 `<modelVersion>4.0.0</modelVersion>`（必要，永远写 4.0.0）

- 是 **pom 格式本身的版本号**，不是项目版本号
- 不写 → 报错 `'modelVersion' is missing.`；写错 → 报错 unsupported
- 为什么是 4.0.0：pom 格式从 Maven 2（2005 年）定型后从没变过，二十年一直兼容
- 项目版本号是另一个标签：`<version>1.0.0</version>`

### 1.3 项目坐标 GAV（项目身份证号）

```xml
<groupId>com.campus</groupId>
<artifactId>campus-agent</artifactId>
<version>1.0.0</version>
```

| 项 | 含义 | 类比 |
|---|---|---|
| groupId | 组织名，惯例倒写域名 | 姓氏 |
| artifactId | 项目名 | 名字 |
| version | 版本号 | 出生年份 |

三个拼起来 `com.campus:campus-agent:1.0.0` 全球唯一。Maven 靠它去中央仓库（Maven Central）精确取库。

### 1.4 `<packaging>jar</packaging>`

编译产物格式。jar = Java 压缩包，装 .class 文件。

### 1.5 properties 编译参数

```xml
<maven.compiler.release>17</maven.compiler.release>
<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
```

- `release 17`：生成兼容 Java 17 的字节码（JDK 21 也能编译 17 目标，企业常规操作）
- `sourceEncoding UTF-8`：源码按 UTF-8 读

### 1.6 dependencies —— 最核心的"购物清单"

每个 `<dependency>` 是别人写好的库，同样用 GAV 坐标：

| 库 | 作用 |
|---|---|
| sqlite-jdbc | Java 读写 SQLite 数据库 |
| jackson-databind | 解析/生成 JSON（LLM 返回的就是 JSON） |
| junit-jupiter | 写单元测试 |

两个关键理解：
1. 企业开发就是"组装库"，不是从零造轮子
2. `mvn compile` 时 Maven 把库（含传递依赖）下载到 `C:\Users\bkonw\.m2\repository\` 缓存，放进 classpath

### 1.7 `<scope>test</scope>`（JUnit 里有）

- 限定库**只在测试阶段可用**，最终打包的 jar 不含它
- 没写 scope 默认 `compile`（编译和运行都要）

### 1.8 build/plugins —— 给 Maven 自己用的工具

| 插件 | 作用 |
|---|---|
| maven-surefire-plugin | 让 `mvn test` 能发现并运行 JUnit 测试 |
| exec-maven-plugin | 让 `mvn exec:java` 直接启动 Main 类（`<mainClass>` 指定入口） |

依赖是给你的代码用的；插件是给 Maven 扩展能力的。

### 1.9 Maven 生命周期（面试常问）

```
validate → compile → test → package → install → deploy
```

- `mvn compile`：编译到 .class 就停
- `mvn test`：先编译再跑测试
- `mvn package`：最后打 jar 包

---

## 2. .gitignore —— git 的"黑名单"

一句话：git 每次检查改动前先看这份名单，名单上的东西假装不存在。

### 2.1 逐行解释

| 行 | 为什么忽略 |
|---|---|
| `target/` | Maven 编译产物目录，随时能重新编译出来，是"可再生垃圾" |
| `data/` | 数据库目录，真实个人数据（课表/作业/老师信息），隐私 + 不是代码 |
| `config.properties` | 里面有 API Key，一旦进 git 历史就永远删不掉，等于泄露钱包密码 |
| `.idea/` | IDEA 个人工程配置（窗口布局等），只属于你电脑 |
| `*.iml` | IDEA 自动生成的模块文件 |

### 2.2 语法规则

1. 结尾 `/` = 忽略目录；不带 = 忽略文件
2. `*` = 通配符
3. 每行一条规则，`#` 开头是注释

### 2.3 三个关键理解

1. **`.example` 模板模式**：`config.properties.example` 进仓库（只有占位符没密码），真 `config.properties` 进黑名单。企业标配。
2. **只对未跟踪文件生效**：已提交过的文件再加进 .gitignore 无效。所以要在第一次提交前写好规则。
3. 忽略清单三类东西：能重新编译的（target/）、有隐私的（data/、key）、只属于你电脑的（.idea/）。
4. **规则先于事实**：.gitignore 是"规则文件"而非"执行记录"——被忽略的文件现在不存在没关系，规则是预先埋伏，等它出现（如 Task 9 复制出 config.properties）那一刻自动生效。规则必须在文件出现前写好，晚了就失效。

---

## 3. config.properties —— 程序的"配置单"

### 3.1 每行作用

```properties
api.key=在这里填入你的DeepSeek API Key   ← 必须写：API 门禁卡，没有它程序启动报错
model=deepseek-chat                    ← 有默认值，可省
base.url=https://api.deepseek.com      ← 有默认值，可省
db.path=data/agent.db                  ← 有默认值，可省
```

- `api.key` 有两个来源：**环境变量 `DEEPSEEK_API_KEY` 优先，配置文件兜底**
- 其余三项代码里有默认值（`p.getProperty("model", "deepseek-chat")` 的第二个参数）
- 全写出来是好习惯：改模型/数据库位置只动配置不动代码（**配置与代码分离**）

### 3.2 .properties 文件格式规则（只有两条）

1. 每行 `key=value`
2. 注释用 `#` 开头

**没有声明行**——不要写一个孤零零的 `properties` 在第一行（会被当成空键的脏数据）。

### 3.3 安全规则

- `.example` 里只放占位符，**真 key 只填进被 .gitignore 保护的 `config.properties`**
- 真 key 永远不进 git

---

## 4. 报错速查表（本阶段遇到的）

| 报错关键句 | 含义 | 解法 |
|---|---|---|
| `no POM in this directory (C:\Users\bkonw)` | 在错误目录跑了 mvn | `cd D:\VibeCoding\deepseekHarness\myAgent` 再跑 |
| `The term 'xxx' is not recognized` | 命令拼错或没装 | 检查拼写/安装 |
| `cannot find symbol` | 引用了不存在的类/方法 | 检查 import 和类名 |
| 中文乱码 | 终端编码不是 UTF-8 | 先执行 `chcp 65001` |

**读报错习惯**：先读第一行，它通常直接说答案。

## 5. 防身命令

```powershell
pwd            # 我在哪个目录
ls             # 这目录里有什么
chcp 65001     # 切 UTF-8 防乱码（每次新开终端都要）
```

跑 Maven 前先 `pwd` 确认在项目根目录——`ls` 能看到 `pom.xml` 就对了。

---

## 6. git 基础（2026-08-15 补充）

### 6.1 三个区域模型

```
工作区（你的文件） --git add--> 暂存区 --git commit--> 版本库（历史记录）
```

- `git add` = 选入购物车（暂存），可只挑部分文件提交
- `git commit` = 结账留底（写入历史，永久保存）

### 6.2 命令通用格式

```
git <子命令> [选项] [参数]
```

- 子命令：动作（add / commit / status / log / diff）
- 选项：以 `-` 开头的开关。`-m` = `--message` 缩写（附提交说明）；`--global` = 全局配置；`-c name=value` = 本次临时覆盖
- 参数：动作对象（文件名、提交说明）

不加 `-m` 会弹编辑器让你写说明——新手永远带 `-m`。

### 6.3 高频命令

```powershell
git status     # 当前状态：改动/暂存情况（随时跑）
git log        # 提交历史（q 退出）
git diff       # 看具体改了哪些行
git add .      # 全部改动入暂存区（.gitignore 的除外）
```

### 6.4 首次提交报 `Author identity unknown`？

git 需要知道你是谁才能写历史。一次性设置（全局）：

```powershell
git config --global user.name "你的名字"
git config --global user.email "你的邮箱"
```

设完重新执行 `git commit` 即可。不想暴露邮箱可用 GitHub 的 `用户名@users.noreply.github.com` 匿名邮箱。

### 6.5 LF/CRLF 警告是什么

- Windows 用 CRLF 换行，Linux/Mac 用 LF，git 内部统一存 LF
- `git add` 时警告 `LF will be replaced by CRLF` 是正常现象，不是错误
- Windows 用户一次性设置：`git config --global core.autocrlf true`（提交时转 LF 存库、检出时转回 CRLF）

### 6.6 `git log --oneline` 与提交署名

- `--oneline` = 每条提交压成一行显示
- 历史里的 `user <user@local>` 是 AI 代提交时用 `git -c user.name=... user.email=...` 一次性身份写的，属正常历史记录，不要用 rebase 改写
- git 身份靠自觉，不做验证

---

## 7. 提交说明的写法：Conventional Commits（约定式提交）

格式：`类型: 描述`，如 `docs: add git basics section to task 2 notes`

| 前缀 | 含义 | 例子 |
|---|---|---|
| `feat:` | 新功能 | `feat: ItemRepository jdbc crud` |
| `fix:` | 修 bug | `fix: parse empty date` |
| `docs:` | 文档 | `docs: learning notes` |
| `build:` | 构建/依赖 | `build: maven skeleton` |
| `test:` | 测试 | `test: regression suite` |
| `chore:` | 杂务 | `chore: update gitignore` |

好处：`git log` 一屏扫出功能/修复/文档；面试官看 GitHub 时干净的历史是加分项。
