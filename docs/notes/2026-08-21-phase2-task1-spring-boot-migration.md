# Task 1 总结：项目改造成 Spring Boot 工程（2026-08-21）

> 今日状态：Task 1 完成，`mvn spring-boot:run` 启动成功，访问 `/api/hello` 返回 `{"status":"ok"}`，已提交

## 今日范围

- `pom.xml`：改为 Spring Boot 父工程 + starter-web
- `src/main/java/com/campus/agent/CampusAgentApplication.java`：Boot 入口
- `src/main/java/com/campus/agent/web/HelloController.java`：冒烟接口

## 今日知识点

### 1. pom.xml 的 `<parent>`

- 继承 Spring Boot 提供的父 POM。
- 好处：大量依赖版本号由 Spring Boot 统一管理，不用自己写版本号。
- 例：
  ```xml
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.6</version>
    <relativePath/>
  </parent>
  ```

### 2. starter（启动器）

- 是一个“依赖聚合包”。
- `spring-boot-starter-web` 一次带来：
  - Spring MVC
  - 内嵌 Tomcat
  - Jackson
  - 等
- 不用手动一个个加这些依赖。

### 3. `@SpringBootApplication`

- 是组合注解，通常包含：
  - `@SpringBootConfiguration`：标记配置类
  - `@EnableAutoConfiguration`：开启自动配置
  - `@ComponentScan`：扫描当前包及其子包下的组件
- 作用：让 Spring Boot 知道“从这里开始扫描和自动配置”。

### 4. `SpringApplication.run(...)`

- 真正启动 Spring Boot 应用。
- 会创建 Spring 容器、启动内嵌 Tomcat、加载配置、扫描 Bean 等。
- 不是“执行这个类本身”。

### 5. `@RestController`

- 告诉 Spring 这是一个处理 HTTP 请求的控制器。
- 方法返回值会自动转成 JSON。

### 6. `@GetMapping("/api/hello")`

- 告诉 Spring 这个方法处理 GET 请求。
- 路径是 `/api/hello`。

### 7. 冒烟验证

- `mvn spring-boot:run` 启动应用。
- 看到 `Tomcat started on port 8080`。
- 浏览器访问 `http://localhost:8080/api/hello`，看到 `{"status":"ok"}`。
- 说明项目已经能作为 Web 服务运行。

## 文件与方法链路

### 文件职责

- `CampusAgentApplication`：Spring Boot 应用入口。
- `HelloController`：提供 `/api/hello` 冒烟接口。

### 请求链路

```text
浏览器 GET /api/hello
   ↓
DispatcherServlet
   ↓ 查映射表
HelloController.hello()
   ↓ 返回 Map
Spring 自动转 JSON
   ↓
{"status":"ok"}
```

## 今天答错的点（复习重点）

1. **`@SpringBootApplication` 是“告诉 Spring 这是启动入口”？** 不准确。  
   它是组合注解，作用是标记配置类、开启自动配置、扫描组件。
2. **`SpringApplication.run(...)` 是“在执行 CampusAgentApplication 类”？** 错。  
   它是真正启动 Spring Boot 应用的方法。
3. **`@RestController` 只表示接收 HTTP 请求？** 不完整。  
   它还表示方法返回值会自动转成 JSON。

## 下一步

- Task 2：配置化（application.yml + AppProperties）。
