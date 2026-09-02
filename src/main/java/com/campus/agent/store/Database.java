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
                    if (!e.getMessage().toLowerCase().contains("duplicate column")) {
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

    private static final String[] MIGRATIONS = {
            "ALTER TABLE courses ADD COLUMN start_time TEXT",
            "ALTER TABLE courses ADD COLUMN end_time TEXT",
            "ALTER TABLE courses ADD COLUMN weekday INTEGER",
            "ALTER TABLE courses ADD COLUMN weeks TEXT"
    };

    private static final String SCHEMA = """
            CREATE TABLE IF NOT EXISTS assignments(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              title TEXT NOT NULL,
              course TEXT,
              teacher TEXT,
              content TEXT,
              due_date TEXT,
              due_time TEXT,
              status TEXT NOT NULL DEFAULT 'pending',
              source_message_id INTEGER,
              created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')));
            CREATE TABLE IF NOT EXISTS exams(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              title TEXT NOT NULL,
              course TEXT,
              teacher TEXT,
              content TEXT,
              location TEXT,
              due_date TEXT,
              due_time TEXT,
              status TEXT NOT NULL DEFAULT 'upcoming',
              source_message_id INTEGER,
              created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')));
            CREATE TABLE IF NOT EXISTS todos(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              title TEXT NOT NULL,
              content TEXT,
              due_date TEXT,
              due_time TEXT,
              status TEXT NOT NULL DEFAULT 'pending',
              source_message_id INTEGER,
              created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')));
            CREATE TABLE IF NOT EXISTS courses(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              title TEXT NOT NULL,
              teacher TEXT,
              location TEXT,
                weekday INTEGER,
                weeks TEXT,
                start_time TEXT,
                end_time TEXT,
              source_message_id INTEGER,
              created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')));
            CREATE TABLE IF NOT EXISTS events(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              title TEXT NOT NULL,
              content TEXT,
              location TEXT,
              due_date TEXT,
              due_time TEXT,
              status TEXT NOT NULL DEFAULT 'upcoming',
              source_message_id INTEGER,
              created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')));
            CREATE TABLE IF NOT EXISTS messages(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              role TEXT NOT NULL,
              content TEXT NOT NULL,
              created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')));
            CREATE TABLE IF NOT EXISTS raw_inbox(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              content TEXT NOT NULL,
              reason TEXT,
              created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')));
            CREATE TABLE IF NOT EXISTS course_overrides(
               id INTEGER PRIMARY KEY AUTOINCREMENT,
               course_id INTEGER,
               course_title TEXT,
               override_date TEXT NOT NULL,
               kind TEXT NOT NULL,
               new_start_time TEXT,
               new_end_time TEXT,
               new_location TEXT,
               note TEXT,
               source_message_id INTEGER,
               created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')));
            CREATE TABLE IF NOT EXISTS internships(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              company TEXT NOT NULL,
              position TEXT NOT NULL,
              city TEXT,
              salary TEXT,
              deadline TEXT,
              jd TEXT,
              link TEXT,
              status TEXT NOT NULL DEFAULT 'new',
              source_message_id INTEGER,
              created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')));
            CREATE TABLE IF NOT EXISTS profile(
              id INTEGER PRIMARY KEY,
              skills TEXT,
              target_role TEXT,
              target_city TEXT,
              grade TEXT,
              note TEXT,
              updated_at TEXT NOT NULL DEFAULT (datetime('now','localtime')));
            
            CREATE TABLE IF NOT EXISTS study_progress(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              study_date TEXT NOT NULL,
              content TEXT NOT NULL,
              source_message_id INTEGER,
              created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')));  
            """;
}