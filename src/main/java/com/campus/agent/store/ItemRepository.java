package com.campus.agent.store;

import com.campus.agent.model.ExtractedItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 所有数据库读写。裸 JDBC + PreparedStatement（防 SQL 注入，面试常考）。 */
public class ItemRepository {

    private final Database db;

    public ItemRepository(Database db) {
        this.db = db;
    }

    /** 按类型写入对应表，返回自增 id。 */
    public long insert(ExtractedItem item, long sourceMessageId) throws SQLException {
        Connection c = db.conn();
        String sql = switch (item.type()) {
            case "assignment" ->
                    "INSERT INTO assignments(title,course,teacher,content,due_date,due_time,source_message_id) VALUES(?,?,?,?,?,?,?)";
            case "exam" ->
                    "INSERT INTO exams(title,course,teacher,content,location,due_date,due_time,source_message_id) VALUES(?,?,?,?,?,?,?,?)";
            case "todo" ->
                    "INSERT INTO todos(title,content,due_date,due_time,source_message_id) VALUES(?,?,?,?,?)";
            case "course" ->
                    "INSERT INTO courses(title,teacher,location,source_message_id) VALUES(?,?,?,?)";
            case "event" ->
                    "INSERT INTO events(title,content,location,due_date,due_time,source_message_id) VALUES(?,?,?,?,?,?)";
            default -> throw new IllegalArgumentException("未知类型: " + item.type());
        };
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            switch (item.type()) {
                case "assignment" -> setParams(ps, item.title(), item.course(), item.teacher(),
                        item.content(), item.dueDate(), item.dueTime(), sourceMessageId);
                case "exam" -> setParams(ps, item.title(), item.course(), item.teacher(),
                        item.content(), item.location(), item.dueDate(), item.dueTime(), sourceMessageId);
                case "todo" -> setParams(ps, item.title(), item.content(), item.dueDate(), item.dueTime(), sourceMessageId);
                case "course" -> setParams(ps, item.title(), item.teacher(), item.location(), sourceMessageId);
                case "event" -> setParams(ps, item.title(), item.content(), item.location(),
                        item.dueDate(), item.dueTime(), sourceMessageId);
                default -> throw new IllegalArgumentException("未知类型: " + item.type());
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    /** 按 id 更新记录（全字段覆盖，由调用方保证已合并好）。 */
    public boolean updateById(ExtractedItem item, long id) throws SQLException {
        String sql = switch (item.type()) {
            case "assignment" ->
                    "UPDATE assignments SET title=?,course=?,teacher=?,content=?,due_date=?,due_time=? WHERE id=?";
            case "exam" ->
                    "UPDATE exams SET title=?,course=?,teacher=?,content=?,location=?,due_date=?,due_time=? WHERE id=?";
            case "todo" ->
                    "UPDATE todos SET title=?,content=?,due_date=?,due_time=? WHERE id=?";
            case "course" ->
                    "UPDATE courses SET title=?,teacher=?,location=? WHERE id=?";
            case "event" ->
                    "UPDATE events SET title=?,content=?,location=?,due_date=?,due_time=? WHERE id=?";
            default -> throw new IllegalArgumentException("未知类型: " + item.type());
        };
        try (PreparedStatement ps = db.conn().prepareStatement(sql)) {
            switch (item.type()) {
                case "assignment" -> setParams(ps, item.title(), item.course(), item.teacher(),
                        item.content(), item.dueDate(), item.dueTime(), id);
                case "exam" -> setParams(ps, item.title(), item.course(), item.teacher(),
                        item.content(), item.location(), item.dueDate(), item.dueTime(), id);
                case "todo" -> setParams(ps, item.title(), item.content(), item.dueDate(), item.dueTime(), id);
                case "course" -> setParams(ps, item.title(), item.teacher(), item.location(), id);
                case "event" -> setParams(ps, item.title(), item.content(), item.location(),
                        item.dueDate(), item.dueTime(), id);
                default -> throw new IllegalArgumentException("未知类型: " + item.type());
            }
            return ps.executeUpdate() == 1;
        }
    }

    /** 读取全部业务记录，按 作业→考试→待办→课程→日程 顺序。 */
    public List<StoredItem> allItems() throws SQLException {
        List<StoredItem> list = new ArrayList<>();
        queryInto(list, "assignment", "SELECT id,'assignment' AS type,title,course,teacher,content,NULL AS location,due_date,due_time,status FROM assignments ORDER BY id");
        queryInto(list, "exam", "SELECT id,'exam' AS type,title,course,teacher,content,location,due_date,due_time,status FROM exams ORDER BY id");
        queryInto(list, "todo", "SELECT id,'todo' AS type,title,NULL AS course,NULL AS teacher,content,NULL AS location,due_date,due_time,status FROM todos ORDER BY id");
        queryInto(list, "course", "SELECT id,'course' AS type,title,NULL AS course,NULL AS teacher,NULL AS content,location,NULL AS due_date,NULL AS due_time,'active' AS status FROM courses ORDER BY id");
        queryInto(list, "event", "SELECT id,'event' AS type,title,NULL AS course,NULL AS teacher,content,location,due_date,due_time,status FROM events ORDER BY id");
        return list;
    }

    public Optional<StoredItem> getById(String type, long id) throws SQLException {
        String sql = switch (type) {
            case "assignment" -> "SELECT id,'assignment' AS type,title,course,teacher,content,NULL AS location,due_date,due_time,status FROM assignments WHERE id=?";
            case "exam" -> "SELECT id,'exam' AS type,title,course,teacher,content,location,due_date,due_time,status FROM exams WHERE id=?";
            case "todo" -> "SELECT id,'todo' AS type,title,NULL AS course,NULL AS teacher,content,NULL AS location,due_date,due_time,status FROM todos WHERE id=?";
            case "course" -> "SELECT id,'course' AS type,title,NULL AS course,NULL AS teacher,NULL AS content,location,NULL AS due_date,NULL AS due_time,'active' AS status FROM courses WHERE id=?";
            case "event" -> "SELECT id,'event' AS type,title,NULL AS course,NULL AS teacher,content,location,due_date,due_time,status FROM events WHERE id=?";
            default -> throw new IllegalArgumentException("未知类型: " + type);
        };
        try (PreparedStatement ps = db.conn().prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        }
        return Optional.empty();
    }

    public long insertMessage(String role, String content) throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement(
                "INSERT INTO messages(role, content) VALUES(?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, role);
            ps.setString(2, content);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public long insertRaw(String content, String reason) throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement(
                "INSERT INTO raw_inbox(content, reason) VALUES(?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, content);
            ps.setString(2, reason);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private void queryInto(List<StoredItem> list, String type, String sql) throws SQLException {
        try (Statement st = db.conn().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
    }

    private StoredItem map(ResultSet rs) throws SQLException {
        return new StoredItem(
                rs.getLong("id"), rs.getString("type"), rs.getString("title"),
                rs.getString("course"), rs.getString("teacher"), rs.getString("content"),
                rs.getString("location"), rs.getString("due_date"), rs.getString("due_time"),
                rs.getString("status"));
    }

    private void setParams(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            if (params[i] == null) {
                ps.setObject(i + 1, null);
            } else if (params[i] instanceof Long l) {
                ps.setLong(i + 1, l);
            } else {
                ps.setString(i + 1, params[i].toString());
            }
        }
    }
}