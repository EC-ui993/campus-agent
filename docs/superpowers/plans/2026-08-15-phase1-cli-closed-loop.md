# 阶段 0-1：命令行版最小闭环 · 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 跑通「粘贴老师消息 → LLM 抽取结构化信息 → 存入 SQLite → 命令行问答/纠正」的最小闭环。

**Architecture:** 纯 Java 17 控制台程序（无框架）：`Main` 提供命令行 REPL，`AssistantService` 编排意图分类、信息抽取、问答与纠正，`DeepSeekClient` 封装 HTTP 调用，`ItemRepository` + `Database` 用裸 JDBC 读写 SQLite。所有 LLM 交互走 `LlmClient` 接口，测试用 Fake 实现离线驱动。

**Tech Stack:** Java 17、Maven、sqlite-jdbc、Jackson、JUnit 5、DeepSeek chat/completions API（JSON 模式）。

**用户须知（学习提示）：** 本计划既是施工方案也是你的教材。每个任务的代码都完整给出，建议每写一段都手动敲一遍而不是复制——尤其是 SQL 和 Jackson 用法。跑不通时把报错原样发给我。

---

## 文件结构总览（本计划将创建的全部文件）

```
myAgent/
├─ pom.xml                                  Maven 构建（依赖、编译、exec 插件）
├─ config.properties.example                配置模板（复制成 config.properties 填 key）
├─ .gitignore                               忽略 target/ data/ config.properties
├─ src/main/java/com/campus/agent/
│  ├─ Main.java                             REPL 入口 + /list /help /quit + 冒烟参数模式
│  ├─ AppConfig.java                        读取环境变量/config.properties
│  ├─ Prompts.java                          四个提示词模板（分类/抽取/纠正/问答）
│  ├─ llm/
│  │  ├─ LlmClient.java                     接口：chat / chatJson
│  │  ├─ LlmException.java                  运行时异常
│  │  └─ DeepSeekClient.java                HttpClient 实现 + buildBody/parseContent（可单测）
│  ├─ model/
│  │  └─ ExtractedItem.java                 record：抽取结果 + 解析/校验
│  ├─ store/
│  │  ├─ Database.java                      打开/建库建表（SQLite, WAL）
│  │  ├─ StoredItem.java                    record：查询出来的行
│  │  └─ ItemRepository.java                JDBC CRUD（insert/updateById/getById/allItems/消息/兜底）
│  └─ service/
│     └─ AssistantService.java              编排：分类→抽取→校验→入库→回执；问答；纠正
└─ src/test/java/com/campus/agent/
   ├─ testing/FakeLlm.java                  按队列吐出预设响应的假 LLM
   ├─ model/ExtractedItemTest.java
   ├─ store/DatabaseTest.java
   ├─ store/ItemRepositoryTest.java
   ├─ llm/DeepSeekClientTest.java
   ├─ service/AssistantServiceTest.java
   ├─ PromptsTest.java
   └─ SampleMessagesTest.java               10 条典型老师消息回归
```

---

## Task 1: 环境准备（阶段 0）

**Files:** 无代码文件。

- [ ] **Step 1: 验证 JDK 17**

在 PowerShell 运行：
```powershell
java -version
```
Expected：出现 `openjdk version "17.x"`（或 21.x，均可）。若提示找不到命令，去 <https://adoptium.net/temurin/releases/> 下载 Temurin 17 MSI 安装，安装后**重开终端**再验证。

- [ ] **Step 2: 验证 Maven**

```powershell
mvn -version
```
Expected：`Apache Maven 3.9.x` 且 `Java version: 17.x`。若没有：<https://maven.apache.org/download.cgi> 下载 Binary zip，解压到 `D:\tools\apache-maven-3.9.x`，然后把 `D:\tools\apache-maven-3.9.x\bin` 加入系统环境变量 Path，重开终端验证。

- [ ] **Step 3: 准备 DeepSeek API Key**

打开 <https://platform.deepseek.com> 注册并充值少量金额（¥10 足够本阶段用很久），在「API keys」页创建一个 key 并保存好（只显示一次）。

- [ ] **Step 4: 设置终端 UTF-8（防中文乱码）**

以后每次打开 PowerShell 跑本程序前执行一次：
```powershell
chcp 65001
```
Expected：`Active code page: 65001`

- [ ] **Step 5: （可选）安装 DB Browser for SQLite**

<https://sqlitebrowser.org/dl/> 下载安装。以后想看数据库里的数据，用它打开 `data/agent.db` 即可。

- [ ] **Step 6: 确认工作目录**

```powershell
cd D:\VibeCoding\deepseekHarness\myAgent
git log --oneline
```
Expected：能看到之前的设计文档 commit（`docs: campus-agent design spec ...`）。

---

## Task 2: Maven 项目骨架

**Files:**
- Create: `pom.xml`
- Create: `.gitignore`
- Create: `config.properties.example`

- [ ] **Step 1: 写 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>com.campus</groupId>
  <artifactId>campus-agent</artifactId>
  <version>1.0.0</version>
  <packaging>jar</packaging>

  <properties>
    <maven.compiler.release>17</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.xerial</groupId>
      <artifactId>sqlite-jdbc</artifactId>
      <version>3.46.1.3</version>
    </dependency>
    <dependency>
      <groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId>
      <version>2.17.2</version>
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>5.10.3</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>3.2.5</version>
      </plugin>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <version>3.2.0</version>
        <configuration>
          <mainClass>com.campus.agent.Main</mainClass>
          <commandlineArgs>-Dfile.encoding=UTF-8</commandlineArgs>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: 写 .gitignore**

```
target/
data/
config.properties
.idea/
*.iml
```

- [ ] **Step 3: 写 config.properties.example**

```properties
# 复制本文件为 config.properties 并填入你的 key（也可以不复制，改用环境变量 DEEPSEEK_API_KEY，环境变量优先）
api.key=在这里填入你的DeepSeek API Key
model=deepseek-chat
base.url=https://api.deepseek.com
db.path=data/agent.db
```

- [ ] **Step 4: 验证 Maven 能拉依赖**

Run: `mvn -q compile`
Expected: `BUILD SUCCESS` 无 ERROR（首次会下载依赖，需要几分钟）。

- [ ] **Step 5: Commit**

```powershell
git add pom.xml .gitignore config.properties.example
git commit -m "build: maven skeleton with sqlite/jackson/junit deps"
```

---

## Task 3: ExtractedItem 抽取结果模型（TDD）

**Files:**
- Create: `src/main/java/com/campus/agent/model/ExtractedItem.java`
- Create: `src/test/java/com/campus/agent/model/ExtractedItemTest.java`

- [ ] **Step 1: 写失败测试**

`src/test/java/com/campus/agent/model/ExtractedItemTest.java`：

