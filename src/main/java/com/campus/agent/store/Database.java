package com.campus.agent.store;

import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** 打开（必要时创建）SQLite 数据库并初始化表结构。用完必须 close()。 */
public class Database implements AutoCloseable {

    private final Connection conn;

    public Database(Path dbFile) throws SQLException {
        try {
            if (dbFile.getParent() != null) {
                Files.createDirectories(dbFile.getParent());
            }
        } catch (Exception e) {
            throw new SQLException("无法创建数据目录: " + dbFile.getParent(), e);
        }
        conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL;");
            st.execute("PRAGMA busy_timeout=5000;");
            String schema;
            try (var in = Database.class.getResourceAsStream("/schema.sql")) {
                schema = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new SQLException("读取 schema.sql 失败: " + e.getMessage(), e);
            }
            for (String sql : schema.split(";")) {
                if (!sql.isBlank()) {
                    st.execute(sql);
                }
            }
            for (String sql : MIGRATIONS) {
                try {
                    st.execute(sql);
                } catch (SQLException e) {
                    String msg = e.getMessage().toLowerCase();
                    if (!msg.contains("duplicate column") && !msg.contains("no such column")) {
                        throw e;
                    }
                }
            }
        }
    }

    public Connection conn() {
        return conn;
    }

    @Override
    public void close() throws SQLException {
        conn.close();
    }

    /** 老库结构迁移：新库执行会因列已存在而报错，由上面的容错吞掉（幂等）。 */
    private static final String[] MIGRATIONS = {
            "ALTER TABLE courses ADD COLUMN start_time TEXT",
            "ALTER TABLE courses ADD COLUMN end_time TEXT",
            "ALTER TABLE courses ADD COLUMN weekday INTEGER",
            "ALTER TABLE courses ADD COLUMN weeks TEXT",
            "ALTER TABLE study_progress RENAME COLUMN content TO context"
    };
}
