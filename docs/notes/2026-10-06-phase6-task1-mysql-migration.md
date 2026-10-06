# 阶段 6 Task 1 总结：SQLite → MySQL 迁移（2026-10-06）

> 验收三项全过：项目在 MySQL 上全功能跑通 ✅ / 能讲清 2 条 EXPLAIN 输出 ✅ / 生产代码中已无 `Database` 类 ✅
> 环境：MySQL **8.4.11**（Docker 容器 `agent-mysql`，宿主映射 `:3306`）

## 一、心智模型：SQLite 是一个文件，MySQL 是一个服务

这一条想通了，后面全是它的推论。

| | SQLite | MySQL |
|---|---|---|
| 引擎在哪 | **你的 Java 程序里**（一个 jar 包） | **独立进程** `mysqld` |
| 程序怎么访问 | 直接读写 `data/agent.db` | 通过 TCP **连过去** |
| 有几个库 | 一个文件 = 一个库 | 一个服务里放**很多个库** |
| 需要连什么 | 一个文件路径 | **地址 + 用户名 + 密码 + 库名** |
| 谁能访问 | **操作系统**的文件权限 | **MySQL 自己**管（`CREATE USER` / `GRANT`） |
| 端口 | 没有 | 3306 |

**三个推论：**

1. **为什么要"建库"**：SQLite 里建库 = 产生一个文件（测试代码里的 `Files.createTempFile` 就在干这事）；MySQL 里库是服务内部的**命名空间**，只能发 `CREATE DATABASE` 给它。
   → 由此有了 **`agent`**（真实数据）和 **`agent_test`**（测试用）两个库。
2. **为什么需要用户名密码**：它是**网络服务**，任何能连到 3306 的人都够得着。SQLite 时代别人得先摸到你的硬盘。
3. **为什么"装 MySQL"这么重**：装的不是文件，是一个**要常驻运行的服务**。

## 二、账号体系：MySQL 的访问控制是**两道独立的门**

| | 第一道门 | 第二道门 |
|---|---|---|
| 管什么 | 能不能**连进来** | 连进来能**干什么** |
| 靠什么 | `'campus'@'%'` + 密码 | `GRANT` 的权限清单 |
| SQLite 对应 | 文件权限 | —— **没有这道门** |

**两道门独立**：`'campus'@'%'` + 只 `GRANT SELECT` = 全世界能连，但只能读。
`'campus'@'localhost'` + `ALL PRIVILEGES` = 只有本机能连，但能 `DROP DATABASE`。

**本项目实际授权：**

```sql
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX ON agent.*    TO 'campus'@'%';
GRANT ALL PRIVILEGES                                    ON agent_test.* TO 'campus'@'%';
```

- `CREATE`：启动时 `spring.sql.init` 要建表
- `ALTER`：将来改表结构
- `INDEX`：建索引
- **`DROP` 故意不给**：应用从不删表

**最小权限的实战验证**：搬运数据时本想用 `TRUNCATE`，但 **MySQL 的 `TRUNCATE` 需要 `DROP` 权限**（它本质接近 drop + create），而 `DELETE` 只要 `DELETE` 权限 → **改用 `DELETE`，`campus` 自己就能干完**。
→ 当初"不给 DROP"的决定被证明是"给得刚刚好"：**最小权限不是越少越好，是刚好够。**

**另一个坑**：应用从宿主机经端口映射连容器，MySQL 眼里源 IP 是 Docker 网桥地址，**不是 127.0.0.1** → 这就是必须用 `@'%'` 而不是 `@'localhost'` 的原因。`@'localhost'` 只认同一台机器上的 socket/回环连接。

## 三、`schema.sql` 方言化：TEXT 的三颗雷

**SQLite 的 `TEXT` 是"万能字符串"；MySQL 的 `TEXT` 是"大文本"专用类型。** 差异导致三处会直接建表失败：

| 位置 | 原写法 | 报错 | 改成 |
|---|---|---|---|
| `reports.report_date` | `TEXT NOT NULL UNIQUE` | **1170**（BLOB/TEXT 列建索引必须给前缀长度） | `VARCHAR(32) NOT NULL UNIQUE` |
| `status` × 5 张表 | `TEXT NOT NULL DEFAULT '字面量'` | **1101**（BLOB/TEXT 列不能有字面量默认值） | `VARCHAR(20)` |
| `assignments.due_date` | `TEXT`（计划要给它建索引） | **1170**（同第一颗） | `VARCHAR(32)` |

