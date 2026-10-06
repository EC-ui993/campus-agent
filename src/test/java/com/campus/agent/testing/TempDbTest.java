package com.campus.agent.testing;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TempDb 的方言翻译测试。
 *
 * <p>第一个测试就是那次「迁移后 82 个测试挂了 32 个」故障的回归测试：
 * MySQL 方言的 DDL 直接跑在 SQLite 上不会报错，只会静默地让主键失去自增能力，
 * 插入后 id 读回来是 null。
 */
class TempDbTest {

    /** 不传 id 插入，应当能拿到自动生成的主键——即 id 是 SQLite 的 rowid 别名。 */
    @Test
    void insertWithoutIdGeneratesKey() throws Exception {
        try (Connection conn = DriverManager.getConnection(TempDb.newDatabaseUrl());
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO assignments(title) VALUES(?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, "高数");
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next(), "插入后应当返回生成的 id");
                assertNotNull(keys.getObject(1),
                        "id 为 null 说明该列不是 SQLite 的 rowid 别名——方言翻译漏了 AUTO_INCREMENT");
            }
        }
        // 顺带覆盖 status 的 DEFAULT：上面只插了 title，若默认值在翻译中丢失，这条 insert 会失败
    }

    /** 翻译后的 DDL 建出的表数量，应与生产 schema.sql 一致。 */
    @Test
    void createsAllThirteenTables() throws Exception {
        try (Connection conn = DriverManager.getConnection(TempDb.newDatabaseUrl());
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            List<String> tables = new ArrayList<>();
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
            assertEquals(13, tables.size(), "表清单: " + tables);
        }
    }

    /** DDL 里出现翻译表覆盖不到的 MySQL 写法时，必须抛错而不是放行。 */
    @Test
    void rejectsUntranslatedMysqlSyntax() {
        // DATETIME 单独出现（没有跟上 NOT NULL DEFAULT CURRENT_TIMESTAMP）时，规则 2 不匹配 → 应被守卫拦下
        assertThrows(IllegalStateException.class,
                () -> TempDb.toSqlite("CREATE TABLE t(created_at DATETIME);"));
    }
}
