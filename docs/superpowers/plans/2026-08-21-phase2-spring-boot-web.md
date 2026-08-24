# 阶段 2：Spring Boot 网页版 · 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把阶段 1 的命令行助手搬进 Spring Boot，变成浏览器（含手机同 WiFi）可访问的网页聊天助手，并完成持久层 MyBatis-Plus 化与按记录纠正。

**Architecture:** 单模块 Spring Boot 3 应用：阶段 1 的 `AssistantService`/`ItemRepository`/`DeepSeekClient` 通过 `@Configuration` 装配为 Bean，`ChatController` 提供 REST/SSE 接口，`static/index.html` 单页前端通过 fetch 调用；阶段末尾用 MyBatis-Plus + HikariCP 替换裸 JDBC，并把纠正改为按记录 id 的无状态设计。

**Tech Stack:** Java 17（编译目标）/ JDK 21 运行、Spring Boot 3.5.x、spring-boot-starter-web、MyBatis-Plus（mybatis-plus-spring-boot3-starter）、SQLite、Jackson、JUnit 5、原生 HTML/JS 前端。

## 执行纪律（先懂再写，继承阶段 1 教训）

- 每个任务先过「概念清单」：导师逐行讲解，**学员用自己的话复述过关后才动笔**。
- 下方代码是**导师的参考实现**，不得整段甩给学员照抄；学员在引导下自己敲。
- 每个任务结束：测试绿 + 提交 + 把本任务概念记入 `docs/notes/`。
- 学员 Java SE 刚学到反射：Spring 的注解、IoC、AOP 等概念需从"它替你干了什么"的角度讲。

## 文件结构总览（本计划新增/修改）

```
myAgent/
├─ pom.xml                                   改：Boot 依赖、MyBatis-Plus、spring-boot-maven-plugin
├─ src/main/resources/
│  ├─ application.yml                        新：端口/配置/口令
│  ├─ schema.sql                             新：建表 DDL（从 Database.SCHEMA 抽出，单一事实来源）
│  └─ static/index.html                      新：单页聊天前端（内联 CSS/JS，Task 5 起逐步加）
├─ src/main/java/com/campus/agent/
│  ├─ CampusAgentApplication.java            新：Boot 入口
│  ├─ web/
│  │  ├─ ChatController.java                 新：POST /api/chat、POST /api/chat/stream、GET /api/items
│  │  └─ AccessTokenFilter.java              新：简单口令校验
│  ├─ config/
│  │  ├─ AppProperties.java                  新：@ConfigurationProperties 配置绑定
│  │  └─ AppBeans.java                       新：@Bean 装配（Database/Repo/LlmClient/Service/Executor）
│  ├─ llm/LlmClient.java                     改：增加 chatStream 方法
│  ├─ llm/DeepSeekClient.java                改：实现 chatStream（DeepSeek stream 模式）
│  ├─ service/AssistantService.java          改：handleStreaming；Task 9 改无状态纠正
│  └─ store/
│     ├─ Database.java                       改：Task 8 改从 classpath schema.sql 读 DDL
│     ├─ ItemRepository.java                 改：Task 8 换 MyBatis-Plus 实现
│     ├─ entity/                             新：5 个实体（Assignment/Exam/Todo/Course/Event）
│     └─ mapper/                             新：5 个 BaseMapper 接口
└─ src/test/java/com/campus/agent/
   ├─ web/ChatControllerTest.java            新：MockMvc 集成测试
   ├─ service/AssistantServiceTest.java      改：Task 9 补充按 id 纠正测试
   └─ store/ItemRepositoryTest.java          改：Task 8 改为 MyBatis-Plus 环境
```

---

## Task 0: Spring Boot 概念启蒙（无代码，纯理解）

**Files:** 无。产出：`docs/notes/2026-08-xx-task0-spring-concepts.md` 笔记。

**概念清单（每项都要学员能复述）：**
- **框架 vs 类库**：类库是你调它（如 Jackson）；框架是它调你（如 Spring Boot 启动时自动调用你的代码）——"控制反转"
- **IoC 容器 / Bean**：对象不再由你 `new`，交给 Spring 容器创建、装配、管理生命周期（类比：把"自己造零件"变成"填写采购清单"）
- **依赖注入（DI）**：构造器参数由容器自动传入（你已在 `AssistantService(LlmClient, ItemRepository, Database)` 里写过雏形，只不过当时是 Main 手动 new）
- **注解的本质**：类/方法上的标记（如 `@RestController`），框架启动时**用反射扫描**这些标记来工作——正好用上你学过的反射
- **Spring MVC 请求流程**：浏览器请求 → DispatcherServlet → Controller 方法 → 返回值转 JSON → 浏览器
- **SSE 与普通响应的区别**：普通 HTTP 是"一问一答、一次给完"；SSE 是"一问、持续回答"（流式输出打字机效果）

**验收：** 学员能用自己的话回答"Spring Boot 启动后，一个 HTTP 请求是怎么到达我们的代码并被处理的？"

---

