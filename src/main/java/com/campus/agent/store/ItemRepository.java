package com.campus.agent.store;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campus.agent.model.ExtractedItem;
import com.campus.agent.store.entity.Assignment;
import com.campus.agent.store.entity.Course;
import com.campus.agent.store.entity.CourseOverride;
import com.campus.agent.store.entity.Event;
import com.campus.agent.store.entity.Exam;
import com.campus.agent.store.entity.Todo;
import com.campus.agent.store.mapper.AssignmentMapper;
import com.campus.agent.store.mapper.CourseMapper;
import com.campus.agent.store.mapper.CourseOverrideMapper;
import com.campus.agent.store.mapper.EventMapper;
import com.campus.agent.store.mapper.ExamMapper;
import com.campus.agent.store.mapper.TodoMapper;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 所有数据库读写。MyBatis-Plus 管业务表，JDBC 管杂表（messages/raw_inbox）。 */
@Repository
public class ItemRepository {

    private final AssignmentMapper assignmentMapper;
    private final ExamMapper examMapper;
    private final TodoMapper todoMapper;
    private final CourseMapper courseMapper;
    private final EventMapper eventMapper;
    private final CourseOverrideMapper courseOverrideMapper;
    private final DataSource dataSource;

    public ItemRepository(AssignmentMapper assignmentMapper, ExamMapper examMapper, TodoMapper todoMapper,
                          CourseMapper courseMapper, EventMapper eventMapper,
                          CourseOverrideMapper courseOverrideMapper, DataSource dataSource) {
        this.assignmentMapper = assignmentMapper;
        this.examMapper = examMapper;
        this.todoMapper = todoMapper;
        this.courseMapper = courseMapper;
        this.eventMapper = eventMapper;
        this.courseOverrideMapper = courseOverrideMapper;
        this.dataSource = dataSource;
    }

    public long insert(ExtractedItem item, long sourceMessageId) {
        switch (item.type()) {
            case "assignment" -> {
                Assignment e = new Assignment();
                e.setTitle(item.title());
                e.setCourse(item.course());
                e.setTeacher(item.teacher());
                e.setContent(item.content());
                e.setDueDate(item.dueDate());
                e.setDueTime(item.dueTime());
                e.setStatus("pending");
                e.setSourceMessageId(sourceMessageId);
                assignmentMapper.insert(e);
                return e.getId();
            }
            case "exam" -> {
                Exam e = new Exam();
                e.setTitle(item.title());
                e.setCourse(item.course());
                e.setTeacher(item.teacher());
                e.setContent(item.content());
                e.setLocation(item.location());
                e.setDueDate(item.dueDate());
                e.setDueTime(item.dueTime());
                e.setStatus("upcoming");
                e.setSourceMessageId(sourceMessageId);
                examMapper.insert(e);
                return e.getId();
            }
            case "todo" -> {
                Todo e = new Todo();
                e.setTitle(item.title());
                e.setContent(item.content());
                e.setDueDate(item.dueDate());
                e.setDueTime(item.dueTime());
                e.setStatus("pending");
                e.setSourceMessageId(sourceMessageId);
                todoMapper.insert(e);
                return e.getId();
            }
            case "course" -> {
                Course e = new Course();
                e.setTitle(item.title());
                e.setTeacher(item.teacher());
                e.setLocation(item.location());
                e.setWeekday(item.weekday());
                e.setWeeks(item.weeks());
                e.setStartTime(item.startTime());
                e.setEndTime(item.endTime());
                e.setSourceMessageId(sourceMessageId);
                courseMapper.insert(e);
                return e.getId();
            }
            case "event" -> {
                Event e = new Event();
                e.setTitle(item.title());
                e.setContent(item.content());
                e.setLocation(item.location());
                e.setDueDate(item.dueDate());
                e.setDueTime(item.dueTime());
                e.setStatus("upcoming");
                e.setSourceMessageId(sourceMessageId);
                eventMapper.insert(e);
                return e.getId();
            }
            default -> throw new IllegalArgumentException("未知类型: " + item.type());
        }
    }