**完整改造规则（13 张表）：**

| # | 原 | 改成 |
|---|---|---|
| 1 | `id INTEGER PRIMARY KEY AUTOINCREMENT` | `id BIGINT AUTO_INCREMENT PRIMARY KEY` |
| 2 | `id INTEGER PRIMARY KEY`（裸主键，semester/profile） | **同上统一**——否则翻译规则匹配不上，SQLite 侧静默失去自增 |
| 3 | `created_at/updated_at TEXT DEFAULT (datetime('now','localtime'))` | `DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP` |
| 4 | `report_date` / `status` / `due_date` / `due_time` 等**短定长字符串** | `VARCHAR(n)` |
| 5 | 其余 `TEXT`（title / content / note / jd / link…） | **保持不动**（只改必须改的） |

**通用判据**：会被 SQL 拿来做**条件、排序、索引**的字符串列 → `VARCHAR`；纯存储不参与比较的 → `TEXT`。
`due_time` 只参与排序（`ORDER BY` 用 TEXT 不报错），但**要进复合索引就必须是 VARCHAR**——所以它最后也改了。

## 四、测试层：单份 DDL + 方言翻译器（P2 方案）

**问题**：生产 `schema.sql` 是 MySQL 方言，测试跑在 SQLite 上（快、免装 MySQL）。

**错误解法**：维护两份 DDL → 必然漂移，而且**守卫测试也救不了**：列名集合相同 ≠ 行为相同（`report_date` 两侧同名同 `UNIQUE`，parity 测试会绿而 MySQL 建表会红）。另外查 `information_schema` 需要 MySQL 连接，与"单测免装 MySQL"自相矛盾。

**采用解法**：**只保留一份 `schema.sql`**，`TempDb` 读**同一份文件**做机械替换后交给 SQLite：

```java
private static final String[][] DIALECT = {
    {"BIGINT AUTO_INCREMENT PRIMARY KEY", "INTEGER PRIMARY KEY AUTOINCREMENT"},
    {"DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP", "TEXT NOT NULL DEFAULT (datetime('now','localtime'))"},
    {"VARCHAR\\(\\d+\\)", "TEXT"},
};
```

**这张三行的表本身就是「SQLite 与 MySQL 差在哪」的活文档。** 漂移在结构上不可能发生。

### 守卫：把"沉默"变成"报错"

```java
private static final String[] MYSQL_ONLY_TOKENS = {"AUTO_INCREMENT", "CURRENT_TIMESTAMP", "VARCHAR(", "DATETIME"};
// 翻译后若残留任一记号 → 抛异常，并指出漏了哪个
```

**为什么必须有它**：这次迁移最先踩的坑就是**SQLite 不报错**。

`id BIGINT AUTO_INCREMENT PRIMARY KEY` 丢给 SQLite，它**不报语法错**——SQLite 的类型名允许多个词，于是它理解成"类型 = `BIGINT AUTO_INCREMENT`"。而 SQLite 只把**恰好是 `INTEGER` 且 `PRIMARY KEY`** 的列当 rowid 别名 → **该列不再自增** → 插入成功、读回来 `id` 是 `null` → `NullPointerException`。

> **症状**：82 个测试挂了 32 个（Errors 30 / Failures 2），报错是 `Assignment.getId()` 返回 null。
> **教训**：**SQLite 的宽容会把方言错误吞掉，只留下行为差异。** 守卫的作用就是不让它沉默。

（顺带印证：`SemesterTest`、profile 相关测试仍然绿——因为代码**显式 `setId(1L)`**，不依赖自增。）

### 一个容易漏的细节

测试侧必须**同时**关掉 Spring 的建表：

```java
registry.add("spring.sql.init.mode", () -> "never");
```

不关的话，Spring 启动时还会拿 classpath 上的 MySQL 方言 `schema.sql` 再跑一遍 → **原地复现那个静默 bug**。建表由 `TempDb` 独家负责（因为必须先翻译）。

### 测试类样板收敛

9 个 `@SpringBootTest` 各自 5 行样板（`createTempFile` + 前缀各不相同）→ 收敛成 1 行：

```java
@DynamicPropertySource
static void props(DynamicPropertyRegistry registry) {
    TempDb.register(registry);
}
```

**9 × 5 = 45 行 → 9 行。** 测试数：82 → **85**（新增 `TempDbTest` 3 个）→ **83**（删掉 `DatabaseTest` 2 个）。

## 五、数据搬运（SQLite → MySQL，一次性）