```java
package com.campus.agent.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExtractedItemTest {

    @Test
    void parsesValidJson() {
        ExtractedItem item = ExtractedItem.fromJson("""
                {"type":"assignment","title":"习题5.2","course":"高数","teacher":"王老师",
                 "content":"完成5.2全部习题","location":"","dueDate":"2026-11-20","dueTime":"23:59"}
                """);
        assertEquals("assignment", item.type());
        assertEquals("习题5.2", item.title());
        assertEquals("高数", item.course());
        assertEquals("2026-11-20", item.dueDate());
        assertTrue(item.validate().isEmpty());
    }

    @Test
    void blankOptionalFieldsBecomeNull() {
        ExtractedItem item = ExtractedItem.fromJson(
                "{\"type\":\"todo\",\"title\":\"领材料\",\"course\":\"  \",\"teacher\":\"\",\"content\":\"\",\"location\":\"\",\"dueDate\":\"\",\"dueTime\":\"\"}");
        assertNull(item.course());
        assertNull(item.dueDate());
        assertTrue(item.validate().isEmpty());
    }

    @Test
    void rejectsMissingTitle() {
        ExtractedItem item = ExtractedItem.fromJson(
                "{\"type\":\"assignment\",\"title\":\"\",\"course\":\"\",\"teacher\":\"\",\"content\":\"\",\"location\":\"\",\"dueDate\":\"\",\"dueTime\":\"\"}");
        List<String> errors = item.validate();
        assertTrue(errors.stream().anyMatch(e -> e.contains("title")), "应报 title 错误，实际: " + errors);
    }

    @Test
    void rejectsInvalidType() {
        ExtractedItem item = ExtractedItem.fromJson(
                "{\"type\":\"poem\",\"title\":\"随便\",\"course\":\"\",\"teacher\":\"\",\"content\":\"\",\"location\":\"\",\"dueDate\":\"\",\"dueTime\":\"\"}");
        List<String> errors = item.validate();
        assertTrue(errors.stream().anyMatch(e -> e.contains("type")), "应报 type 错误，实际: " + errors);
    }

    @Test
    void rejectsBadDateFormat() {
        ExtractedItem item = ExtractedItem.fromJson(
                "{\"type\":\"exam\",\"title\":\"期中\",\"course\":\"\",\"teacher\":\"\",\"content\":\"\",\"location\":\"\",\"dueDate\":\"11月20日\",\"dueTime\":\"\"}");
        List<String> errors = item.validate();
        assertTrue(errors.stream().anyMatch(e -> e.contains("dueDate")), "应报 dueDate 错误，实际: " + errors);
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(RuntimeException.class, () -> ExtractedItem.fromJson("这不是JSON"));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q -Dtest=ExtractedItemTest test`
Expected: FAIL（`package com.campus.agent.model does not exist` / 编译错误）。

- [ ] **Step 3: 写实现**

`src/main/java/com/campus/agent/model/ExtractedItem.java`：

```java
package com.campus.agent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * LLM 抽取结果。type 取值：assignment 作业 / exam 考试 / todo 待办 / course 课程 / event 日程。
 * 除 type、title 外的字段可为 null（表示无该信息）。
 */
public record ExtractedItem(
        String type, String title, String course, String teacher,
        String content, String location, String dueDate, String dueTime) {

    public static final Set<String> VALID_TYPES =
            Set.of("assignment", "exam", "todo", "course", "event");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ExtractedItem fromJson(String json) {
        final JsonNode n;
        try {
            n = MAPPER.readTree(json);
        } catch (IOException e) {
            throw new RuntimeException("抽取结果不是合法 JSON: " + e.getMessage());
        }
        return new ExtractedItem(
                blankToNull(n.path("type").asText("")),
                blankToNull(n.path("title").asText("")),
                blankToNull(n.path("course").asText("")),
                blankToNull(n.path("teacher").asText("")),
                blankToNull(n.path("content").asText("")),
                blankToNull(n.path("location").asText("")),
                blankToNull(n.path("dueDate").asText("")),
                blankToNull(n.path("dueTime").asText("")));
    }

    /** 校验抽取结果；返回错误列表，空列表表示合法。 */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (type == null || !VALID_TYPES.contains(type)) {
            errors.add("type 必须是 assignment/exam/todo/course/event 之一，收到: " + type);
        }
        if (title == null || title.isBlank()) {
            errors.add("title 不能为空");
        }
        if (dueDate != null && !dueDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
            errors.add("dueDate 格式应为 yyyy-MM-dd，收到: " + dueDate);
        }
        if (dueTime != null && !dueTime.matches("\\d{2}:\\d{2}")) {
            errors.add("dueTime 格式应为 HH:mm，收到: " + dueTime);
        }
        return errors;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn -q -Dtest=ExtractedItemTest test`
Expected: `Tests run: 6, Failures: 0, Errors: 0` 且 BUILD SUCCESS。

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/campus/agent/model/ExtractedItem.java src/test/java/com/campus/agent/model/ExtractedItemTest.java
git commit -m "feat: ExtractedItem model with json parsing and validation"
```

---

## Task 4: Database 建库建表（TDD）

**Files:**
- Create: `src/main/java/com/campus/agent/store/Database.java`
- Create: `src/test/java/com/campus/agent/store/DatabaseTest.java`

- [ ] **Step 1: 写失败测试**

`src/test/java/com/campus/agent/store/DatabaseTest.java`：

```java
package com.campus.agent.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseTest {

    @Test
    void createsDbFileAndAllTables(@TempDir Path tmp) throws Exception {
        Path dbFile = tmp.resolve("test.db");
        try (Database db = new Database(dbFile)) {
            assertNotNull(db.conn());
            try (Statement st = db.conn().createStatement();
                 ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
                Set<String> tables = new HashSet<>();
                while (rs.next()) tables.add(rs.getString("name"));
                assertTrue(tables.containsAll(Set.of(
                        "assignments", "exams", "todos", "courses", "events", "messages", "raw_inbox")),
                        "缺少表: " + tables);
            }
        }
        assertTrue(Files.exists(dbFile));
    }

    @Test
    void secondOpenKeepsData(@TempDir Path tmp) throws Exception {
        Path dbFile = tmp.resolve("reopen.db");
        try (Database db = new Database(dbFile)) {
            try (Statement st = db.conn().createStatement()) {
                st.executeUpdate("INSERT INTO messages(role, content) VALUES('user','你好')");
            }
        }
        try (Database db = new Database(dbFile);
             Statement st = db.conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) AS c FROM messages")) {
            rs.next();
            assertEquals(1, rs.getInt("c"));
        }
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q -Dtest=DatabaseTest test`
Expected: FAIL（`cannot find symbol: class Database`）。

- [ ] **Step 3: 写实现**

`src/main/java/com/campus/agent/store/Database.java`：

```java
package com.campus.agent.store;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** 打开（必要时创建）SQLite 数据库并初始化表结构。用完必须 close()。 */
public class Database implements AutoCloseable {

    private final Connection conn;

    public Database(Path dbFile) throws SQLException {
        try {
            if (dbFile.getParent() != null) {
                Files.createDirectories(dbFile.getParent());
            }
        } catch (Exception e) {
            throw new SQLException("无法创建数据目录: " + dbFile.getParent(), e);
        }
        conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL;");
            st.execute("PRAGMA busy_timeout=5000;");
            st.execute(SCHEMA);
        }
    }

    public Connection conn() {
        return conn;
    }

    @Override
    public void close() throws SQLException {
        conn.close();
    }

    private static final String SCHEMA = """
            CREATE TABLE IF NOT EXISTS assignments(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              title TEXT NOT NULL,
              course TEXT,
              teacher TEXT,
              content TEXT,
              due_date TEXT,
              due_time TEXT,
              status TEXT NOT NULL DEFAULT 'pending',
              source_message_id INTEGER,
              created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')));
            CREATE TABLE IF NOT EXISTS exams(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              title TEXT NOT NULL,
              course TEXT,
              teacher TEXT,
              content TEXT,
              location TEXT,
              due_date TEXT,
              due_time TEXT,
              status TEXT NOT NULL DEFAULT 'upcoming',
              source_message_id INTEGER,
              created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')));
            CREATE TABLE IF NOT EXISTS todos(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              title TEXT NOT NULL,
              content TEXT,
              due_date TEXT,
              due_time TEXT,
              status TEXT NOT NULL DEFAULT 'pending',
              source_message_id INTEGER,
              created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')));
            CREATE TABLE IF NOT EXISTS courses(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              title TEXT NOT NULL,
              teacher TEXT,
              location TEXT,
              source_message_id INTEGER,
              created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')));
            CREATE TABLE IF NOT EXISTS events(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              title TEXT NOT NULL,
              content TEXT,
              location TEXT,
              due_date TEXT,
              due_time TEXT,
              status TEXT NOT NULL DEFAULT 'upcoming',
              source_message_id INTEGER,
              created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')));
            CREATE TABLE IF NOT EXISTS messages(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              role TEXT NOT NULL,
              content TEXT NOT NULL,
              created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')));
            CREATE TABLE IF NOT EXISTS raw_inbox(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              content TEXT NOT NULL,
              reason TEXT,
              created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')));
            """;
}
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn -q -Dtest=DatabaseTest test`
Expected: `Tests run: 2, Failures: 0, Errors: 0` 且 BUILD SUCCESS。

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/campus/agent/store/Database.java src/test/java/com/campus/agent/store/DatabaseTest.java
git commit -m "feat: Database opens sqlite and creates 7 core tables"
```