    public boolean updateById(ExtractedItem item, long id) {
        switch (item.type()) {
            case "assignment" -> {
                Assignment e = new Assignment();
                e.setId(id);
                e.setTitle(item.title());
                e.setCourse(item.course());
                e.setTeacher(item.teacher());
                e.setContent(item.content());
                e.setDueDate(item.dueDate());
                e.setDueTime(item.dueTime());
                return assignmentMapper.updateById(e) == 1;
            }
            case "exam" -> {
                Exam e = new Exam();
                e.setId(id);
                e.setTitle(item.title());
                e.setCourse(item.course());
                e.setTeacher(item.teacher());
                e.setContent(item.content());
                e.setLocation(item.location());
                e.setDueDate(item.dueDate());
                e.setDueTime(item.dueTime());
                return examMapper.updateById(e) == 1;
            }
            case "todo" -> {
                Todo e = new Todo();
                e.setId(id);
                e.setTitle(item.title());
                e.setContent(item.content());
                e.setDueDate(item.dueDate());
                e.setDueTime(item.dueTime());
                return todoMapper.updateById(e) == 1;
            }
            case "course" -> {
                Course e = new Course();
                e.setId(id);
                e.setTitle(item.title());
                e.setTeacher(item.teacher());
                e.setLocation(item.location());
                e.setWeekday(item.weekday());
                e.setWeeks(item.weeks());
                e.setStartTime(item.startTime());
                e.setEndTime(item.endTime());
                return courseMapper.updateById(e) == 1;
            }
            case "event" -> {
                Event e = new Event();
                e.setId(id);
                e.setTitle(item.title());
                e.setContent(item.content());
                e.setLocation(item.location());
                e.setDueDate(item.dueDate());
                e.setDueTime(item.dueTime());
                return eventMapper.updateById(e) == 1;
            }
            default -> throw new IllegalArgumentException("未知类型: " + item.type());
        }
    }

    public List<StoredItem> allItems() {
        List<StoredItem> list = new ArrayList<>();
        for (Assignment e : assignmentMapper.selectList(null)) {
            list.add(new StoredItem(e.getId(), "assignment", e.getTitle(), e.getCourse(), e.getTeacher(),
                    e.getContent(), null, e.getDueDate(), e.getDueTime(), null, null, e.getStatus()));
        }
        for (Exam e : examMapper.selectList(null)) {
            list.add(new StoredItem(e.getId(), "exam", e.getTitle(), e.getCourse(), e.getTeacher(),
                    e.getContent(), e.getLocation(), e.getDueDate(), e.getDueTime(), null, null, e.getStatus()));
        }
        for (Todo e : todoMapper.selectList(null)) {
            list.add(new StoredItem(e.getId(), "todo", e.getTitle(), null, null,
                    e.getContent(), null, e.getDueDate(), e.getDueTime(), null, null, e.getStatus()));
        }
        for (Course e : courseMapper.selectList(null)) {
            list.add(new StoredItem(e.getId(), "course", e.getTitle(), null, e.getTeacher(),
                    null, e.getLocation(), null, null, e.getStartTime(), e.getEndTime(), "active"));
        }
        for (Event e : eventMapper.selectList(null)) {
            list.add(new StoredItem(e.getId(), "event", e.getTitle(), null, null,
                    e.getContent(), e.getLocation(), e.getDueDate(), e.getDueTime(), null, null, e.getStatus()));
        }
        return list;
    }