**方案**：`data/migrate_to_mysql.py` 读 SQLite → 生成 `INSERT` 语句 → 经 `mysql` 客户端灌入。
**不装 MySQL 的 Python 驱动**（网络装包不可靠）——只读 SQLite + 生成文本，把"连库"交给客户端。

**四个坑：**

| # | 坑 | 解法 |
|---|---|---|
| 1 | **转义**：引号会截断语句；**反斜杠 SQLite 不转义、MySQL 转义**，`C:\Users` 会被吞 | `v.replace('\\','\\\\').replace("'","''")`——**从内往外，顺序不能反** |
| 2 | **NULL ≠ 空串**：`None` 必须输出不带引号的 `NULL`，写成 `'None'` 会静默污染数据 | 显式分支 |
| 3 | **必须保留原始 id**：`course_overrides.course_id`、`source_message_id` 靠它 | INSERT 显式带 `id` 列 |
| 4 | **显式列名**：别依赖两边列顺序一致 | `INSERT INTO t (列…) VALUES (…)` |

**执行细节**：dump 文件 UTF-8 **无 BOM**（有 BOM 第一个语句就语法错）；用 `cmd /c "... < dump.sql"` 重定向（PowerShell 管道会重新编码）；加 `--default-character-set=utf8mb4`。

**结果：13/13 表逐行一致**

| 表 | 行 | | 表 | 行 |
|---|---|---|---|---|
| internships | 9 | | courses | 11 |
| assignments | 4 | | course_overrides | 1 |
| messages | 308 | | semester / profile | 1 / 1 |
| reports | 28 | | raw_inbox | 7 |
| study_progress | 7 | | exams / todos / events | 0 / 0 / 0 |

中文完好：`internships.jd` 最长一条 `CHAR_LENGTH=699 / LENGTH=1755`（≈2.5 倍，多字节存储正确）。

## 六、EXPLAIN 两次对比（本次最核心的一张表）

**实验设计**：不能在真实库做——`agent.assignments` 只有 4 行，优化器会直接选全表扫描，有没有索引都一样。所以在 `agent_test` 造 **1000 行**（`due_date` 从 2026-01-01 起每天一条），查「2026 年 3 月」→ 命中 31 行（选择率 ~3%）。

```sql
SELECT * FROM assignments
WHERE due_date >= '2026-03-01' AND due_date <= '2026-03-31'
ORDER BY due_date, due_time;
```

| 列 | 建索引前 | 建索引后 |
|---|---|---|
| `type` | `ALL` | **`range`** |
| `possible_keys` | `NULL` | **`idx_assignments_due`** |
| `key` | `NULL` | **`idx_assignments_due`** |
| `key_len` | `NULL` | **`131`** |
| `rows` | `1000` | **`31`** |
| `filtered` | `11.11` | **`100.00`** |
| `Extra` | `Using where; Using filesort` | **`Using index condition`** |

**五列的含义：**

| 列 | 一句话 |
|---|---|
| `type` | 怎么找行：`ALL` 逐行翻 / `range` 走索引定位到区间。阶梯 `ALL < index < range < ref < eq_ref < const` |
| `possible_keys` | 有哪些索引**能用**。`NULL` = 一个都没有 → **先去看索引建没建** |
| `key` | 实际**用了**哪个。`NULL` = 都没用（但 `possible_keys` 有值 → 是成本问题，不是缺失问题） |
| `rows` | **预估**要**检查**多少行——**衡量工作量，不是结果行数** |
| `filtered` | 检查过的行里多少比例合格 |
| `Extra` | `Using filesort` = 额外排了一次序 |

**两个关键理解：**

1. **`rows` 是工作量，`rows × filtered ≈ 预估返回行数`。**
   有索引时 `31 × 100% = 31`；无索引时 `1000 × 11.11% ≈ 111`——**而实际只有 31 行，优化器高估了 3.6 倍**。没走索引时它只能靠统计信息盲估，**优化器不是神，它只是算得快**。

2. **`Using filesort` 为什么消失**：`ORDER BY due_date, due_time` 要求有序；复合索引 `(due_date, due_time)` 在 B+ 树里**本身就是这个顺序**，范围扫描读出来的行**天然有序** → 不需要"再排一遍"。
   **顺序的要求来自查询（`ORDER BY`），不是列的声明。** 索引不"执行"任何东西，它只是一份**排好序的结构**；因为**有序**，区间内条目**相邻**，所以能"读一段"就收工——**有序是因，连续是果**。

