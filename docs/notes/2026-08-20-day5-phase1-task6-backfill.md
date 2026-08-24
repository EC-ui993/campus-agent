# 第 5 天总结：理解回填 · Task 6 LlmClient + DeepSeekClient（2026-08-20）

> 今日状态：Task 6 完成并提交，全量测试 16/16 绿；已补做逐行理解回填

## 今日范围

- `src/main/java/com/campus/agent/llm/LlmClient.java`
- `src/main/java/com/campus/agent/llm/LlmException.java`
- `src/main/java/com/campus/agent/llm/DeepSeekClient.java`
- `src/test/java/com/campus/agent/llm/DeepSeekClientTest.java`
- `src/test/java/com/campus/agent/testing/FakeLlm.java`

## 今日知识点

### 1. 接口 LlmClient

- `interface` 不能 `new`，只定义方法签名。
- 方法：
  - `chat(systemPrompt, userPrompt)`：普通对话，返回文本。
  - `chatJson(systemPrompt, userPrompt)`：要求模型输出 JSON，返回 JSON 字符串。
- 作用：生产用 `DeepSeekClient`，测试用 `FakeLlm`，两边都实现同一个接口，上层代码不关心具体实现。

### 2. LlmException

- 继承 `RuntimeException`，属于**非受检异常**。
- 两个构造方法：
  - `LlmException(String message)`：只有错误信息。
  - `LlmException(String message, Throwable cause)`：错误信息 + 原始原因。

### 3. DeepSeekClient 基本结构

- 实现 `LlmClient`。
- 成员：
  - `MAPPER`：Jackson 解析器，静态共享。
  - `HttpClient http`：发 HTTP 请求的客户端。
  - `apiKey` / `model` / `baseUrl`：构造时传入。
- 构造方法接收：API 密钥、模型名、API 地址。

### 4. chat / chatJson 与 request

- `chat` 调用 `request(..., null)`。
- `chatJson` 调用 `request(..., "json_object")`。
- 第三个参数对应 `response_format`：非 null 才写入请求 JSON。

### 5. request 前半段：构造 HttpRequest

- URL：`baseUrl + "/chat/completions"`
- `Content-Type: application/json`：告诉服务器发的是 JSON。
- `Authorization: Bearer <apiKey>`：身份认证。
- 请求体：`buildBody(...)` 生成 JSON 字符串，再用 `HttpRequest.BodyPublishers.ofString(...)` 包装。

### 6. request 后半段：发送并处理响应

- `http.send(...)` 发送整个 HttpRequest，等待响应。
- `HttpResponse<String> resp` 是响应对象。
- `resp.body()` 返回服务器返回的完整响应体字符串（通常是一段 JSON）。
- 状态码不是 200：截取响应体前 200 字符，抛 `LlmException`。
- 状态码是 200：`parseContent(resp.body())` 取出 content 文本。

### 7. buildBody：Java 对象 → JSON 字符串

- 用 `LinkedHashMap` 保证字段顺序。
- 放入 `model`、`temperature`、`messages`（system + user 两条）。
- 如果 `responseFormat != null`，再放入 `response_format`。
- `MAPPER.writeValueAsString(body)` 把 Map 转成 JSON 字符串。

### 8. parseContent：从响应 JSON 取 content

- `MAPPER.readTree(responseBody)` 把响应体解析成 JSON 树。
- `path("choices").path(0).path("message").path("content")` 一路取到 content 节点。
- `isMissingNode()` / `isNull()`：取不到就抛 `LlmException`。
- 取到就 `content.asText()` 返回字符串。

### 9. FakeLlm

- 实现 `LlmClient`，测试时假装成 LLM。
- 两个 `Deque<String>`（`ArrayDeque` 实现）：
  - `jsonResponses`：存 `chatJson` 要返回的 JSON。
  - `chatResponses`：存 `chat` 要返回的普通文本。
- `json(...)` / `chat(...)`：把预设内容加入队列，`return this` 支持链式调用。
- `poll()`：取出并**移除**队首元素，所以每次调用会按顺序拿到下一条。
- 队列为空时：
  - `chat` 返回 `""`
  - `chatJson` 返回 `"{}"`

### 10. DeepSeekClientTest 的 4 个测试

1. `buildBodyContainsExpectedFields`：验证生成的 JSON 包含 model、response_format、messages 两条。
2. `buildBodyOmitsResponseFormatWhenNull`：第四个参数为 null 时，JSON 不包含 response_format。
3. `parseContentExtractsText`：能从响应 JSON 中取出 content 文本。
4. `parseContentThrowsOnMissingContent`：空 JSON / 非法 JSON 都会抛 `LlmException`。

## 文件与方法链路

### 文件职责

- `LlmClient`：定义 LLM 调用的抽象接口。
- `LlmException`：LLM 相关异常。
- `DeepSeekClient`：真实调用 DeepSeek API。
- `DeepSeekClientTest`：测试 buildBody / parseContent。
- `FakeLlm`：测试用的假 LLM，按预设队列返回。

### 依赖关系

```text
DeepSeekClient / FakeLlm
   ↓ 实现
LlmClient
   ↓ 被上层使用
AssistantService（Task 7 才会用到）
```

```text
DeepSeekClient.request
   ↓ 构造
HttpRequest
   ↓ 发送
DeepSeek API
   ↓ 响应
HttpResponse
   ↓ parseContent
content 文本
```

### 主要方法链路

- `chat` / `chatJson` → `request(systemPrompt, userPrompt, responseFormat)`
- `request` → `buildBody(...)` → `http.send(...)` → 判断状态码 → `parseContent(...)`
- `buildBody` → `Map`/`List` 组装 → `MAPPER.writeValueAsString` → JSON 字符串
- `parseContent` → `readTree` → `path` 链 → `asText()`
- `FakeLlm.json` / `chat` → `Deque.add` → 接口方法调用时 `Deque.poll`

## 今天答错的点（复习重点）

1. **`return this` 是为了更新队列？** 错。是为了支持链式调用，队列在 `add` 时已经更新。
2. **`poll()` 取出后元素还在队列里？** 错。`poll()` 取出并移除队首元素。
3. **`resp.body()` 返回 content 文本？** 错。它返回完整响应体 JSON 字符串，取 content 是 `parseContent` 的事。
4. **请求体是 `newBuilder` 创建出来的？** 不准确。请求体来自 `buildBody(...)` 生成的 JSON，再用 `BodyPublishers.ofString` 包装。
5. **`isMissingNode()` 是判断第四个参数是否为 null？** 不准确。它判断解析后的 JSON 里有没有 `response_format` 字段。
6. **`http.send` 只发送请求体？** 错。它发送整个 HttpRequest。

## 明日接续点

- Task 6 理解回填完成，已提交。
- 下一步是 Task 7：Prompts + AssistantService（TDD）。
- Task 7 会引入意图分类、record / question / correction 三条分支，是阶段 1 的核心编排。
- 建议先读 `PromptsTest` 和 `AssistantServiceTest`，再写实现。