    public Optional<StoredItem> getById(String type, long id) {
        switch (type) {
            case "assignment" -> {
                Assignment e = assignmentMapper.selectById(id);
                if (e == null) return Optional.empty();
                return Optional.of(new StoredItem(e.getId(), "assignment", e.getTitle(), e.getCourse(), e.getTeacher(),
                        e.getContent(), null, e.getDueDate(), e.getDueTime(), null, null, e.getStatus()));
            }
            case "exam" -> {
                Exam e = examMapper.selectById(id);
                if (e == null) return Optional.empty();
                return Optional.of(new StoredItem(e.getId(), "exam", e.getTitle(), e.getCourse(), e.getTeacher(),
                        e.getContent(), e.getLocation(), e.getDueDate(), e.getDueTime(), null, null, e.getStatus()));
            }
            case "todo" -> {
                Todo e = todoMapper.selectById(id);
                if (e == null) return Optional.empty();
                return Optional.of(new StoredItem(e.getId(), "todo", e.getTitle(), null, null,
                        e.getContent(), null, e.getDueDate(), e.getDueTime(), null, null, e.getStatus()));
            }
            case "course" -> {
                Course e = courseMapper.selectById(id);
                if (e == null) return Optional.empty();
                return Optional.of(new StoredItem(e.getId(), "course", e.getTitle(), null, e.getTeacher(),
                        null, e.getLocation(), null, null, e.getStartTime(), e.getEndTime(), "active"));
            }
            case "event" -> {
                Event e = eventMapper.selectById(id);
                if (e == null) return Optional.empty();
                return Optional.of(new StoredItem(e.getId(), "event", e.getTitle(), null, null,
                        e.getContent(), e.getLocation(), e.getDueDate(), e.getDueTime(), null, null, e.getStatus()));
            }
            default -> throw new IllegalArgumentException("未知类型: " + type);
        }
    }

    public long insertOverrideByTitle(String courseTitle, LocalDate date, String kind,
                                      String newStart, String newEnd, String newLocation,
                                      String note, long sourceMessageId) {
        Course course = courseMapper.selectOne(new QueryWrapper<Course>().eq("title", courseTitle).last("LIMIT 1"));
        CourseOverride ov = new CourseOverride();
        ov.setCourseId(course == null ? null : course.getId());
        ov.setCourseTitle(courseTitle);
        ov.setOverrideDate(date.toString());
        ov.setKind(kind);
        ov.setNewStartTime(newStart);
        ov.setNewEndTime(newEnd);
        ov.setNewLocation(newLocation);
        ov.setNote(note);
        ov.setSourceMessageId(sourceMessageId);
        courseOverrideMapper.insert(ov);
        return ov.getId();
    }

    public List<CourseOccurrence> coursesOn(LocalDate date) {
        int weekday = date.getDayOfWeek().getValue();
        Map<Long, CourseOccurrence> byId = new LinkedHashMap<>();
        List<Course> courses = courseMapper.selectList(
                new QueryWrapper<Course>().eq("weekday", weekday).orderByAsc("start_time"));
        for (Course c : courses) {
            byId.put(c.getId(), new CourseOccurrence(c.getId(), c.getTitle(), c.getTeacher(), c.getLocation(),
                    c.getStartTime(), c.getEndTime(), null));
        }
        List<CourseOverride> overrides = courseOverrideMapper.selectList(
                new QueryWrapper<CourseOverride>().eq("override_date", date.toString()));
        for (CourseOverride ov : overrides) {
            if (ov.getCourseId() == null) continue;
            CourseOccurrence base = byId.get(ov.getCourseId());
            if (base == null) continue;
            if ("cancel".equals(ov.getKind())) {
                byId.remove(ov.getCourseId());
            } else {
                byId.put(ov.getCourseId(), new CourseOccurrence(base.courseId(), base.title(), base.teacher(),
                        pick(ov.getNewLocation(), base.location()),
                        pick(ov.getNewStartTime(), base.startTime()),
                        pick(ov.getNewEndTime(), base.endTime()),
                        ov.getNote()));
            }
        }
        return List.copyOf(byId.values());
    }

    private String pick(String newVal, String oldVal) {
        return (newVal != null && !newVal.isBlank()) ? newVal : oldVal;
    }

    public long insertMessage(String role, String content) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
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
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
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
}