---

## Task 5: StoredItem + ItemRepository 读写（TDD）

**Files:**
- Create: `src/main/java/com/campus/agent/store/StoredItem.java`
- Create: `src/main/java/com/campus/agent/store/ItemRepository.java`
- Create: `src/test/java/com/campus/agent/store/ItemRepositoryTest.java`

- [ ] **Step 1: 写失败测试**

`src/test/java/com/campus/agent/store/ItemRepositoryTest.java`：

```java
package com.campus.agent.store;

import com.campus.agent.model.ExtractedItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ItemRepositoryTest {

    @Test
    void insertAndListRoundTrip(@TempDir Path tmp) throws Exception {
        try (Database db = new Database(tmp.resolve("r.db"))) {
            ItemRepository repo = new ItemRepository(db);
            long id = repo.insert(new ExtractedItem(
                    "assignment", "习题5.2", "高数", "王老师", "完成5.2全部", null, "2026-11-20", "23:59"), 1);
            assertTrue(id > 0);

            List<StoredItem> all = repo.allItems();
            assertEquals(1, all.size());
            StoredItem s = all.get(0);
            assertEquals("assignment", s.type());
            assertEquals("习题5.2", s.title());
            assertEquals("2026-11-20", s.dueDate());
            assertEquals("pending", s.status());
        }
    }

    @Test
    void updateByIdMergesFields(@TempDir Path tmp) throws Exception {
        try (Database db = new Database(tmp.resolve("u.db"))) {
            ItemRepository repo = new ItemRepository(db);
            long id = repo.insert(new ExtractedItem(
                    "todo", "领材料", null, null, "辅导员办公室", null, "2026-11-21", "10:00"), 1);

            boolean updated = repo.updateById(new ExtractedItem(
                    "todo", "领材料", null, null, "改去教务处", null, "2026-11-22", "10:00"), id);
            assertTrue(updated);

            Optional<StoredItem> found = repo.getById("todo", id);
            assertTrue(found.isPresent());
            assertEquals("2026-11-22", found.get().dueDate());
        }
    }

    @Test
    void insertMessageAndRawInbox(@TempDir Path tmp) throws Exception {
        try (Database db = new Database(tmp.resolve("m.db"))) {
            ItemRepository repo = new ItemRepository(db);
            long mid = repo.insertMessage("user", "老师通知原文");
            assertTrue(mid > 0);

            long rid = repo.insertRaw("解析失败的原文", "title 不能为空");
            assertTrue(rid > 0);

            assertEquals(1, repo.allItems().size()); // raw_inbox 不属于 allItems
        }
    }

    @Test
    void allItemsCoversEveryType(@TempDir Path tmp) throws Exception {
        try (Database db = new Database(tmp.resolve("t.db"))) {
            ItemRepository repo = new ItemRepository(db);
            repo.insert(new ExtractedItem("assignment", "作业1", null, null, null, null, null, null), 1);
            repo.insert(new ExtractedItem("exam", "考试1", null, null, null, "D105", "2026-12-01", "19:00"), 1);
            repo.insert(new ExtractedItem("todo", "待办1", null, null, null, null, null, null), 1);
            repo.insert(new ExtractedItem("course", "数据结构", "张老师", "B302", null, null, null, null), 1);
            repo.insert(new ExtractedItem("event", "组会", null, null, "实验室", null, "2026-11-27", "16:00"), 1);
            assertEquals(5, repo.allItems().size());
        }
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q -Dtest=ItemRepositoryTest test`
Expected: FAIL（编译错误，类不存在）。

- [ ] **Step 3: 写 StoredItem**

`src/main/java/com/campus/agent/store/StoredItem.java`：

```java
package com.campus.agent.store;

import java.util.LinkedHashMap;
import java.util.Map;

/** 数据库查询出的一行记录（五种业务表统一形状）。 */
public record StoredItem(
        long id, String type, String title, String course, String teacher,
        String content, String location, String dueDate, String dueTime, String status) {

    /** 转成紧凑 Map（跳过 null），用于拼给 LLM 的 JSON 上下文。 */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("type", type);
        m.put("title", title);
        if (course != null) m.put("course", course);
        if (teacher != null) m.put("teacher", teacher);
        if (content != null) m.put("content", content);
        if (location != null) m.put("location", location);
        if (dueDate != null) m.put("dueDate", dueDate);
        if (dueTime != null) m.put("dueTime", dueTime);
        if (status != null) m.put("status", status);
        return m;
    }
}
```

- [ ] **Step 4: 写 ItemRepository**

`src/main/java/com/campus/agent/store/ItemRepository.java`：

