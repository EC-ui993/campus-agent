# Task 4 总结：ChatController 非流式接口 + MockMvc（2026-08-22）

> 今日状态：Task 4 完成，ChatControllerTest 绿，全量 24 个测试绿，已提交

## 今日范围

- `src/main/java/com/campus/agent/web/ChatController.java`
- `src/test/java/com/campus/agent/web/ChatControllerTest.java`

## 今日知识点

### 1. `@RequestMapping("/api")` + `@PostMapping("/chat")`

- 类上 `@RequestMapping("/api")`：这个控制器的公共前缀。
- 方法上 `@PostMapping("/chat")`：处理 POST `/chat`。
- 完整路径：`/api/chat`。

### 2. `@RequestBody`

- 把请求体里的 JSON 自动反序列化成 Java 对象。
- 例：
  ```json
  {"message":"高数作业习题5.2"}
  ```
  变成：
  ```java
  new ChatRequest("高数作业习题5.2")
  ```

### 3. record 作为请求/响应对象

- `ChatRequest(String message)`：接收前端发来的 JSON。
- `ChatReply(String reply)`：返回给前端的 JSON 结构。

### 4. Controller 方法流程

```text
前端 POST /api/chat
   ↓ @RequestBody
ChatRequest
   ↓ service.handle(req.message())
String reply
   ↓ new ChatReply(reply)
ChatReply
   ↓ Spring 自动转 JSON
{"reply":"✅ 已记录：..."}
```

### 5. MockMvc

- 不启动真实 Tomcat，模拟 HTTP 请求测试 Controller。
- `@SpringBootTest`：启动完整 Spring Boot 应用上下文。
- `@AutoConfigureMockMvc`：自动配置 MockMvc。

### 6. `@TestConfiguration` + `@Primary`

- `@TestConfiguration`：只在测试环境生效的配置类。
- `@Primary`：有多个同类型 Bean 时，优先用这个。
- 作用：测试里用 `FakeLlm` 替换真实 `DeepSeekClient`。

### 7. `@DynamicPropertySource`

- 在 Spring 容器启动前动态覆盖配置属性。
- 这里把 `app.db-path` 指向系统临时目录的临时数据库，避免污染真实数据。

### 8. Windows 临时文件清理问题

- 原计划里的 `@AfterAll` 删除临时数据库会报“文件被占用”。
- 原因是 Spring 容器里的数据库连接还没关闭。
- 修法：去掉 `@AfterAll` 清理，让系统临时目录自己处理。

## 文件与方法链路

```text
浏览器 / 测试请求
   ↓ POST /api/chat
ChatController.chat(ChatRequest)
   ↓ service.handle(message)
AssistantService
   ↓
ChatReply
   ↓ Spring 转 JSON
浏览器 / 测试断言
```

## 今天答错的点（复习重点）

1. **`@SpringBootTest` 是“只在测试环境有效”？** 不准确。  
   它是启动完整 Spring Boot 上下文来测试。
2. **`@DynamicPropertySource` 指向 `/agent/web/test/db`？** 错。  
   它指向系统临时目录里的临时文件。
3. **`chat` 方法“把反序列化后的 Java 对象返回”？** 不准确。  
   它是处理用户消息，把回复包成 `ChatReply`，由 Spring 转 JSON 返回。

## 下一步

- Task 5：前端聊天页 v1（非流式）。
