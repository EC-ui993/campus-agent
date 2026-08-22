# Task 0 总结：Spring Boot 概念启蒙（2026-08-21）

> 今日状态：阶段 2 开始，完成 Task 0 纯概念理解，无代码；验收通过

## 今日范围

- 无代码
- 产出：本笔记

## 今日知识点

### 1. 框架 vs 类库（控制反转）

- **类库**：你主动调用它，你控制流程。例如 Jackson 的 `MAPPER.readTree(...)`。
- **框架**：它调用你的代码，它控制流程。例如 Spring Boot 启动时自动创建对象、处理请求。
- 控制反转（IoC）：控制权从“你”手里转到“框架”手里。

### 2. IoC 容器 / Bean

- IoC 容器：帮你创建和管理对象的“对象仓库”。
- Bean：被 IoC 容器管理的对象。
- 对比阶段 1：
  - 阶段 1 在 `main` 里手动 `new` 并组装依赖；
  - Spring 由容器自动创建和管理。

### 3. 依赖注入（DI）

- 一个对象需要的依赖，不由它自己 `new`，而是由容器在创建它时自动传入。
- 阶段 1 雏形：
  ```java
  public AssistantService(LlmClient llm, ItemRepository repo, Database db) { ... }
  ```
- 相同点：都是外部把依赖传进来，类自己不 new。
- 不同点：阶段 1 的外部是 `Main`，Spring 的外部是 IoC 容器。

### 4. 注解的本质

- 注解本身只是类/方法上的标记，不干活。
- Spring 启动时用**反射**读取注解，并根据标记执行对应行为。
- 例：
  - `@RestController` → 注册成处理 HTTP 请求的 Bean
  - `@GetMapping("/api/hello")` → 绑定 URL 和方法

### 5. Spring MVC 请求流程

```text
浏览器
   ↓ HTTP 请求
DispatcherServlet（前端控制器，Spring 自动提供）
   ↓ 根据 URL 找 Controller 方法
Controller 方法执行
   ↓ 返回 Java 对象
Spring 自动转成 JSON
   ↓ HTTP 响应
浏览器
```

### 6. SSE 与普通响应的区别

- 普通 HTTP：一问一答，一次给完。
- SSE：保持连接，模型生成一段就推送一段，浏览器边收边显示。
- 效果：打字机效果，更贴近真实聊天。

## 验收回答（整体流程）

> Spring Boot 启动后，一个 HTTP 请求先传入 Spring 自带的前端控制器 DispatcherServlet，然后根据 URL 调用对应的 Controller 方法；Spring 框架用反射读取注解，决定调用哪个方法、怎么处理；如果需要对象，IoC 容器会创建或找到对应的 Bean 传入；Controller 返回 Java 对象后，Spring 自动转成 JSON 返回给浏览器。整个过程是控制反转的体现。

## 今天答错的点（复习重点）

1. **“注解是通过反射获取类的信息得到的”** 不准确。  
   正确理解：注解是写在类/方法上的标记；Spring 用反射读取这些标记，从而知道该怎么处理。

## 下一步

- Task 1：项目改造成 Spring Boot 工程（pom、入口、Hello 冒烟）。
