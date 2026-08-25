# Task 7 总结：局域网访问 + 简单口令（2026-08-22）

> 今日状态：Task 7 完成，全量测试绿，已提交；手机局域网访问验证通过

## 今日范围

- `application.yml`：打开 `address: 0.0.0.0`
- `web/AccessTokenFilter.java`：API 口令 Filter
- `static/index.html`：口令弹窗 + 请求头 + 401 处理
- `ChatControllerTest.java`：测试请求加上口令头

## 今日知识点

### 1. `0.0.0.0`

- 表示监听所有网卡。
- 默认只监听 localhost，只有本机能访问。
- 打开后，同一 WiFi 下的手机/其他设备可以通过局域网 IP 访问。

### 2. Filter vs Interceptor

- Filter 在 Servlet 层，最早拦截，进入 Spring MVC 之前。
- Interceptor 在 Spring MVC 层，更靠后。
- 本项目只需要口令校验，用 Filter 最简单。

### 3. HTTP 头传递凭据

- 前端把口令放在自定义请求头：
  ```text
  X-Access-Token: change-me-please
  ```
- 后端通过：
  ```java
  request.getHeader("X-Access-Token")
  ```
  获取。

### 4. localStorage

- 浏览器本地存储，关闭页面后还在。
- 这里用来记住用户输入过的口令，下次不用再输入。
- 相关方法：
  - `localStorage.setItem(key, value)`
  - `localStorage.getItem(key)`
  - `localStorage.removeItem(key)`

### 5. AccessTokenFilter

- `@Component`：注册成 Spring Bean。
- `@Order(1)`：多个 Filter 时先执行。
- `extends OncePerRequestFilter`：每个请求只经过一次。
- `@Value("${app.access-token}")`：从配置注入口令。
- 只拦截 `/api/` 开头的请求。
- 口令匹配放行，不匹配返回 401 JSON。

### 6. 前端口令流程

```text
打开页面
   ↓
localStorage 里有没有 token？
   ├─ 有：直接使用
   └─ 没有：prompt 让用户输入 → 存入 localStorage
   ↓
发送消息时 headers 加 X-Access-Token
   ↓
收到 401：清除 token，提示“口令错误，请重新输入”
```

### 7. 测试修复

- 加了 Filter 后，`ChatControllerTest` 请求 `/api/chat` 会返回 401。
- 修复：在 MockMvc 请求里加：
  ```java
  .header("X-Access-Token", "change-me-please")
  ```

## 文件与方法链路

```text
手机浏览器
   ↓ 访问 http://192.168.x.x:8080
静态页面放行（index.html）
   ↓ 用户输入口令
localStorage 保存 token
   ↓ 发送消息
fetch 带 X-Access-Token
   ↓
AccessTokenFilter
   ├─ 非 /api：放行
   ├─ /api + token 正确：放行
   └─ /api + token 错误：401
   ↓
ChatController → AssistantService
```

## 今天答错的点 / 注意

1. **`@Value("${app.accessToken}")` 找不到配置**。  
   YAML 里是 `access-token`，`@Value` 要写 `${app.access-token}`。
2. **错误口令也能打开页面**。  
   这是正常设计：只拦 `/api/`，不拦静态页面。
3. **手机打不开**：先确认 Spring Boot 在运行、同一 WiFi、IP 正确、防火墙放行 8080。

## 下一步

- Task 8：MyBatis-Plus + HikariCP 替换裸 JDBC。