## Task 1: 项目改造成 Spring Boot 工程

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/campus/agent/CampusAgentApplication.java`
- Create: `src/main/java/com/campus/agent/web/HelloController.java`（临时冒烟接口）

- [ ] **Step 1: 讲概念**：pom 的 `<parent>` 是什么（继承一份配置：版本号统一管理）；starter 是什么（"依赖聚合包"，spring-boot-starter-web 一次带来 MVC+Tomcat+Jackson）

- [ ] **Step 2: 改 pom.xml**

```xml
<project ...>
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.6</version>
    <relativePath/>
  </parent>
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
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.xerial</groupId>
      <artifactId>sqlite-jdbc</artifactId>
      <version>3.46.1.3</version>
    </dependency>
    <dependency>
      <groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

（注：加了 parent 后 JUnit 版本由 Boot 管理，原来的 junit-jupiter/surefire 显式版本删掉；`mvn test` 照常可用。）

- [ ] **Step 3: 写入口类**

```java
package com.campus.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CampusAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(CampusAgentApplication.class, args);
    }
}
```

- [ ] **Step 4: 写冒烟接口**

```java
package com.campus.agent.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HelloController {

    @GetMapping("/api/hello")
    public Map<String, String> hello() {
        return Map.of("status", "ok");
    }
}
```

- [ ] **Step 5: 验证**

Run: `mvn spring-boot:run`
Expected: 控制台出现 `Tomcat started on port 8080`。浏览器打开 `http://localhost:8080/api/hello`，看到 `{"status":"ok"}`。Ctrl+C 停掉。

- [ ] **Step 6: Commit**

```powershell
git add -A
git commit -m "build: migrate project to spring boot with hello endpoint"
```

---

## Task 2: 配置化（application.yml + AppProperties）

**Files:**
- Create: `src/main/resources/application.yml`
- Create: `src/main/java/com/campus/agent/config/AppProperties.java`
- Modify: `CampusAgentApplication.java`（加 `@ConfigurationPropertiesScan`）

- [ ] **Step 1: 讲概念**：约定优于配置（Boot 读 `application.yml` 是约定）；`@Value` vs `@ConfigurationProperties`（前者单值、后者把 `app.*` 前缀整块绑定成对象）；环境变量覆盖（`${DEEPSEEK_API_KEY:默认值}` 语法）。

- [ ] **Step 2: 写 application.yml**

```yaml
server:
  port: 8080
  address: 0.0.0.0        # 允许局域网访问（Task 7 之前先注释掉这行，只开 localhost）

app:
  api-key: ${DEEPSEEK_API_KEY:在这里填入你的key}
  model: deepseek-chat
  base-url: https://api.deepseek.com
  db-path: data/agent.db
  access-token: change-me-please
```

- [ ] **Step 3: 写 AppProperties**

```java
package com.campus.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String apiKey, String model, String baseUrl, String dbPath, String accessToken) {
}
```

- [ ] **Step 4: 入口加扫描**

```java
@SpringBootApplication
@ConfigurationPropertiesScan   // ← 新增这一行
public class CampusAgentApplication { ... }
```

- [ ] **Step 5: 验证**：`mvn spring-boot:run` 启动不报错（AppProperties 被创建）；Ctrl+C 停。Commit：`chore: add application.yml and AppProperties binding`

---

## Task 3: Bean 装配（把阶段 1 的类交给 Spring）

**Files:**
- Create: `src/main/java/com/campus/agent/config/AppBeans.java`

- [ ] **Step 1: 讲概念**：`@Configuration` 类里的 `@Bean` 方法 = "告诉容器这个对象我来生产"；方法参数 = 依赖注入（容器自动传）；`Database` 实现了 `AutoCloseable` → Spring 关闭时会自动调 `close()`；同一个类型必须只有一个 Bean（否则歧义）。

- [ ] **Step 2: 写 AppBeans**

```java
package com.campus.agent.config;

import com.campus.agent.llm.DeepSeekClient;
import com.campus.agent.llm.LlmClient;
import com.campus.agent.service.AssistantService;
import com.campus.agent.store.Database;
import com.campus.agent.store.ItemRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.sql.SQLException;

@Configuration
public class AppBeans {

    @Bean
    public Database database(AppProperties props) throws SQLException {
        return new Database(Path.of(props.dbPath()));
    }

    @Bean
    public ItemRepository itemRepository(Database db) {
        return new ItemRepository(db);
    }

    @Bean
    public LlmClient llmClient(AppProperties props) {
        return new DeepSeekClient(props.apiKey(), props.model(), props.baseUrl());
    }

    @Bean
    public AssistantService assistantService(LlmClient llm, ItemRepository repo, Database db) {
        return new AssistantService(llm, repo, db);
    }
}
```

- [ ] **Step 3: 验证**：`mvn spring-boot:run` 启动成功即证明装配无误（启动时会真实创建 Database——注意 `data/agent.db` 会被建出来，正常）。停掉后 Commit：`feat: wire phase 1 components as spring beans`

---