```java
package com.campus.agent.store;

import com.campus.agent.model.ExtractedItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 所有数据库读写。裸 JDBC + PreparedStatement（防 SQL 注入，面试常考）。 */
public class ItemRepository {

    private final Database db;

    public ItemRepository(Database db) {
        this.db = db;
    }

    /** 按类型写入对应表，返回自增 id。 */
    public long insert(ExtractedItem item, long sourceMessageId) throws SQLException {
        Connection c = db.conn();
        String sql = switch (item.type()) {
            case "assignment" ->
                    "INSERT INTO assignments(title,course,teacher,content,due_date,due_time,source_message_id) VALUES(?,?,?,?,?,?,?)";
            case "exam" ->
                    "INSERT INTO exams(title,course,teacher,content,location,due_date,due_time,source_message_id) VALUES(?,?,?,?,?,?,?,?)";
            case "todo" ->
                    "INSERT INTO todos(title,content,due_date,due_time,source_message_id) VALUES(?,?,?,?,?)";
            case "course" ->
                    "INSERT INTO courses(title,teacher,location,source_message_id) VALUES(?,?,?,?)";
            case "event" ->
                    "INSERT INTO events(title,content,location,due_date,due_time,source_message_id) VALUES(?,?,?,?,?,?)";
            default -> throw new IllegalArgumentException("未知类型: " + item.type());
        };
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            switch (item.type()) {
                case "assignment" -> setParams(ps, item.title(), item.course(), item.teacher(),
                        item.content(), item.dueDate(), item.dueTime(), sourceMessageId);
                case "exam" -> setParams(ps, item.title(), item.course(), item.teacher(),
                        item.content(), item.location(), item.dueDate(), item.dueTime(), sourceMessageId);
                case "todo" -> setParams(ps, item.title(), item.content(), item.dueDate(), item.dueTime(), sourceMessageId);
                case "course" -> setParams(ps, item.title(), item.teacher(), item.location(), sourceMessageId);
                case "event" -> setParams(ps, item.title(), item.content(), item.location(),
                        item.dueDate(), item.dueTime(), sourceMessageId);
                default -> throw new IllegalArgumentException("未知类型: " + item.type());
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    /** 按 id 更新记录（全字段覆盖，由调用方保证已合并好）。 */
    public boolean updateById(ExtractedItem item, long id) throws SQLException {
        String sql = switch (item.type()) {
            case "assignment" ->
                    "UPDATE assignments SET title=?,course=?,teacher=?,content=?,due_date=?,due_time=? WHERE id=?";
            case "exam" ->
                    "UPDATE exams SET title=?,course=?,teacher=?,content=?,location=?,due_date=?,due_time=? WHERE id=?";
            case "todo" ->
                    "UPDATE todos SET title=?,content=?,due_date=?,due_time=? WHERE id=?";
            case "course" ->
                    "UPDATE courses SET title=?,teacher=?,location=? WHERE id=?";
            case "event" ->
                    "UPDATE events SET title=?,content=?,location=?,due_date=?,due_time=? WHERE id=?";
            default -> throw new IllegalArgumentException("未知类型: " + item.type());
        };
        try (PreparedStatement ps = db.conn().prepareStatement(sql)) {
            switch (item.type()) {
                case "assignment" -> setParams(ps, item.title(), item.course(), item.teacher(),
                        item.content(), item.dueDate(), item.dueTime(), id);
                case "exam" -> setParams(ps, item.title(), item.course(), item.teacher(),
                        item.content(), item.location(), item.dueDate(), item.dueTime(), id);
                case "todo" -> setParams(ps, item.title(), item.content(), item.dueDate(), item.dueTime(), id);
                case "course" -> setParams(ps, item.title(), item.teacher(), item.location(), id);
                case "event" -> setParams(ps, item.title(), item.content(), item.location(),
                        item.dueDate(), item.dueTime(), id);
                default -> throw new IllegalArgumentException("未知类型: " + item.type());
            }
            return ps.executeUpdate() == 1;
        }
    }

    /** 读取全部业务记录，按 作业→考试→待办→课程→日程 顺序。 */
    public List<StoredItem> allItems() throws SQLException {
        List<StoredItem> list = new ArrayList<>();
        queryInto(list, "assignment", "SELECT id,'assignment' AS type,title,course,teacher,content,NULL AS location,due_date,due_time,status FROM assignments ORDER BY id");
        queryInto(list, "exam", "SELECT id,'exam' AS type,title,course,teacher,content,location,due_date,due_time,status FROM exams ORDER BY id");
        queryInto(list, "todo", "SELECT id,'todo' AS type,title,NULL AS course,NULL AS teacher,content,NULL AS location,due_date,due_time,status FROM todos ORDER BY id");
        queryInto(list, "course", "SELECT id,'course' AS type,title,NULL AS course,NULL AS teacher,NULL AS content,location,NULL AS due_date,NULL AS due_time,'active' AS status FROM courses ORDER BY id");
        queryInto(list, "event", "SELECT id,'event' AS type,title,NULL AS course,NULL AS teacher,content,location,due_date,due_time,status FROM events ORDER BY id");
        return list;
    }

    public Optional<StoredItem> getById(String type, long id) throws SQLException {
        String sql = switch (type) {
            case "assignment" -> "SELECT id,'assignment' AS type,title,course,teacher,content,NULL AS location,due_date,due_time,status FROM assignments WHERE id=?";
            case "exam" -> "SELECT id,'exam' AS type,title,course,teacher,content,location,due_date,due_time,status FROM exams WHERE id=?";
            case "todo" -> "SELECT id,'todo' AS type,title,NULL AS course,NULL AS teacher,content,NULL AS location,due_date,due_time,status FROM todos WHERE id=?";
            case "course" -> "SELECT id,'course' AS type,title,NULL AS course,NULL AS teacher,NULL AS content,location,NULL AS due_date,NULL AS due_time,'active' AS status FROM courses WHERE id=?";
            case "event" -> "SELECT id,'event' AS type,title,NULL AS course,NULL AS teacher,content,location,due_date,due_time,status FROM events WHERE id=?";
            default -> throw new IllegalArgumentException("未知类型: " + type);
        };
        try (PreparedStatement ps = db.conn().prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        }
        return Optional.empty();
    }

    public long insertMessage(String role, String content) throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement(
                "INSERT INTO messages(role, content) VALUES(?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, role);
            ps.setString(2, content);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public long insertRaw(String content, String reason) throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement(
                "INSERT INTO raw_inbox(content, reason) VALUES(?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, content);
            ps.setString(2, reason);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private void queryInto(List<StoredItem> list, String type, String sql) throws SQLException {
        try (Statement st = db.conn().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
    }

    private StoredItem map(ResultSet rs) throws SQLException {
        return new StoredItem(
                rs.getLong("id"), rs.getString("type"), rs.getString("title"),
                rs.getString("course"), rs.getString("teacher"), rs.getString("content"),
                rs.getString("location"), rs.getString("due_date"), rs.getString("due_time"),
                rs.getString("status"));
    }

    private void setParams(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            if (params[i] == null) {
                ps.setObject(i + 1, null);
            } else if (params[i] instanceof Long l) {
                ps.setLong(i + 1, l);
            } else {
                ps.setString(i + 1, params[i].toString());
            }
        }
    }
}
```

- [ ] **Step 5: 运行确认通过**

