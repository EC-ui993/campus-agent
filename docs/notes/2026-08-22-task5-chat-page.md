# Task 5 总结：前端聊天页 v1（非流式）（2026-08-22）

> 今日状态：Task 5 完成，浏览器页面可正常录入并显示回执，已提交

## 今日范围

- `src/main/resources/static/index.html`

## 今日知识点

### 1. 静态资源约定

- Spring Boot 默认把 `src/main/resources/static/` 下的文件对外服务。
- 浏览器访问 `/` 会显示 `index.html`。
- 不需要写 Controller 返回 HTML。

### 2. fetch API

- 浏览器发 HTTP 请求的方法，可替代表单提交。
- 例：
  ```js
  const resp = await fetch("/api/chat", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ message: text })
  });
  const data = await resp.json();
  ```
- `resp.json()` 解析的是**服务器返回的响应体 JSON**。

### 3. DOM 操作

- `document.createElement("div")`：创建新元素。
- `div.className = "bubble " + who`：设置样式类。
- `div.textContent = text`：设置文字内容。
- `messages.appendChild(div)`：把元素追加到容器。
- 作用：动态添加聊天气泡。

### 4. 前端页面流程

```text
用户在输入框打字
   ↓ 点击发送 / 回车
addBubble(text, "user") 显示用户消息
   ↓ fetch POST /api/chat
服务器处理
   ↓ 返回 JSON
resp.json() → data.reply
   ↓ addBubble(data.reply, "assistant")
显示助手回复
```

### 5. API Key 安全

- 如果没设置 `DEEPSEEK_API_KEY`，`application.yml` 会用默认占位符 `在这里填入你的key`，导致请求头非法。
- 不建议把真实 Key 写进 `application.yml`，因为该文件会被 git 跟踪。
- 更安全做法：用 `setx DEEPSEEK_API_KEY "你的key"` 永久设置用户环境变量，新终端自动生效。

## 文件与方法链路

### 文件职责

- `index.html`：单页聊天前端，内联 CSS + JS。
- 后端 `/api/chat`：处理消息并返回 JSON。

### 请求链路

```text
浏览器 index.html
   ↓ 用户输入
fetch POST /api/chat
   ↓
ChatController.chat()
   ↓
AssistantService.handle()
   ↓
ChatReply JSON
   ↓
浏览器渲染气泡
```

## 今天答错的点（复习重点）

1. **浏览器“访问 static 路径下的所有文件”？** 不准确。  
   浏览器通过 URL 访问，例如 `/` 对应 `index.html`。
2. **`resp.json()` 解析浏览器发送的请求体？** 错。  
   它解析的是服务器返回的响应体 JSON。

## 下一步

- Task 6：SSE 流式回复（LlmClient.chatStream、DeepSeekClient 流式、SseEmitter、打字机前端）。
