# Task 8 总结：MyBatis-Plus + HikariCP 替换裸 JDBC（2026-08-26）

> 今日状态：Task 8 完成，全量 33 个测试绿，已提交

## 今日范围

- `pom.xml`：增加 `spring-boot-starter-jdbc`、`mybatis-plus-spring-boot3-starter`
- `schema.sql`：从 Database.SCHEMA 抽出，统一建表 DDL
- `application.yml`：增加 datasource + spring.sql.init
- `Database.java`：从 classpath 读 schema.sql
- `entity/`：Assignment / Exam / Todo / Course / Event / CourseOverride
- `mapper/`：6 个 BaseMapper 接口
- `ItemRepository.java`：改为 MP 实现
- `Main.java`：改为从 Spring 容器获取 Bean
- 测试：改造成 SpringBootTest + 动态数据源 + 清库

## 今日知识点

### 1. ORM

- 把数据库表和 Java 类对应起来。
- 操作对象，框架帮你生成/执行 SQL。
- 省掉手写 SQL 和 ResultSet 转换。

### 2. MyBatis-Plus BaseMapper<T>

- 继承后自动获得 insert / selectById / selectList / updateById / delete。
- 不需要手写 SQL。
- `T` 是实体类。

### 3. @TableName / @TableId

- `@TableName("assignments")`：类对应哪张表。
- `@TableId(type = IdType.AUTO)`：主键字段，数据库自增。

### 4. 下划线转驼峰

- 数据库 `due_date` ↔ Java `dueDate`
- MyBatis-Plus 自动转换，不用手动映射。

### 5. HikariCP 连接池

- 维护多个数据库连接。
- 每次从池里借，用完还回。
- 解决多线程共用单连接的问题。

### 6. spring.sql.init

- Spring Boot 启动时自动执行 `schema.sql` 建表。
- Database 保留从 classpath 读 schema.sql，CLI/测试也能用同一份 DDL。

### 7. ItemRepository 改造

- 业务表（assignments/exams/todos/courses/events/course_overrides）用 Mapper。
- messages / raw_inbox 仍用 JDBC + DataSource。
- 方法签名保持不变，调用方不需要大改。

### 8. Main 改造

- 不再手动 `new ItemRepository(db)`。
- 改为启动 Spring 容器（WebApplicationType.NONE），从容器拿 Bean。

### 9. 测试改造

- 测试类改为 `@SpringBootTest`。
- `@DynamicPropertySource` 把数据库指向临时文件。
- 每个测试前用 `@BeforeEach` 清库，避免数据互相污染。

## 文件与方法链路

```text
Spring Boot 启动
   ↓ spring.sql.init
schema.sql 建表
   ↓
DataSource / HikariCP
   ↓
MyBatis-Plus Mapper
   ↓
ItemRepository
   ↓
AssistantService / Controller
```

## 今天踩坑记录

1. **`Main.java` 还手动 new ItemRepository(db)**：改为从 Spring 容器获取。
2. **`AssistantService` 里 catch SQLException 编译报错**：MP 方法不再抛 SQLException，删掉多余 catch。
3. **SpringBootTest 共享数据库导致测试互相污染**：加 `@BeforeEach` 清库。
4. **临时文件误删/覆盖顺序**：大文件用临时文本文件替换时要注意先复制再删。

## 下一步

- Task 9：按记录 id 纠正（无状态化）+ GET /api/items 记录面板。
