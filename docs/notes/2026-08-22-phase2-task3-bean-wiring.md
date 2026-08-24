# Task 3 总结：Bean 装配（2026-08-22）

> 今日状态：Task 3 完成，Spring Boot 启动成功，Bean 装配无误，已提交

## 今日范围

- `src/main/java/com/campus/agent/config/AppBeans.java`

## 今日知识点

### 1. `@Configuration`

- 标记一个类是 Spring 的配置类。
- 配置类里可以定义 `@Bean` 方法。

### 2. `@Bean`

- 告诉 Spring 容器：“这个对象由我来生产。”
- Spring 启动时会调用该方法，把返回值放进 IoC 容器管理。
- 其他代码需要这个类型时，容器直接把 Bean 给它。

### 3. `@Bean` 方法的参数 = 依赖注入

- Spring 会先到容器里找对应类型的 Bean；
- 有就直接用；
- 没有会尝试创建后再注入。

### 4. `Database` 的关闭由 Spring 管理

- `Database` 实现 `AutoCloseable`。
- 作为 Bean 后，Spring 在应用关闭时会自动调用 `close()`。
- 不需要手动关闭。

### 5. AppBeans 装配了哪些 Bean

- `Database`：数据库连接管理
- `ItemRepository`：数据读写
- `LlmClient`：DeepSeekClient
- `AssistantService`：核心编排

## 文件与方法链路

```text
AppProperties
   ↓ 注入
AppBeans.database() → Database Bean
AppBeans.itemRepository(Database) → ItemRepository Bean
AppBeans.llmClient(AppProperties) → LlmClient Bean
AppBeans.assistantService(LlmClient, ItemRepository, Database) → AssistantService Bean
```

## 今天答错的点

- 无明显答错；概念理解过关。

## 下一步

- Task 4：ChatController 非流式接口 + MockMvc 集成测试。