## Task 4: ChatController 非流式接口 + 集成测试

**Files:**
- Create: `src/main/java/com/campus/agent/web/ChatController.java`
- Create: `src/test/java/com/campus/agent/web/ChatControllerTest.java`

- [ ] **Step 1: 讲概念**：`@RestController`（= @Controller + 自动 JSON 序列化）；`@PostMapping`/`@RequestBody`（请求体反序列化成 record）；MockMvc（不启真服务器，模拟 HTTP 请求测 Controller）；`@TestConfiguration` + `@Primary`（测试里换掉真 LLM）；`@DynamicPropertySource`（测试改配置指向临时数据库）。

- [ ] **Step 2: 写 ChatController**

```java
package com.campus.agent.web;

import com.campus.agent.service.AssistantService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final AssistantService service;

    public ChatController(AssistantService service) {
        this.service = service;
    }

    public record ChatRequest(String message) {
    }

    public record ChatReply(String reply) {
    }

    @PostMapping("/chat")
    public ChatReply chat(@RequestBody ChatRequest req) {
        return new ChatReply(service.handle(req.message()));
    }
}
```

- [ ] **Step 3: 写集成测试（先红后绿）**

```java
package com.campus.agent.web;

import com.campus.agent.llm.LlmClient;
import com.campus.agent.testing.FakeLlm;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerTest {

    private static Path dbPath;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws Exception {
        dbPath = Files.createTempFile("agent-web-test", ".db");
        Files.deleteIfExists(dbPath); // SQLite 会重新创建
        registry.add("app.db-path", () -> dbPath.toString());
    }

    @AfterAll
    static void cleanup() throws Exception {
        Files.deleteIfExists(dbPath);
        Files.deleteIfExists(Path.of(dbPath + "-wal"));
        Files.deleteIfExists(Path.of(dbPath + "-shm"));
    }

    @TestConfiguration
    static class FakeLlmConfig {
        @Bean
        @Primary
        LlmClient fakeLlm() {
            return new FakeLlm()
                    .json("{\"intent\":\"record\"}")
                    .json("""
                            {"type":"assignment","title":"习题5.2","course":"高数","teacher":"",
                             "content":"","location":"","dueDate":"2026-11-20","dueTime":"23:59"}
                            """);
        }
    }

    @Autowired
    MockMvc mvc;

    @Test
    void recordMessageReturnsAck() throws Exception {
        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"高数作业习题5.2，11月20日前交\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.startsWith("✅")));
    }
}
```

- [ ] **Step 4: 验证**：`mvn -q -Dtest=ChatControllerTest test` 绿；再跑 `mvn -q test` 全量绿（阶段 1 测试不受影响）。Commit：`feat: chat controller with mockmvc integration test`

---

## Task 5: 前端聊天页 v1（非流式）

**Files:**
- Create: `src/main/resources/static/index.html`（内联 CSS + JS）

- [ ] **Step 1: 讲概念**：静态资源约定（`src/main/resources/static/` 下的文件自动对外服务，根路径 `/index.html`）；fetch API（浏览器发 HTTP 请求，替代表单提交）；JSON.parse/stringify；DOM 操作（createElement/appendChild）。

