# Task 2 总结：配置化（application.yml + AppProperties）（2026-08-22）

> 今日状态：Task 2 完成，Spring Boot 启动成功，配置绑定正常，已提交

## 今日范围

- `src/main/resources/application.yml`：配置文件
- `src/main/java/com/campus/agent/config/AppProperties.java`：配置绑定对象
- `src/main/java/com/campus/agent/CampusAgentApplication.java`：加 `@ConfigurationPropertiesScan`

## 今日知识点

### 1. 约定优于配置

- Spring Boot 默认按约定读取配置。
- 配置文件默认叫 `application.yml` 或 `application.properties`。
- 默认位置：`src/main/resources/`。
- 不需要告诉 Spring“去读这个文件”，放在约定位置即可。

### 2. `@Value` vs `@ConfigurationProperties`

- `@Value("${app.model}")`：读单个配置值。
- `@ConfigurationProperties(prefix = "app")`：把 `application.yml` 里所有 `app.` 开头的配置，按字段名自动绑定成对象。
- 例：
  ```yaml
  app:
    api-key: ...
    model: deepseek-chat
  ```
  会绑定到：
  ```java
  @ConfigurationProperties(prefix = "app")
  public record AppProperties(
          String apiKey, String model, String baseUrl, String dbPath, String accessToken) {
  }
  ```

### 3. 环境变量覆盖

- 语法：`${DEEPSEEK_API_KEY:默认值}`
- 优先读环境变量 `DEEPSEEK_API_KEY`；
- 不存在时用冒号后的默认值。
- 好处：API Key 可以不写进文件，更安全。

### 4. `@ConfigurationPropertiesScan`

- 加在入口类上。
- 作用是让 Spring 扫描并注册 `@ConfigurationProperties` 类。
- 不加会报错：未通过 `@EnableConfigurationProperties` 注册、标记为 Spring 组件或通过 `@ConfigurationPropertiesScan` 扫描。

### 5. application.yml 常见错误

- 冒号后必须加空格：`port: 8080`，不能写 `port:8080`。
- 键名要和字段对应：`api-key` 对应 `apiKey`，不是 `api.key`。
- `base-url` 应该是 API 地址，`db-path` 才是数据库路径。
- 文件要放在 `src/main/resources/`，不是项目根目录。

## 文件与方法链路

### 文件职责

- `application.yml`：集中配置（端口、API Key、模型、数据库路径等）。
- `AppProperties`：把 `app.*` 配置绑定成 Java 对象。
- `CampusAgentApplication`：启动入口，开启 `@ConfigurationPropertiesScan`。

### 配置绑定链路

```text
application.yml
   ↓ Spring Boot 自动读取
app.* 配置
   ↓ @ConfigurationProperties(prefix = "app")
AppProperties 对象
   ↓ 注入到需要的地方
业务代码
```

## 今天答错的点 / 踩坑记录

1. **`application.yml` 放在项目根目录**。  
   应该放在 `src/main/resources/`。
2. **YAML 冒号后没空格**：`port:8080` 报错。
3. **键名写错**：`api.key` 不能绑定到 `apiKey`，应该是 `api-key`。
4. **`base-url` 值写错**：写成了 `data/agent.db`，应该是 `https://api.deepseek.com`。
5. **API Key 安全**：不要把 Key 发到聊天里；建议用环境变量注入，不要写进配置文件。

## 下一步

- Task 3：Bean 装配（把阶段 1 的类交给 Spring）。