**`key_len = 131` 说明**：utf8mb4 下 `due_date VARCHAR(32)` 的字节上限约 128 + 开销 ≈ 131 → **范围过滤只用了第一列**；第二列 `due_time` 不参与过滤，**去保证排序了**。
→ **一个复合索引，两列分工：一列管"找得快"（WHERE），一列管"不用再排"（ORDER BY）。** 这正是当初非要把 `due_time` 改成 `VARCHAR(8)` 的原因。

## 七、副产品决策记录

### 1. 删除 `Database` 类：建表**单通道化**

原来有**两个建表器**同时在跑：`AppBeans` 的 `Database` bean（硬编码 `jdbc:sqlite:` + `PRAGMA` 语句）和 Spring 的 `spring.sql.init`。因为 schema 全是 `CREATE TABLE IF NOT EXISTS`，重复执行没暴露问题。

`Database` 是**孤儿 bean**（grep 主代码无任何消费者）→ 删除，`DatabaseTest` 一并删除，`sqlite-jdbc` 降为 `<scope>test</scope>`。

**顺带清了死配置/死代码**：`app.db-path` + `AppProperties.dbPath`（唯一消费者是被删的 bean）、`AppConfig.java`（grep 零引用）、`config.properties.example`，以及 **README 里 7 处过期内容**（SQLite 残留、测试数 80、`store/` 里的 `Database`、`config.properties` 选项，并补上 MySQL 建库/账号/环境变量步骤）。

> **教训**：删代码要连带查**公开文档**，否则留下"照做无效"的假说明书。

### 2. 索引写在哪儿：**必须写进 `CREATE TABLE`**

| 方案 | 问题 |
|---|---|
| 单独 `CREATE INDEX` 语句 | `spring.sql.init` 每次启动都执行 → 第二次报 `Duplicate key name`；而 **MySQL 不支持 `CREATE INDEX IF NOT EXISTS`** |
| 单独语句 + `continue-on-error` | 会吞掉所有 SQL 错误，包括真错误 |
| **写进 `CREATE TABLE` 的 `KEY`** | 索引成为表定义的一部分，`CREATE TABLE IF NOT EXISTS` 天然幂等 ✅ |

### 3. **"该上 Flyway"的那个别扭感**

`CREATE TABLE IF NOT EXISTS` **对已存在的表什么都不做**。所以每次改 schema 都要做**两件事**：

1. 改 `schema.sql`（面向**未来的全新安装**）
2. **另外记得**对已存在的库手动跑 `ALTER`（面向**现在**）

**这个坑在本次实验中当场咬了一次**：`agent_test` 的 `assignments` 是改 `due_time` 类型**之前**建的，`CREATE INDEX ... (due_date, due_time)` 直接报 **1170**（`due_time` 还是 TEXT）——明明 `schema.sql` 里已经是 `VARCHAR(8)` 了。真实库 `agent` 同理，最后是靠四行 `ALTER TABLE ... MODIFY` 补贴上去的。

> **判据**：一个人、一个库、改 schema 频率低 → 手动 `ALTER` + 记笔记，够用。
> 一旦出现**第二个环境**（Task 2 的 Compose 就会再起一个 MySQL）、或开始**记不住哪个库跑到哪一版** → 就该上 Flyway。
> **那个"每次都要记得"的别扭感，就是信号。**

### 4. 其他

- MySQL 容器默认时区是 **UTC**，`created_at` 存的是 UTC 时间。功能无影响（Java 从不读这两列），Task 2 写 compose 时顺手给 mysql 服务加 `TZ=Asia/Shanghai`。
- Maven 直连中央仓库不通（`Connection refused`）→ 新建 `~/.m2/settings.xml` 配阿里云镜像。根因：之前从不联网（依赖都在本地仓库），`mysql-connector-j` 是**第一个不在本地的新依赖**。
- 终端编码第三次咬人（`Invoke-RestMethod` 在响应头无 `charset` 时按 ISO-8859-1 解码）→ **终端显示 ≠ 数据内容**；判据是库里/浏览器里到底是什么。

## 八、下一步

- Task 2：Docker Compose 全家桶（`app` + `mysql:8` + `nginx`）
  - 注意：Compose 会起**第二个 MySQL 实例** → 那时"哪个库跑到哪一版"的问题会真正出现（见 §7.3）
  - `docker-compose.yml` 里 mysql 服务加 `TZ=Asia/Shanghai`
- Task 3：Python + FastAPI 复刻最小闭环