Run: `mvn -q -Dtest=ItemRepositoryTest test`
Expected: `Tests run: 4, Failures: 0, Errors: 0` 且 BUILD SUCCESS。

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/campus/agent/store/StoredItem.java src/main/java/com/campus/agent/store/ItemRepository.java src/test/java/com/campus/agent/store/ItemRepositoryTest.java
git commit -m "feat: ItemRepository jdbc crud for all five item types"
```

---

## Task 6: LlmClient 接口与 DeepSeekClient（TDD）

**Files:**
- Create: `src/main/java/com/campus/agent/llm/LlmClient.java`
- Create: `src/main/java/com/campus/agent/llm/LlmException.java`
- Create: `src/main/java/com/campus/agent/llm/DeepSeekClient.java`
- Create: `src/test/java/com/campus/agent/llm/DeepSeekClientTest.java`
- Create: `src/test/java/com/campus/agent/testing/FakeLlm.java`（测试工具，供后续任务用）

- [ ] **Step 1: 写失败测试**

`src/test/java/com/campus/agent/llm/DeepSeekClientTest.java`：

```java
package com.campus.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeepSeekClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void buildBodyContainsExpectedFields() throws Exception {
        String body = DeepSeekClient.buildBody("deepseek-chat", "你是助手", "你好", "json_object");
        JsonNode n = MAPPER.readTree(body);
        assertEquals("deepseek-chat", n.path("model").asText());
        assertEquals("json_object", n.path("response_format").path("type").asText());
        assertEquals(2, n.path("messages").size());
        assertEquals("你是助手", n.path("messages").path(0).path("content").asText());
    }

    @Test
    void buildBodyOmitsResponseFormatWhenNull() throws Exception {
        JsonNode n = MAPPER.readTree(DeepSeekClient.buildBody("deepseek-chat", "s", "u", null));
        assertTrue(n.path("response_format").isMissingNode());
    }

    @Test
    void parseContentExtractsText() {
        String body = "{\"choices\":[{\"message\":{\"content\":\"你好，同学\"}}]}";
        assertEquals("你好，同学", DeepSeekClient.parseContent(body));
    }

    @Test
    void parseContentThrowsOnMissingContent() {
        assertThrows(LlmException.class, () -> DeepSeekClient.parseContent("{}"));
        assertThrows(LlmException.class, () -> DeepSeekClient.parseContent("不是JSON"));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q -Dtest=DeepSeekClientTest test`
Expected: FAIL（编译错误）。

- [ ] **Step 3: 写三个 LLM 文件**

`src/main/java/com/campus/agent/llm/LlmException.java`：

```java
package com.campus.agent.llm;

public class LlmException extends RuntimeException {
    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

`src/main/java/com/campus/agent/llm/LlmClient.java`：

```java
package com.campus.agent.llm;

/** LLM 客户端抽象。生产用 DeepSeekClient，测试用 FakeLlm。 */
public interface LlmClient {

    /** 普通对话，返回文本。 */
    String chat(String systemPrompt, String userPrompt);

    /** 要求模型严格输出 JSON（DeepSeek json_object 模式），返回 JSON 字符串。 */
    String chatJson(String systemPrompt, String userPrompt);
}
```

`src/main/java/com/campus/agent/llm/DeepSeekClient.java`：

```java
package com.campus.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 通过 DeepSeek chat/completions API 调用模型。 */
public class DeepSeekClient implements LlmClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public DeepSeekClient(String apiKey, String model, String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        return request(systemPrompt, userPrompt, null);
    }

    @Override
    public String chatJson(String systemPrompt, String userPrompt) {
        return request(systemPrompt, userPrompt, "json_object");
    }

    private String request(String systemPrompt, String userPrompt, String responseFormat) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            buildBody(model, systemPrompt, userPrompt, responseFormat),
                            StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                String snippet = resp.body();
                if (snippet.length() > 200) snippet = snippet.substring(0, 200);
                throw new LlmException("API 返回 " + resp.statusCode() + ": " + snippet);
            }
            return parseContent(resp.body());
        } catch (IOException | InterruptedException e) {
            throw new LlmException("网络错误: " + e.getMessage(), e);
        }
    }

    /** 组装请求体（包级可见，便于测试）。 */
    static String buildBody(String model, String systemPrompt, String userPrompt, String responseFormat)
            throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0.2);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));
        if (responseFormat != null) {
            body.put("response_format", Map.of("type", responseFormat));
        }
        return MAPPER.writeValueAsString(body);
    }

    /** 从响应体取出 choices[0].message.content。 */
    static String parseContent(String responseBody) {
        final JsonNode root;
        try {
            root = MAPPER.readTree(responseBody);
        } catch (IOException e) {
            throw new LlmException("响应不是合法 JSON: " + e.getMessage());
        }
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.isNull()) {
            throw new LlmException("响应缺少 choices[0].message.content");
        }
        return content.asText();
    }
}
```

- [ ] **Step 4: 写测试工具 FakeLlm**

`src/test/java/com/campus/agent/testing/FakeLlm.java`：

```java
package com.campus.agent.testing;

import com.campus.agent.llm.LlmClient;

import java.util.ArrayDeque;
import java.util.Deque;

/** 按入队顺序吐出预设响应的假 LLM。json() 进 chatJson 队列，chat() 进 chat 队列。 */
public class FakeLlm implements LlmClient {

    private final Deque<String> jsonResponses = new ArrayDeque<>();
    private final Deque<String> chatResponses = new ArrayDeque<>();

    public FakeLlm json(String response) {
        jsonResponses.add(response);
        return this;
    }

    public FakeLlm chat(String response) {
        chatResponses.add(response);
        return this;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        return chatResponses.isEmpty() ? "" : chatResponses.poll();
    }

    @Override
    public String chatJson(String systemPrompt, String userPrompt) {
        return jsonResponses.isEmpty() ? "{}" : jsonResponses.poll();
    }
}
```

- [ ] **Step 5: 运行确认通过**

Run: `mvn -q -Dtest=DeepSeekClientTest test`
Expected: `Tests run: 4, Failures: 0, Errors: 0` 且 BUILD SUCCESS。

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/campus/agent/llm/ src/test/java/com/campus/agent/llm/DeepSeekClientTest.java src/test/java/com/campus/agent/testing/FakeLlm.java
git commit -m "feat: DeepSeekClient with buildBody/parseContent plus FakeLlm test double"
```

---

## Task 7: Prompts + AssistantService（record 流）（TDD）

**Files:**
- Create: `src/main/java/com/campus/agent/Prompts.java`
- Create: `src/main/java/com/campus/agent/service/AssistantService.java`
- Create: `src/test/java/com/campus/agent/PromptsTest.java`
- Create: `src/test/java/com/campus/agent/service/AssistantServiceTest.java`

- [ ] **Step 1: 写失败测试**

`src/test/java/com/campus/agent/PromptsTest.java`：

```java
package com.campus.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromptsTest {

    @Test
    void extractPromptInjectsToday() {
        String prompt = Prompts.extract();
        assertTrue(prompt.contains("yyyy-MM-dd"), "抽取提示词应说明日期格式");
        assertTrue(prompt.matches("(?s).*\\d{4}-\\d{2}-\\d{2}.*"), "应注入今天日期: " + prompt);
    }
}
```

`src/test/java/com/campus/agent/service/AssistantServiceTest.java`：

```java
package com.campus.agent.service;

import com.campus.agent.model.ExtractedItem;
import com.campus.agent.store.Database;
import com.campus.agent.store.ItemRepository;
import com.campus.agent.store.StoredItem;
import com.campus.agent.testing.FakeLlm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AssistantServiceTest {

    private static final String VALID_ASSIGNMENT = """
            {"type":"assignment","title":"习题5.2","course":"高数","teacher":"王老师",
             "content":"完成5.2全部习题","location":"","dueDate":"2026-11-20","dueTime":"23:59"}
            """;

    private AssistantService newService(FakeLlm llm, Path tmp) throws Exception {
        Database db = new Database(tmp.resolve("s.db"));
        return new AssistantService(llm, new ItemRepository(db), db);
    }

    @Test
    void recordFlowStoresAndAcks(@TempDir Path tmp) throws Exception {
        FakeLlm llm = new FakeLlm()
                .json("{\"intent\":\"record\"}")
                .json(VALID_ASSIGNMENT);
        AssistantService service = newService(llm, tmp);
        String reply = service.handle("高数作业习题5.2，11月20日前交");
        assertTrue(reply.startsWith("✅"), "回执应以 ✅ 开头，实际: " + reply);
        assertTrue(reply.contains("习题5.2"));
        assertTrue(reply.contains("2026-11-20"));

        List<StoredItem> all = service.repository().allItems();
        assertEquals(1, all.size());
        assertEquals("assignment", all.get(0).type());
    }

    @Test
    void failedExtractionGoesToRawInbox(@TempDir Path tmp) throws Exception {
        FakeLlm llm = new FakeLlm()
                .json("{\"intent\":\"record\"}")
                .json("不是JSON")
                .json("{\"type\":\"nonsense\",\"title\":\"\"}");
        AssistantService service = newService(llm, tmp);
        String reply = service.handle("某条奇怪的消息");
        assertTrue(reply.contains("没能解析"), "应提示解析失败，实际: " + reply);
        assertTrue(service.repository().allItems().isEmpty(), "不应产生业务记录");
    }

    @Test
    void questionFlowReturnsLlmAnswer(@TempDir Path tmp) throws Exception {
        FakeLlm llm = new FakeLlm()
                .json("{\"intent\":\"question\"}")
                .chat("根据数据库，今天没有课。");
        AssistantService service = newService(llm, tmp);
        assertEquals("根据数据库，今天没有课。", service.handle("今天上什么课？"));
    }

    @Test
    void correctionFlowUpdatesLastRecord(@TempDir Path tmp) throws Exception {
        FakeLlm llm = new FakeLlm()
                .json("{\"intent\":\"record\"}")
                .json(VALID_ASSIGNMENT)
                .json("{\"intent\":\"correction\"}")
                .json("""
                        {"type":"assignment","title":"","course":"","teacher":"",
                         "content":"","location":"","dueDate":"2026-11-21","dueTime":""}
                        """);
        AssistantService service = newService(llm, tmp);
        service.handle("高数作业习题5.2，11月20日前交");
        long id = service.repository().allItems().get(0).id();

        String reply = service.handle("不对，截止是11月21日");
        assertTrue(reply.startsWith("✅ 已更新"), "应为更新回执，实际: " + reply);

        ExtractedItem unused = null; // 仅为可读性占位
        StoredItem updated = service.repository().getById("assignment", id).orElseThrow();
        assertEquals("2026-11-21", updated.dueDate());
        assertEquals("习题5.2", updated.title(), "未纠正的字段应保留原值");
    }

    @Test
    void unknownIntentFallsBackToRecord(@TempDir Path tmp) throws Exception {
        FakeLlm llm = new FakeLlm()
                .json("{\"intent\":\"something-else\"}")
                .json(VALID_ASSIGNMENT);
        AssistantService service = newService(llm, tmp);
        assertTrue(service.handle("随便说点什么").startsWith("✅"));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q -Dtest=PromptsTest,AssistantServiceTest test`
Expected: FAIL（编译错误）。

- [ ] **Step 3: 写 Prompts**

`src/main/java/com/campus/agent/Prompts.java`：

```java
package com.campus.agent;

import java.time.LocalDate;

/** 所有提示词模板。 */
public final class Prompts {

    private Prompts() {
    }

    /** 意图分类：record / question / correction。 */
    public static final String CLASSIFY = """
            你是校园助手的意图分类器。判断用户消息属于哪一类，只输出 JSON，格式：{"intent":"..."}。
            取值规则：
            - record：提供或陈述新信息（课程、作业、考试、待办、日程、老师通知等）
            - question：提问、查询（如“今天有什么课”“还有哪些作业没写”）
            - correction：纠正刚记录的信息（如“不对，是11-21”“改一下，地点是A301”）
            只输出 JSON，不要输出其他任何文字。
            """;

    private static final String EXTRACT_TEMPLATE = """
            你是校园助手的信息抽取器。从用户消息中抽取结构化信息，只输出 JSON，不要输出其他内容。
            JSON 字段：
            - type: 必填，取值 assignment(作业) / exam(考试) / todo(待办) / course(课程信息) / event(日程活动)。
              不属于前四类的零散信息一律归为 todo。
            - title: 必填，一句话简短标题
            - course: 课程名（与某门课相关才填，没有留空）
            - teacher: 老师姓名（没有留空）
            - content: 详细内容/要求（没有留空）
            - location: 地点（没有留空）
            - dueDate: 截止/考试/活动日期，格式 yyyy-MM-dd（“今天/明天/下周X”要换算成具体日期；没有则留空）
            - dueTime: 时间，格式 HH:mm（没有留空）
            今天是 {today}。
            """;

    public static String extract() {
        return EXTRACT_TEMPLATE.replace("{today}", LocalDate.now().toString());
    }

    /** 纠正：只输出被纠正的字段新值，未提及的字段输出空字符串。 */
    public static final String CORRECT = """
            你是校园助手的纠错抽取器。用户正在纠正刚记录的一条信息。
            请输出纠正后的完整记录 JSON（字段与信息抽取一致：type/title/course/teacher/content/location/dueDate/dueTime）。
            规则：
            - type 保持原记录的类型不变
            - 用户明确纠正的字段输出新值
            - 用户没提到的字段输出空字符串（系统会保留原值）
            只输出 JSON，不要输出其他任何文字。
            """;

    /** 问答：只依据提供的数据库内容回答。 */
    public static final String ANSWER = """
            你是用户的校园助手。用户消息里会附带数据库中的结构化数据（JSON 数组）。
            请只依据这些数据回答用户问题：
            - 有相关数据就清楚地回答（课表、截止日期等）
            - 没有相关数据就直说“我这儿没有相关记录”
            - 回答简洁，用中文
            """;
}
```

- [ ] **Step 4: 写 AssistantService**

`src/main/java/com/campus/agent/service/AssistantService.java`：

```java
package com.campus.agent.service;

import com.campus.agent.Prompts;
import com.campus.agent.llm.LlmClient;
import com.campus.agent.model.ExtractedItem;
import com.campus.agent.store.Database;
import com.campus.agent.store.ItemRepository;
import com.campus.agent.store.StoredItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 核心编排：意图分类 → 记录/提问/纠正 三条分支。 */
public class AssistantService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LlmClient llm;
    private final ItemRepository repo;
    private final Database db;
    private long lastInsertedId = -1;
    private String lastInsertedType = null;

    public AssistantService(LlmClient llm, ItemRepository repo, Database db) {
        this.llm = llm;
        this.repo = repo;
        this.db = db;
    }

    /** 供测试与 Main 查询用。 */
    public ItemRepository repository() {
        return repo;
    }

    /** 处理一条用户消息，返回助手的回复文本。 */
    public String handle(String input) {
        long messageId;
        try {
            messageId = repo.insertMessage("user", input);
        } catch (SQLException e) {
            throw new RuntimeException("保存聊天记录失败: " + e.getMessage(), e);
        }
        String intent = classify(input);
        return switch (intent) {
            case "question" -> answer(input);
            case "correction" -> correct(input);
            default -> record(input, messageId);
        };
    }

    private String classify(String input) {
        try {
            JsonNode n = MAPPER.readTree(llm.chatJson(Prompts.CLASSIFY, input));
            return n.path("intent").asText("record");
        } catch (Exception e) {
            return "record"; // 分类失败按记录处理，抽取环节还有兜底
        }
    }

    private String record(String input, long messageId) {
        List<String> lastErrors = List.of();
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String json = llm.chatJson(Prompts.extract(), input);
                ExtractedItem item = ExtractedItem.fromJson(json);
                lastErrors = item.validate();
                if (lastErrors.isEmpty()) {
                    long id = repo.insert(item, messageId);
                    lastInsertedId = id;
                    lastInsertedType = item.type();
                    String reply = ack(item);
                    repo.insertMessage("assistant", reply);
                    return reply;
                }
            } catch (Exception e) {
                lastErrors = List.of(e.getMessage());
            }
        }
        String reply = "⚠️ 这条我没能解析成结构化记录（原因：" + String.join("；", lastErrors)
                + "），原文已存档。你可以换个说法再说一次。";
        try {
            repo.insertRaw(input, String.join("；", lastErrors));
            repo.insertMessage("assistant", reply);
        } catch (SQLException e) {
            throw new RuntimeException("写 raw_inbox 失败: " + e.getMessage(), e);
        }
        return reply;
    }

    private String answer(String input) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            for (StoredItem s : repo.allItems()) {
                rows.add(s.toMap());
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询失败: " + e.getMessage(), e);
        }
        String context;
        try {
            context = rows.isEmpty() ? "（数据库为空，没有任何记录）" : MAPPER.writeValueAsString(rows);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化失败: " + e.getMessage(), e);
        }
        String reply = llm.chat(Prompts.ANSWER, "【数据库内容】\n" + context + "\n\n【用户问题】\n" + input);
        try {
            repo.insertMessage("assistant", reply);
        } catch (SQLException e) {
            throw new RuntimeException("保存聊天记录失败: " + e.getMessage(), e);
        }
        return reply;
    }

    private String correct(String input) {
        if (lastInsertedId < 0 || lastInsertedType == null) {
            return "我还没有刚记录的信息可纠正，请先录入一条信息。";
        }
        try {
            String json = llm.chatJson(Prompts.CORRECT, input);
            ExtractedItem corr = ExtractedItem.fromJson(json);
            Optional<StoredItem> currentOpt = repo.getById(lastInsertedType, lastInsertedId);
            if (currentOpt.isEmpty()) {
                String reply = "没找到要纠正的原记录，请先录入一条信息。";
                repo.insertMessage("assistant", reply);
                return reply;
            }
            StoredItem cur = currentOpt.get();
            ExtractedItem merged = new ExtractedItem(
                    lastInsertedType,
                    pick(corr.title(), cur.title()),
                    pick(corr.course(), cur.course()),
                    pick(corr.teacher(), cur.teacher()),
                    pick(corr.content(), cur.content()),
                    pick(corr.location(), cur.location()),
                    pick(corr.dueDate(), cur.dueDate()),
                    pick(corr.dueTime(), cur.dueTime()));
            List<String> errors = merged.validate();
            if (!errors.isEmpty()) {
                String reply = "纠正失败：" + String.join("；", errors);
                repo.insertMessage("assistant", reply);
                return reply;
            }
            repo.updateById(merged, lastInsertedId);
            String reply = "✅ 已更新：" + summarize(merged);
            repo.insertMessage("assistant", reply);
            return reply;
        } catch (SQLException e) {
            throw new RuntimeException("纠正时数据库出错: " + e.getMessage(), e);
        }
    }

    /** 纠正值非空则用之，否则保留原值。 */
    private String pick(String newValue, String oldValue) {
        return (newValue != null && !newValue.isBlank()) ? newValue : oldValue;
    }

    private String ack(ExtractedItem item) {
        return "✅ 已记录：" + summarize(item);
    }

    private String summarize(ExtractedItem item) {
        StringBuilder sb = new StringBuilder(typeLabel(item.type()) + "《" + item.title() + "》");
        if (item.course() != null) sb.append("（课程：").append(item.course()).append("）");
        if (item.teacher() != null) sb.append("，老师：").append(item.teacher());
        if (item.location() != null) sb.append("，地点：").append(item.location());
        if (item.dueDate() != null) {
            sb.append("，日期：").append(item.dueDate());
            if (item.dueTime() != null) sb.append(" ").append(item.dueTime());
        }
        if (item.content() != null) sb.append("，详情：").append(item.content());
        return sb.toString();
    }

    private String typeLabel(String type) {
        return switch (type) {
            case "assignment" -> "作业";
            case "exam" -> "考试";
            case "todo" -> "待办";
            case "course" -> "课程";
            case "event" -> "日程";
            default -> "记录";
        };
    }
}
```

注意：`AssistantService` 构造器第三个参数 `Database db` 在本阶段暂未使用（预留给阶段 2 的事务控制），保留是为了避免阶段 2 改签名。

- [ ] **Step 5: 运行确认通过**

Run: `mvn -q -Dtest=PromptsTest,AssistantServiceTest test`
Expected: `Tests run: 6, Failures: 0, Errors: 0` 且 BUILD SUCCESS。

（若出现 `unused variable` 之类的告警不影响通过；`ExtractedItem unused = null;` 这行如果让你不舒服，删掉即可——它在测试里只是占位。）

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/campus/agent/Prompts.java src/main/java/com/campus/agent/service/AssistantService.java src/test/java/com/campus/agent/PromptsTest.java src/test/java/com/campus/agent/service/AssistantServiceTest.java
git commit -m "feat: AssistantService intent routing with record/question/correction flows"
```