- [ ] **Step 2: 写 index.html（参考实现）**

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>校园助手</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body { font-family: system-ui, sans-serif; background: #f5f6f8; height: 100vh; display: flex; flex-direction: column; }
  header { background: #2c3e50; color: #fff; padding: 12px 16px; font-weight: 600; }
  #messages { flex: 1; overflow-y: auto; padding: 16px; display: flex; flex-direction: column; gap: 10px; }
  .bubble { max-width: 78%; padding: 10px 14px; border-radius: 12px; line-height: 1.6; white-space: pre-wrap; word-break: break-word; }
  .user { align-self: flex-end; background: #409eff; color: #fff; border-bottom-right-radius: 4px; }
  .assistant { align-self: flex-start; background: #fff; color: #333; border-bottom-left-radius: 4px; box-shadow: 0 1px 2px rgba(0,0,0,.08); }
  footer { display: flex; gap: 8px; padding: 10px; background: #fff; border-top: 1px solid #e5e6eb; }
  #input { flex: 1; border: 1px solid #d0d3d9; border-radius: 8px; padding: 10px 12px; font-size: 15px; }
  #send { border: none; background: #409eff; color: #fff; border-radius: 8px; padding: 0 20px; font-size: 15px; cursor: pointer; }
</style>
</head>
<body>
<header>🏫 校园助手</header>
<div id="messages"></div>
<footer>
  <input id="input" placeholder="粘贴老师通知，或问我问题…" autocomplete="off">
  <button id="send">发送</button>
</footer>
<script>
  const messages = document.getElementById("messages");
  const input = document.getElementById("input");
  const sendBtn = document.getElementById("send");

  function addBubble(text, who) {
    const div = document.createElement("div");
    div.className = "bubble " + who;
    div.textContent = text;
    messages.appendChild(div);
    messages.scrollTop = messages.scrollHeight;
    return div;
  }

  async function send() {
    const text = input.value.trim();
    if (!text) return;
    input.value = "";
    addBubble(text, "user");
    sendBtn.disabled = true;
    try {
      const resp = await fetch("/api/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ message: text })
      });
      const data = await resp.json();
      addBubble(data.reply, "assistant");
    } catch (err) {
      addBubble("网络错误：" + err, "assistant");
    } finally {
      sendBtn.disabled = false;
    }
  }

  sendBtn.onclick = send;
  input.addEventListener("keydown", (e) => { if (e.key === "Enter") send(); });
</script>
</body>
</html>
```

- [ ] **Step 3: 验证**：`mvn spring-boot:run` → 浏览器开 `http://localhost:8080/` → 发一条"高数作业习题5.2，11月20日前交"，看到 ✅ 回执气泡；再问"有哪些作业"，得到回答。Commit：`feat: static chat page v1 with non-stream fetch`

---

## Task 6: SSE 流式回复

**Files:**
- Modify: `llm/LlmClient.java`（+`chatStream`）
- Modify: `llm/DeepSeekClient.java`（实现 stream 模式）
- Modify: `testing/FakeLlm.java`（+流式队列）
- Modify: `service/AssistantService.java`（+`handleStreaming`）
- Modify: `web/ChatController.java`（+`POST /api/chat/stream`）
- Modify: `static/index.html`（改流式渲染）
- Test: `service/AssistantServiceTest.java`（+流式测试）

- [ ] **Step 1: 讲概念**：DeepSeek 的 `"stream": true` 返回格式（`data: {...}` 行 + 结尾 `data: [DONE]`，`choices[0].delta.content` 是增量）；`SseEmitter`（Spring 的 SSE 出口，`event().data()` 发一块）；**异步**（Controller 方法必须立即返回 emitter，真正的活放线程池跑——否则请求线程被占死）；`@RequestBody` 配合流式响应的局限（前端用 fetch 读 ReadableStream 而不是 EventSource，因为 EventSource 只支持 GET）。

- [ ] **Step 2: LlmClient 增加流式方法**

```java
public interface LlmClient {
    String chat(String systemPrompt, String userPrompt);
    String chatJson(String systemPrompt, String userPrompt);

    /** 流式对话：把回答增量一块块交给 onDelta。 */
    void chatStream(String systemPrompt, String userPrompt, java.util.function.Consumer<String> onDelta);
}
```

- [ ] **Step 3: DeepSeekClient 实现**

```java
@Override
public void chatStream(String systemPrompt, String userPrompt, java.util.function.Consumer<String> onDelta) {
    try {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(
                        buildStreamBody(model, systemPrompt, userPrompt), StandardCharsets.UTF_8))
                .build();
        HttpResponse<Stream<String>> resp =
                http.send(req, HttpResponse.BodyHandlers.ofLines(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw new LlmException("API 返回 " + resp.statusCode());
        }
        resp.body().forEach(line -> {
            if (!line.startsWith("data: ")) return;
            String payload = line.substring(6).trim();
            if ("[DONE]".equals(payload)) return;
            String delta = parseStreamDelta(payload);
            if (delta != null && !delta.isEmpty()) onDelta.accept(delta);
        });
    } catch (IOException | InterruptedException e) {
        throw new LlmException("流式请求失败: " + e.getMessage(), e);
    }
}

static String buildStreamBody(String model, String systemPrompt, String userPrompt) throws IOException {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", model);
    body.put("temperature", 0.2);
    body.put("stream", true);
    body.put("messages", List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", userPrompt)));
    return MAPPER.writeValueAsString(body);
}

static String parseStreamDelta(String payload) {
    try {
        JsonNode n = MAPPER.readTree(payload);
        JsonNode delta = n.path("choices").path(0).path("delta").path("content");
        return delta.isMissingNode() || delta.isNull() ? null : delta.asText();
    } catch (IOException e) {
        return null;
    }
}
```

- [ ] **Step 4: FakeLlm 增加流式支持**

```java
private final Deque<String> streamChunks = new ArrayDeque<>();

public FakeLlm streamChunks(String... chunks) {
    for (String c : chunks) streamChunks.add(c);
    return this;
}

@Override
public void chatStream(String systemPrompt, String userPrompt, java.util.function.Consumer<String> onDelta) {
    while (!streamChunks.isEmpty()) onDelta.accept(streamChunks.poll());
}
```

- [ ] **Step 5: AssistantService 增加 handleStreaming**

```java
/** 处理消息：回答类走流式回调（onDelta 逐块收到增量），返回完整回复；记录/纠正类无增量，直接返回回执。 */
public String handleStreaming(String input, java.util.function.Consumer<String> onDelta) {
    long messageId;
    try {
        messageId = repo.insertMessage("user", input);
    } catch (SQLException e) {
        throw new RuntimeException("保存聊天记录失败: " + e.getMessage(), e);
    }
    String intent = classify(input);
    if (!"question".equals(intent)) {
        return switch (intent) {
            case "correction" -> correct(input);
            default -> record(input, messageId);
        };
    }
    List<Map<String, Object>> rows = new ArrayList<>();
    try {
        for (StoredItem s : repo.allItems()) rows.add(s.toMap());
    } catch (SQLException e) {
        throw new RuntimeException("查询失败: " + e.getMessage(), e);
    }
    String context;
    try {
        context = rows.isEmpty() ? "（数据库为空，没有任何记录）" : MAPPER.writeValueAsString(rows);
    } catch (JsonProcessingException e) {
        throw new RuntimeException("序列化失败: " + e.getMessage(), e);
    }
    StringBuilder acc = new StringBuilder();
    llm.chatStream(Prompts.ANSWER, "【数据库内容】\n" + context + "\n\n【用户问题】\n" + input, delta -> {
        acc.append(delta);
        onDelta.accept(delta);
    });
    String reply = acc.toString();
    try {
        repo.insertMessage("assistant", reply);
    } catch (SQLException e) {
        throw new RuntimeException("保存聊天记录失败: " + e.getMessage(), e);
    }
    return reply;
}
```

- [ ] **Step 6: ChatController 增加流式端点**

```java
private final java.util.concurrent.ExecutorService chatExecutor =
        java.util.concurrent.Executors.newFixedThreadPool(4);

@PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public org.springframework.web.servlet.mvc.method.annotation.SseEmitter chatStream(@RequestBody ChatRequest req) {
    SseEmitter emitter = new SseEmitter(120_000L);
    chatExecutor.submit(() -> {
        try {
            String full = service.handleStreaming(req.message(), delta -> {
                try {
                    emitter.send(SseEmitter.event().data(delta));
                } catch (Exception e) {
                    throw new RuntimeException("SSE 发送失败", e);
                }
            });
            emitter.send(SseEmitter.event().name("done").data(full));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    });
    return emitter;
}
```

- [ ] **Step 7: 前端改流式渲染**（`index.html` 的 send() 替换为读流实现）

```javascript
async function send() {
  const text = input.value.trim();
  if (!text) return;
  input.value = "";
  addBubble(text, "user");
  sendBtn.disabled = true;
  const bubble = addBubble("", "assistant");
  try {
    const resp = await fetch("/api/chat/stream", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ message: text })
    });
    const reader = resp.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const parts = buffer.split("\n");
      buffer = parts.pop();
      for (const part of parts) {
        const line = part.trim();
        if (line.startsWith("data:")) {
          bubble.textContent += line.substring(5).trim();
          messages.scrollTop = messages.scrollHeight;
        } else if (line.startsWith("event:done")) {
          // 完整回复在紧随的 data 行，无需特殊处理
        }
      }
    }
    if (!bubble.textContent) addBubble("（无回复）", "assistant");
  } catch (err) {
    bubble.textContent = "网络错误：" + err;
  } finally {
    sendBtn.disabled = false;
  }
}
```

- [ ] **Step 8: 测试 + 验证**

`AssistantServiceTest` 增加：

```java
@Test
void questionStreamEmitsDeltas(@TempDir Path tmp) throws Exception {
    FakeLlm llm = new FakeLlm()
            .json("{\"intent\":\"question\"}")
            .streamChunks("今天", "没", "有课。");
    try (Database db = new Database(tmp.resolve("stream.db"))) {
        AssistantService service = new AssistantService(llm, new ItemRepository(db), db);
        StringBuilder received = new StringBuilder();
        String full = service.handleStreaming("今天有什么课？", received::append);
        assertEquals("今天没有课。", full);
        assertEquals("今天没有课。", received.toString());
    }
}
```

Run: `mvn -q -Dtest=AssistantServiceTest test` 绿；`mvn -q test` 全量绿。
手工验证：`mvn spring-boot:run` → 浏览器提问，回答逐字打出（打字机效果）。Commit：`feat: sse streaming chat endpoint and frontend`

---

## 补充任务（Task 6 之后）：课程表增加起止时间

**Files:**
- Modify: `Database.java`（courses 表增加 `start_time`、`end_time`）
- Modify: `ExtractedItem.java`（增加 `startTime`、`endTime`）
- Modify: `StoredItem.java`（增加 `startTime`、`endTime`）
- Modify: `ItemRepository.java`（course 插入/更新/查询支持起止时间）
- Modify: `Prompts.java`（抽取提示词增加 `startTime`、`endTime`）
- Modify: `AssistantService.java`（纠正合并时保留起止时间）
- Modify: `ItemRepositoryTest.java`（构造参数更新）

**验证：** `mvn -q test` 全绿。

**说明：** 解决课程只有名称/地点、没有具体上课时间的问题；`courses` 表现在能存 `start_time` 和 `end_time`，问答时能把这些时间带给 LLM。

---


## Task 7: 局域网访问 + 简单口令

**Files:**
- Modify: `application.yml`（打开 0.0.0.0）
- Create: `web/AccessTokenFilter.java`
- Modify: `static/index.html`（口令弹窗 + 请求头）

- [ ] **Step 1: 讲概念**：`0.0.0.0` 绑定 = 监听所有网卡（局域网内他人可访问）；Filter 与 Interceptor 的区别（Filter 是 Servlet 层、最早拦；这里够用）；HTTP 头传递凭据；localStorage（浏览器本地存储）。

- [ ] **Step 2: application.yml 打开绑定**（去掉 `address` 行的注释）

- [ ] **Step 3: 写 AccessTokenFilter**

```java
package com.campus.agent.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(1)
public class AccessTokenFilter extends OncePerRequestFilter {

    private final String token;

    public AccessTokenFilter(@Value("${app.access-token}") String token) {
        this.token = token;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean isApi = path.startsWith("/api/");
        if (!isApi) {           // 静态页面直接放行，口令在页面里校验
            chain.doFilter(request, response);
            return;
        }
        String given = request.getHeader("X-Access-Token");
        if (token.equals(given)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"口令不正确\"}");
        }
    }
}
```

- [ ] **Step 4: 前端加口令**（index.html 脚本开头）

```javascript
const TOKEN_KEY = "campus-agent-token";
let token = localStorage.getItem(TOKEN_KEY);
if (!token) {
  token = prompt("请输入访问口令：");
  if (token) localStorage.setItem(TOKEN_KEY, token);
}
// 所有 fetch 的 headers 里加：
//   "X-Access-Token": token
// 收到 401 时提示并清除本地存储：localStorage.removeItem(TOKEN_KEY)
```

- [ ] **Step 5: 验证**：`mvn spring-boot:run` → 电脑浏览器带口令访问正常；PowerShell 里 `ipconfig` 查局域网 IP（形如 192.168.x.x）→ **手机连同一 WiFi**，浏览器开 `http://192.168.x.x:8080` → 输口令 → 聊天。手机无响应时查 Windows 防火墙放行 8080。Commit：`feat: lan access with simple token filter`

---

## Task 8: MyBatis-Plus 替换持久层

**Files:**
- Modify: `pom.xml`（+spring-boot-starter-jdbc、mybatis-plus-spring-boot3-starter）
- Create: `src/main/resources/schema.sql`
- Modify: `store/Database.java`（改从 classpath 读 schema.sql；CLI 仍用）
- Modify: `application.yml`（+datasource、spring.sql.init）
- Create: `store/entity/` 5 个实体；`store/mapper/` 5 个 Mapper
- Modify: `store/ItemRepository.java`（改 MP 实现）
- Modify: `store/ItemRepositoryTest.java`（改 MP 测试环境）

- [ ] **Step 1: 讲概念**：ORM（对象-关系映射：实体类 ↔ 表）；MyBatis-Plus 的 `BaseMapper<T>`（继承即得 CRUD，零 XML）；`@TableName`/`@TableId`（表和主键映射）；下划线转驼峰（`due_date` ↔ `dueDate` 自动对应）；**DataSource 连接池**（HikariCP 管理多连接，替代单 Connection——顺便解决多线程共用连接问题）；`spring.sql.init`（启动时自动执行 schema.sql）。

- [ ] **Step 2: pom 增加依赖**

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
<dependency>
  <groupId>com.baomidou</groupId>
  <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
  <version>3.5.12</version>
</dependency>
```

- [ ] **Step 3: 建 schema.sql**（把 `Database.SCHEMA` 文本块的 7 条建表语句原样搬进 `src/main/resources/schema.sql`，去掉三引号、保留分号）

- [ ] **Step 4: application.yml 增加**

```yaml
spring:
  datasource:
    url: jdbc:sqlite:data/agent.db
    driver-class-name: org.sqlite.JDBC
  sql:
    init:
      mode: always
      encoding: UTF-8
```

- [ ] **Step 5: Database 改从 classpath 读 DDL**（保持 CLI 可用且单一事实来源）

```java
try (Statement st = conn.createStatement()) {
    st.execute("PRAGMA journal_mode=WAL;");
    st.execute("PRAGMA busy_timeout=5000;");
    String schema;
    try (var in = Database.class.getResourceAsStream("/schema.sql")) {
        schema = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    for (String sql : schema.split(";")) {
        if (!sql.isBlank()) st.execute(sql);
    }
}
```
（删除类里原来的 SCHEMA 常量；import 增加 `java.nio.charset.StandardCharsets`。阶段 1 的 DatabaseTest 应保持全绿——验证 classpath 加载 OK。）

- [ ] **Step 6: 写 5 个实体（以 Assignment 为例，其余同理）**

```java
package com.campus.agent.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("assignments")
public class Assignment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String course;
    private String teacher;
    private String content;
    private String dueDate;
    private String dueTime;
    private String status;
    private Long sourceMessageId;
    // getter/setter 全部手写（理解原理；熟练后再换 Lombok）
}
```

其余 4 个实体是**学员练习**（导师核对，不代写）：`Exam`(@TableName("exams")，字段 title/course/teacher/content/location/dueDate/dueTime/status/sourceMessageId)、`Todo`("todos"，字段 title/content/dueDate/dueTime/status/sourceMessageId)、`Course`("courses"，字段 title/teacher/location/sourceMessageId)、`Event`("events"，字段 title/content/location/dueDate/dueTime/status/sourceMessageId)。created_at 列有数据库默认值，实体不需要字段。

- [ ] **Step 7: 写 5 个 Mapper**

```java
package com.campus.agent.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.agent.store.entity.Assignment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AssignmentMapper extends BaseMapper<Assignment> {
}
```
（Exam/Todo/Course/Event 同理。）

- [ ] **Step 8: ItemRepository 改 MP 实现**（方法签名不变，内部换实现）

```java
public class ItemRepository {
    private final AssignmentMapper assignmentMapper;
    private final ExamMapper examMapper;
    private final TodoMapper todoMapper;
    private final CourseMapper courseMapper;
    private final EventMapper eventMapper;
    // 构造器注入 5 个 Mapper

    public long insert(ExtractedItem item, long sourceMessageId) {
        switch (item.type()) {
            case "assignment" -> {
                Assignment e = new Assignment();
                e.setTitle(item.title()); e.setCourse(item.course()); e.setTeacher(item.teacher());
                e.setContent(item.content()); e.setDueDate(item.dueDate()); e.setDueTime(item.dueTime());
                e.setStatus("pending"); e.setSourceMessageId(sourceMessageId);
                assignmentMapper.insert(e);
                return e.getId();
            }
            // 学员练习：剩余 4 个类型仿照 assignment 分支自己写。
            // 注意差异：exam 多 location 字段；todo 无 course/teacher；course 无 due 字段；
            // event 有 location 与 due 字段。导师核对 setter 与列对应关系。
            default -> throw new IllegalArgumentException("未知类型: " + item.type());
        }
    }

    public List<StoredItem> allItems() { /* 分别 selectList(null)，转 StoredItem 后合并，保持原顺序 */ }

    public Optional<StoredItem> getById(String type, long id) { /* 按类型 selectById，转 StoredItem */ }

    public boolean updateById(ExtractedItem item, long id) { /* 构造带 id 的实体，updateById 返回 int==1 */ }

    // insertMessage / insertRaw：阶段 1 用 JDBC 即可——演示"MP 管业务表、JDBC 管杂表"的混合用法。
    // 注意：原来的 Database 字段没了，这两个方法改为从哪拿连接？
    // 方案：注入 DataSource，dataSource.getConnection() 用 try-with-resources。
}
```

（insertMessage/insertRaw 改用注入的 `javax.sql.DataSource`。）

- [ ] **Step 9: ItemRepositoryTest 改造**：改为 `@SpringBootTest` + `@DynamicPropertySource`（临时 db 文件 + `spring.datasource.url` 覆盖）+ 注入 ItemRepository；4 个测试场景（insertAndListRoundTrip / updateByIdMergesFields / insertMessageAndRawInbox / allItemsCoversEveryType）断言不变。`AssistantServiceTest` 保持原样（它用自己的 Database，不走 Spring）。AppBeans 里删除 `database()`/`itemRepository(Database)` 旧装配，改为注入 5 个 Mapper 装配 ItemRepository。

- [ ] **Step 10: 验证 + Commit**：`mvn -q test` 全量绿；`mvn spring-boot:run` 后浏览器聊天照常工作。Commit：`feat: replace jdbc repository with mybatis-plus mappers and hikari datasource`

---

## Task 9: 按记录 id 纠正（无状态化）

**Files:**
- Modify: `service/AssistantService.java`（去掉 lastInsertedId/lastInsertedType 字段，纠正改为 LLM 给出 type+id）
- Modify: `Prompts.java`（CORRECT 提示词增加 id/type 输出）
- Modify: `web/ChatController.java`（+`GET /api/items` 记录列表）
- Modify: `static/index.html`（+「查看记录」面板）
- Test: `AssistantServiceTest.java` 纠正测试改造

- [ ] **Step 1: 讲概念**：**共享可变状态 vs 无状态**（阶段 1 的 `lastInsertedId` 是"记住上一次"的状态，网页多线程并发时互相踩——这是把 CLI 搬上 Web 的经典坑）；无状态设计（纠正目标从消息内容里解析，不依赖记忆）；REST 的 GET 幂等查询。

- [ ] **Step 2: Prompts.CORRECT 改造**

```
你是校园助手的纠错抽取器。下面是数据库现有记录（JSON 数组）：
{records}
用户要纠正其中一条。请输出 JSON：
{"type":"该记录的类型(assignment/exam/todo/course/event)","id":记录的数字id,
 "title":"仅被纠正字段的新值，其余留空", ...其余字段同信息抽取...}
只输出 JSON。
```

（`{records}` 由服务用 `repo.allItems()` 序列化后替换。）

- [ ] **Step 3: AssistantService 改造**

```java
private String correct(String input) {
    try {
        String recordsJson = MAPPER.writeValueAsString(
                repo.allItems().stream().map(StoredItem::toMap).toList());
        String prompt = Prompts.CORRECT.replace("{records}", recordsJson);
        String json = llm.chatJson(prompt, input);
        JsonNode n = MAPPER.readTree(json);
        String type = n.path("type").asText("");
        long id = n.path("id").asLong(-1);
        if (id < 0 || !ExtractedItem.VALID_TYPES.contains(type)) {
            String reply = "没听懂要纠正哪条，请带上编号，如“把作业第3条的日期改成11月12日”。";
            repo.insertMessage("assistant", reply);
            return reply;
        }
        ExtractedItem corr = ExtractedItem.fromJson(json);
        Optional<StoredItem> curOpt = repo.getById(type, id);
        if (curOpt.isEmpty()) {
            String reply = "没找到编号为 " + id + " 的" + type + " 记录。";
            repo.insertMessage("assistant", reply);
            return reply;
        }
        StoredItem cur = curOpt.get();
        ExtractedItem merged = new ExtractedItem(type,
                pick(corr.title(), cur.title()), pick(corr.course(), cur.course()),
                pick(corr.teacher(), cur.teacher()), pick(corr.content(), cur.content()),
                pick(corr.location(), cur.location()), pick(corr.dueDate(), cur.dueDate()),
                pick(corr.dueTime(), cur.dueTime()));
        List<String> errors = merged.validate();
        if (!errors.isEmpty()) {
            String reply = "纠正失败：" + String.join("；", errors);
            repo.insertMessage("assistant", reply);
            return reply;
        }
        repo.updateById(merged, id);
        String reply = "✅ 已更新：" + summarize(merged);
        repo.insertMessage("assistant", reply);
        return reply;
    } catch (Exception e) {
        throw new RuntimeException("纠正出错: " + e.getMessage(), e);
    }
}
```
（删除 `lastInsertedId`、`lastInsertedType` 两个字段及 record() 里的赋值；classify 的 CORRECTION 意图判断不变。`pick`、`summarize` 保留。）

- [ ] **Step 4: GET /api/items**

```java
@GetMapping("/items")
public List<Map<String, Object>> items() {
    try {
        return service.repository().allItems().stream().map(StoredItem::toMap).toList();
    } catch (SQLException e) {
        throw new RuntimeException("查询失败", e);
    }
}
```

- [ ] **Step 5: 前端「记录」面板**：标题栏加按钮，点击 fetch `/api/items`（带 token 头）渲染编号列表（显示 类型#id + 标题 + 日期），用户可照着说"把作业第3条改成…"。

- [ ] **Step 6: 测试改造**：`AssistantServiceTest.correctionFlowUpdatesLastRecord` 改为：先录一条；纠正消息用 FakeLlm 返回 `{"type":"assignment","id":1,"title":"","dueDate":"2026-11-21",...}`；断言 getById("assignment",1) 的 dueDate 更新、title 保留。再补一个"id 不存在"分支测试。

- [ ] **Step 7: 验证 + Commit**：全量 `mvn -q test` 绿；浏览器里录两条 → 打开记录面板 → 纠正第二条成功。Commit：`feat: stateless correction by record id plus items endpoint`

---

## Task 10: 端到端验收 + 数据连续性 + 笔记归档

**Files:** 无新代码；更新 `docs/notes/` 与交接文档。

- [ ] **Step 1: 数据连续性检查**：Web 版 `spring.datasource.url` 与 `app.db-path` 都指向 `data/agent.db`——阶段 1 CLI 里录的真实数据应能在网页版 `/api/items` 和问答中看到（这正是"同一个库"的意义）。
- [ ] **Step 2: 电脑浏览器全流程**：录入 → 流式提问 → 纠正第 N 条 → 记录面板核对。
- [ ] **Step 3: 手机验收**：同 WiFi 手机浏览器访问 `http://<局域网IP>:8080`，口令进入，完整走一遍流程，检查窄屏显示。
- [ ] **Step 4: 全量回归**：`mvn test` 全绿（阶段 1 的 23 个 + 新增的 Controller/流式/纠正测试）。
- [ ] **Step 5: 笔记归档**：新增 `docs/notes/` 阶段 2 概念笔记（Spring 概念、SSE、MyBatis-Plus、无状态设计），提交：`docs: phase 2 concept notes and acceptance record`
- [ ] **Step 6: 回 v4-pro 规划会话**：汇报验收结果，讨论阶段 2.5（Excel 导入是否提前）与阶段 3 计划。

## 阶段 2 完成验收清单

- [ ] `mvn test` 全绿
- [ ] 电脑浏览器端到端：录入 → 提问（流式）→ 纠正指定记录
- [ ] 手机同 WiFi 可访问、口令生效、窄屏可用
- [ ] 阶段 1 的真实数据在网页版可见（同一个 agent.db）
- [ ] 学员能复述：请求怎么进 Controller；SSE 为什么能"打字机"；MyBatis-Plus 为什么不需要写 SQL；纠正为什么不能靠"记住上一条"
