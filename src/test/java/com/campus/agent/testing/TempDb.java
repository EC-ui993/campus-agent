package com.campus.agent.testing;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;

/**
 * 测试用临时数据库。
 *
 * <p>生产环境的 {@code schema.sql} 是 MySQL 方言，而单元测试跑在 SQLite 上（快、免装 MySQL）。
 * 本类读<b>同一份</b> {@code schema.sql}，做方言翻译后再交给 SQLite 建表——
 * 只有一份 DDL，所以不存在"两份定义互相漂移"的问题。
 *
 * <p><b>为什么不直接把 schema.sql 丢给 SQLite：</b>SQLite 对类型名极其宽容，
 * {@code id BIGINT AUTO_INCREMENT PRIMARY KEY} 不会报错，而是被理解成
 * "类型 = BIGINT AUTO_INCREMENT"，于是该列<b>不再是 rowid 别名、不再自增</b>——
 * 建表成功、插入成功、读回来 id 是 null。方言差异会以"静默行为改变"的形式出现，
 * 不会响亮报错。{@link #toSqlite} 末尾的守卫就是为了把这种沉默变成异常。
 */
public final class TempDb {

    private TempDb() {
    }

    /**
     * 方言翻译规则，顺序敏感。
     *
     * <p><b>这张表本身就是「SQLite 与 MySQL 差在哪」的活文档</b>——
     * schema.sql 里一旦出现新的 MySQL 专有写法，就在这里补一条。
     */
    private static final String[][] DIALECT = {
            // MySQL 自增主键 → SQLite 的 rowid 别名（必须恰好是 INTEGER，否则不自增）
            {"BIGINT AUTO_INCREMENT PRIMARY KEY", "INTEGER PRIMARY KEY AUTOINCREMENT"},
            // MySQL 时间戳默认值 → SQLite 的本地时间函数
            {"DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP", "TEXT NOT NULL DEFAULT (datetime('now','localtime'))"},
            // MySQL 变长字符串 → SQLite 只有 TEXT（类型名对 SQLite 无约束力）
            {"VARCHAR\\(\\d+\\)", "TEXT"},
    };

    /** 翻译后不允许残留的 MySQL 专有记号；残留即抛错，不让"静默行为差异"溜过去。 */
    private static final String[] MYSQL_ONLY_TOKENS = {
            "AUTO_INCREMENT", "CURRENT_TIMESTAMP", "VARCHAR(", "DATETIME",
    };

    /**
     * 注册测试数据源：新建一个 SQLite 临时库（表已按翻译后的 DDL 建好），
     * 并关闭 Spring 的 schema.sql 自动执行——建表由本类负责，因为必须先翻译。
     *
     * <p>各 {@code @SpringBootTest} 的写法因此收敛为一行：
     * <pre>{@code
     * @DynamicPropertySource
     * static void props(DynamicPropertyRegistry registry) {
     *     TempDb.register(registry);
     * }
     * }</pre>
     */
    public static void register(DynamicPropertyRegistry registry) {
        String url = newDatabaseUrl();
        registry.add("spring.datasource.url", () -> url);
        // application.yml 里写的是 MySQL 驱动，测试要显式换成 SQLite 的
        registry.add("spring.datasource.driver-class-name", () -> "org.sqlite.JDBC");
        registry.add("spring.sql.init.mode", () -> "never");
    }

    /** 新建一个建好表的 SQLite 临时库，返回其 JDBC URL。 */
    static String newDatabaseUrl() {
        try {
            Path file = Files.createTempFile("agent-test-", ".db");
            file.toFile().deleteOnExit();
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + file)) {
                String ddl = toSqlite(readSchema());
                ScriptUtils.executeSqlScript(conn,
                        new EncodedResource(new ByteArrayResource(ddl.getBytes(StandardCharsets.UTF_8)),
                                StandardCharsets.UTF_8));
            }
            return "jdbc:sqlite:" + file;
        } catch (Exception e) {
            throw new IllegalStateException("测试库初始化失败: " + e.getMessage(), e);
        }
    }

    /** 读生产 schema.sql 的原文。 */
    private static String readSchema() throws Exception {
        try (var in = new ClassPathResource("schema.sql").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 把 MySQL 方言的 schema.sql 翻译成 SQLite 能<b>正确</b>执行的 DDL（不只是"能执行"）。 */
    static String toSqlite(String mysqlSchema) {
        String sql = mysqlSchema;
        for (String[] rule : DIALECT) {
            sql = sql.replaceAll(rule[0], rule[1]);
        }
        for (String token : MYSQL_ONLY_TOKENS) {
            if (sql.contains(token)) {
                throw new IllegalStateException(
                        "方言翻译不完整：DDL 中仍残留 MySQL 专有写法 [" + token
                                + "]，请在 TempDb.DIALECT 中补一条替换规则");
            }
        }
        return sql;
    }
}