---

## Task 8: AppConfig + Main（REPL 与冒烟模式）

**Files:**
- Create: `src/main/java/com/campus/agent/AppConfig.java`
- Create: `src/main/java/com/campus/agent/Main.java`

- [ ] **Step 1: 写 AppConfig**

`src/main/java/com/campus/agent/AppConfig.java`：

```java
package com.campus.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** 配置加载：环境变量 DEEPSEEK_API_KEY 优先，其次 config.properties。 */
public record AppConfig(String apiKey, String model, String baseUrl, String dbPath) {

    public static AppConfig load() {
        Properties p = new Properties();
        Path f = Path.of("config.properties");
        if (Files.exists(f)) {
            try (var reader = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                p.load(reader);
            } catch (IOException e) {
                throw new IllegalStateException("读取 config.properties 失败: " + e.getMessage(), e);
            }
        }
        String key = System.getenv("DEEPSEEK_API_KEY");
        if (key == null || key.isBlank()) {
            key = p.getProperty("api.key", "");
        }
        if (key.isBlank()) {
            throw new IllegalStateException(
                    "缺少 DeepSeek API Key：请设置环境变量 DEEPSEEK_API_KEY，"
                            + "或复制 config.properties.example 为 config.properties 并填写 api.key");
        }
        return new AppConfig(
                key.trim(),
                p.getProperty("model", "deepseek-chat"),
                p.getProperty("base.url", "https://api.deepseek.com"),
                p.getProperty("db.path", "data/agent.db"));
    }
}
```

