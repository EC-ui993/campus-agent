package com.campus.agent.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseTest {

    @Test
    void createsDbFileAndAllTables(@TempDir Path tmp) throws Exception {
        Path dbFile = tmp.resolve("test.db");
        try (Database db = new Database(dbFile)) {
            assertNotNull(db.conn());
            try (Statement st = db.conn().createStatement();
                 ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
                Set<String> tables = new HashSet<>();
                while (rs.next()) tables.add(rs.getString("name"));
                assertTrue(tables.containsAll(Set.of(
                                "assignments", "exams", "todos", "courses", "events", "messages", "raw_inbox")),
                        "缺少表: " + tables);
            }
        }
        assertTrue(Files.exists(dbFile));
    }

    @Test
    void secondOpenKeepsData(@TempDir Path tmp) throws Exception {
        Path dbFile = tmp.resolve("reopen.db");
        try (Database db = new Database(dbFile)) {
            try (Statement st = db.conn().createStatement()) {
                st.executeUpdate("INSERT INTO messages(role, content) VALUES('user','你好')");
            }
        }
        try (Database db = new Database(dbFile);
             Statement st = db.conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) AS c FROM messages")) {
            rs.next();
            assertEquals(1, rs.getInt("c"));
        }
    }
}