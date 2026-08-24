# Task 6 总结：SSE 流式回复（2026-08-22）

> 今日状态：Task 6 完成，全量 25 个测试绿，已提交

## 今日范围

- `llm/LlmClient.java`：增加 `chatStream`
- `llm/DeepSeekClient.java`：实现流式请求
- `testing/FakeLlm.java`：增加流式队列
- `service/AssistantService.java`：增加 `handleStreaming`
- `web/ChatController.java`：增加 `/api/chat/stream`
- `static/index.html`：前端读流式响应
- `service/AssistantServiceTest.java`：增加流式测试

## 今日知识点

### 1. DeepSeek 流式返回格式

- 普通请求：一次性返回完整结果。
- 流式请求：一行一行返回增量。
- 每行格式：
  ```text
  data: {"choices":[{"delta":{"content":"今"}}]}
  ```
- 增量在 `choices[0].delta.content`
- 结束标记：`data: [DONE]`

### 2. SseEmitter

- Spring 的 SSE 出口，向浏览器持续推送数据。
- `emitter.send(...)`：推送一块数据。
- `emitter.complete()`：正常结束。
- `emitter.completeWithError(e)`：出错结束。

### 3. 异步

- Controller 必须先立刻返回 `SseEmitter`。
- 真正的流式处理放到线程池执行。
- 避免请求线程被占死。

### 4. 为什么不用 EventSource

- EventSource 只支持 GET。
- 我们的接口是 POST，所以用 `fetch` 读 `ReadableStream`。

### 5. DeepSeekClient.chatStream

- 构造请求体时多一个 `stream: true`。
- 用 `HttpResponse.BodyHandlers.ofLines()` 按行读取。
- 每行以 `data: ` 开头才处理。
- 用 `parseStreamDelta` 取 `choices[0].delta.content`。

### 6. AssistantService.handleStreaming

- 先保存用户消息、分类意图。
- 非 question：走原来的 record / correct。
- question：查数据库 → 构造上下文 → `llm.chatStream` 逐块回调 → 累积完整回复 → 保存 assistant 消息。

### 7. 前端流式渲染

- `fetch("/api/chat/stream")` 发起 POST。
- `resp.body.getReader()` 读取流。
- 按行解析 `data:` 内容。
- `event:done` 后的 `data:` 应覆盖而不是追加，避免重复。

## 文件与方法链路

```text
浏览器
   ↓ POST /api/chat/stream
ChatController.chatStream
   ↓ 立即返回 SseEmitter
线程池
   ↓ service.handleStreaming
AssistantService
   ↓ llm.chatStream
DeepSeekClient 流式响应
   ↓ onDelta
emitter.send(data)
   ↓
浏览器逐块显示
```

## 今天答错的点 / 踩坑记录

1. **流式返回是“一段一段返回”？** 不准确。普通请求才是一次性返回，流式是一块一块返回。
2. **`HttpResponse.BodyHandlers.ofLines()` 带参？** 当前 Java 版本应无参。
3. **前端 `data: ` 带空格匹配问题**：Spring 发送的可能是 `data:` 不带空格，要兼容两种。
4. **回复重复两遍**：`event:done` 后的完整 `data:` 不应再追加，应该覆盖。
5. **“无回复”**：如果只跳过 done 后的 data，而流式增量没收到，就会空白；改为“done 后覆盖”可兜底。

## 下一步

- 补充任务：课程表增加起止时间（见下一份笔记）。