- [ ] **Step 2: 写 Main**

`src/main/java/com/campus/agent/Main.java`：

```java
package com.campus.agent;

import com.campus.agent.llm.DeepSeekClient;
import com.campus.agent.llm.LlmClient;
import com.campus.agent.service.AssistantService;
import com.campus.agent.store.Database;
import com.campus.agent.store.ItemRepository;
import com.campus.agent.store.StoredItem;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Scanner;

/** 命令行入口。带参数运行 = 冒烟模式（每个参数当作一条消息处理完退出）；不带参数 = 交互 REPL。 */
public class Main {

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.load();
        try (Database db = new Database(Path.of(config.dbPath()))) {
            ItemRepository repo = new ItemRepository(db);
            LlmClient llm = new DeepSeekClient(config.apiKey(), config.model(), config.baseUrl());
            AssistantService service = new AssistantService(llm, repo, db);

            if (args.length > 0) {
                for (String a : args) {
                    System.out.println("你 > " + a);
                    System.out.println("助手 > " + service.handle(a));
                }
                return;
            }

            Scanner in = new Scanner(System.in, StandardCharsets.UTF_8);
            System.out.println("校园助手已启动。直接输入消息；/list 查看记录；/help 帮助；/quit 退出。");
            while (true) {
                System.out.print("你 > ");
                if (!in.hasNextLine()) {
                    break;
                }
                String line = in.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }
                switch (line) {
                    case "/quit", "/exit" -> {
                        System.out.println("再见！");
                        return;
                    }
                    case "/help" -> printHelp();
                    case "/list" -> listAll(repo);
                    default -> System.out.println("助手 > " + service.handle(line));
                }
            }
        }
    }

    private static void printHelp() {
        System.out.println("""
                用法：
                  直接输入消息 → 自动识别是记录还是提问
                  /list  列出数据库所有记录
                  /quit  退出
                示例：
                  高数作业习题5.2，11月20日前交
                  今天有什么课？
                  不对，截止是11月21日
                """);
    }

    private static void listAll(ItemRepository repo) throws Exception {
        var items = repo.allItems();
        if (items.isEmpty()) {
            System.out.println("（数据库还没有记录）");
            return;
        }
        for (StoredItem s : items) {
            String when = s.dueDate() != null
                    ? s.dueDate() + (s.dueTime() != null ? " " + s.dueTime() : "")
                    : "-";
            System.out.printf("[%s] #%d 《%s》 时间:%s 状态:%s%n",
                    typeLabel(s.type()), s.id(), s.title(), when, s.status());
        }
    }

    private static String typeLabel(String type) {
        return switch (type) {
            case "assignment" -> "作业";
            case "exam" -> "考试";
            case "todo" -> "待办";
            case "course" -> "课程";
            case "event" -> "日程";
            default -> "记录";
        };
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn -q compile`
Expected: BUILD SUCCESS。

- [ ] **Step 4: 全量测试**

Run: `mvn -q test`
Expected: `Tests run: 16, Failures: 0, Errors: 0`（6+2+4+4 = 16）且 BUILD SUCCESS。

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/campus/agent/AppConfig.java src/main/java/com/campus/agent/Main.java
git commit -m "feat: Main repl with smoke mode plus AppConfig loader"
```

---

## Task 9: 真实 API 冒烟（人工验收）

**Files:** 无新代码。需要先有 config.properties。

- [ ] **Step 1: 创建 config.properties**

在 PowerShell（先 `chcp 65001`）：
```powershell
Copy-Item config.properties.example config.properties
```
然后用记事本打开 `config.properties`，把 `api.key=` 后面替换成你的真实 key，保存。

- [ ] **Step 2: 冒烟 1——录入一条作业**

Run: `mvn -q exec:java "-Dexec.args=高数作业习题5.2，11月20日前交"`
Expected: 输出类似：
```
你 > 高数作业习题5.2，11月20日前交
助手 > ✅ 已记录：作业《习题5.2》（课程：高数），日期：2026-11-20 ...
```

- [ ] **Step 3: 冒烟 2——提问**

Run: `mvn -q exec:java "-Dexec.args=还有哪些作业没写？"`
Expected: 助手回答中提及刚才那条作业（证明数据真的进了库、又被检索到）。

- [ ] **Step 4: 冒烟 3——纠正**

Run: `mvn -q exec:java "-Dexec.args=不对，截止是11月21日"`
Expected: `助手 > ✅ 已更新：…日期：2026-11-21…`

- [ ] **Step 5: 交互模式体验**

Run: `mvn -q exec:java`
Expected: 出现 `你 >` 提示符。依次输入 `/list`（应显示 1 条记录）、`/quit` 退出。

- [ ] **Step 6: 用 DB Browser 看数据**

用 DB Browser for SQLite 打开 `data/agent.db`，确认 assignments、messages、raw_inbox 表里都有数据。

- [ ] **Step 7: Commit（如有未提交变更）**

```powershell
git status
git add -A
git commit -m "chore: phase 1 smoke verification done"
```
（config.properties 已被 .gitignore 忽略，不会进仓库，你的 key 是安全的。）

---

## Task 10: 10 条典型消息回归测试

**Files:**
- Create: `src/test/java/com/campus/agent/SampleMessagesTest.java`

- [ ] **Step 1: 写回归测试**

`src/test/java/com/campus/agent/SampleMessagesTest.java`：

```java
package com.campus.agent;

import com.campus.agent.service.AssistantService;
import com.campus.agent.store.Database;
import com.campus.agent.store.ItemRepository;
import com.campus.agent.testing.FakeLlm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 10 条典型老师消息走完整流水线（用假 LLM 驱动），保证改代码后管道不退化。 */
class SampleMessagesTest {

    // 每条 = {用户消息, 抽取结果JSON}
    private static final String[][] SAMPLES = {
            {"老师通知：下周一（11月24日）下午两点在A201开会",
                    "{\"type\":\"event\",\"title\":\"老师会议\",\"course\":\"\",\"teacher\":\"\",\"content\":\"老师通知开会\",\"location\":\"A201\",\"dueDate\":\"2026-11-24\",\"dueTime\":\"14:00\"}"},
            {"高数作业：习题5.2，11月20日前交",
                    "{\"type\":\"assignment\",\"title\":\"习题5.2\",\"course\":\"高数\",\"teacher\":\"\",\"content\":\"\",\"location\":\"\",\"dueDate\":\"2026-11-20\",\"dueTime\":\"\"}"},
            {"下周二英语课考试，考1-5单元",
                    "{\"type\":\"exam\",\"title\":\"英语考试\",\"course\":\"英语\",\"teacher\":\"\",\"content\":\"1-5单元\",\"location\":\"\",\"dueDate\":\"2026-11-25\",\"dueTime\":\"\"}"},
            {"记住明天上午10点去辅导员办公室领材料",
                    "{\"type\":\"todo\",\"title\":\"领材料\",\"course\":\"\",\"teacher\":\"\",\"content\":\"辅导员办公室\",\"location\":\"\",\"dueDate\":\"2026-08-16\",\"dueTime\":\"10:00\"}"},
            {"数据库课在周三3-4节，B302，老师是张老师",
                    "{\"type\":\"course\",\"title\":\"数据库\",\"course\":\"\",\"teacher\":\"张老师\",\"content\":\"\",\"location\":\"B302\",\"dueDate\":\"\",\"dueTime\":\"\"}"},
            {"实验报告周五前交给学委",
                    "{\"type\":\"assignment\",\"title\":\"实验报告\",\"course\":\"\",\"teacher\":\"\",\"content\":\"交给学委\",\"location\":\"\",\"dueDate\":\"2026-08-21\",\"dueTime\":\"\"}"},
            {"12月1日晚上7点教学楼D105 英语四级模拟考",
                    "{\"type\":\"exam\",\"title\":\"英语四级模拟考\",\"course\":\"英语\",\"teacher\":\"\",\"content\":\"\",\"location\":\"D105\",\"dueDate\":\"2026-12-01\",\"dueTime\":\"19:00\"}"},
            {"记得给社团发活动总结",
                    "{\"type\":\"todo\",\"title\":\"发活动总结\",\"course\":\"\",\"teacher\":\"\",\"content\":\"给社团\",\"location\":\"\",\"dueDate\":\"\",\"dueTime\":\"\"}"},
            {"周五下午4点实验室组会",
                    "{\"type\":\"event\",\"title\":\"组会\",\"course\":\"\",\"teacher\":\"\",\"content\":\"实验室组会\",\"location\":\"实验室\",\"dueDate\":\"2026-08-21\",\"dueTime\":\"16:00\"}"},
            {"数据结构期末大作业12月10日截止，要求实现一个学生管理系统",
                    "{\"type\":\"assignment\",\"title\":\"学生管理系统大作业\",\"course\":\"数据结构\",\"teacher\":\"\",\"content\":\"实现一个学生管理系统\",\"location\":\"\",\"dueDate\":\"2026-12-10\",\"dueTime\":\"\"}"},
    };

    @Test
    void allTenSamplesFlowThroughPipeline(@TempDir Path tmp) throws Exception {
        FakeLlm llm = new FakeLlm();
        for (String[] sample : SAMPLES) {
            llm.json("{\"intent\":\"record\"}").json(sample[1]);
        }
        try (Database db = new Database(tmp.resolve("samples.db"))) {
            ItemRepository repo = new ItemRepository(db);
            AssistantService service = new AssistantService(llm, repo, db);
            for (String[] sample : SAMPLES) {
                String reply = service.handle(sample[0]);
                assertTrue(reply.startsWith("✅"), "样例应成功入库，实际回复: " + reply);
            }
            List<?> all = repo.allItems();
            assertEquals(10, all.size(), "10 条样例都应入库");
        }
    }
}
```

- [ ] **Step 2: 运行确认通过**

Run: `mvn -q -Dtest=SampleMessagesTest test`
Expected: `Tests run: 1, Failures: 0, Errors: 0`。

- [ ] **Step 3: 全量回归 + Commit**

Run: `mvn -q test`
Expected: `Tests run: 17, Failures: 0, Errors: 0` 且 BUILD SUCCESS。

```powershell
git add src/test/java/com/campus/agent/SampleMessagesTest.java
git commit -m "test: regression suite for 10 typical teacher messages"
```

---

## 阶段 1 完成验收清单

- [ ] `mvn -q test` 全绿（17 个测试）
- [ ] 真实 API 冒烟：录入 → 提问 → 纠正 三连通过
- [ ] `/list` 能看到记录；DB Browser 能看到数据
- [ ] git 提交历史完整（约 9 个 commit）
- [ ] 你能用自己的话向别人解释：消息进了程序之后，经过了哪些步骤变成数据库里的一行

完成上述清单后，通知我进入**阶段 2（Spring Boot 网页版）**的设计与计划。
