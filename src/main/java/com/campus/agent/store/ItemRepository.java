package com.campus.agent.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campus.agent.model.ExtractedItem;
import com.campus.agent.model.ExtractedProfile;
import com.campus.agent.store.entity.*;
import com.campus.agent.store.mapper.*;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
    private final SemesterMapper semesterMapper;
    private final InternshipMapper internshipMapper;
    private final ProfileMapper profileMapper;
    private final StudyProgressMapper studyProgressMapper;
    private final DataSource dataSource;

    public ItemRepository(AssignmentMapper assignmentMapper, ExamMapper examMapper, TodoMapper todoMapper,
                          CourseMapper courseMapper, EventMapper eventMapper,
                          CourseOverrideMapper courseOverrideMapper, SemesterMapper semesterMapper,
                          InternshipMapper internshipMapper, ProfileMapper profileMapper, StudyProgressMapper studyProgressMapper,
                          DataSource dataSource) {
        this.assignmentMapper = assignmentMapper;
        this.examMapper = examMapper;
        this.todoMapper = todoMapper;
        this.courseMapper = courseMapper;
        this.eventMapper = eventMapper;
        this.courseOverrideMapper = courseOverrideMapper;
        this.semesterMapper = semesterMapper;
        this.internshipMapper = internshipMapper;
        this.profileMapper = profileMapper;
        this.studyProgressMapper = studyProgressMapper;
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

    /** 某类型记录中 due_date 落在 [from, to] 区间的列表（按 due_date, due_time 排序）。 */
    public List<StoredItem> dueBetween(String type, LocalDate from, LocalDate to) {
        return switch (type) {
            case "assignment" -> assignmentMapper.selectList(new LambdaQueryWrapper<Assignment>()
                            .ge(Assignment::getDueDate, from.toString())
                            .le(Assignment::getDueDate, to.toString())
                            .orderByAsc(Assignment::getDueDate, Assignment::getDueTime))
                    .stream().map(this::toStored).toList();
            case "exam" -> examMapper.selectList(new LambdaQueryWrapper<Exam>()
                            .ge(Exam::getDueDate, from.toString())
                            .le(Exam::getDueDate, to.toString())
                            .orderByAsc(Exam::getDueDate, Exam::getDueTime))
                    .stream().map(this::toStored).toList();
            case "todo" -> todoMapper.selectList(new LambdaQueryWrapper<Todo>()
                            .ge(Todo::getDueDate, from.toString())
                            .le(Todo::getDueDate, to.toString())
                            .orderByAsc(Todo::getDueDate, Todo::getDueTime))
                    .stream().map(this::toStored).toList();
            default -> throw new IllegalArgumentException("不支持的区间查询类型: " + type);
        };
    }

    private StoredItem toStored(Assignment e) {
        return new StoredItem(e.getId(), "assignment", e.getTitle(), e.getCourse(), e.getTeacher(),
                e.getContent(), null, e.getDueDate(), e.getDueTime(), null, null, e.getStatus());
    }

    private StoredItem toStored(Exam e) {
        return new StoredItem(e.getId(), "exam", e.getTitle(), e.getCourse(), e.getTeacher(),
                e.getContent(), e.getLocation(), e.getDueDate(), e.getDueTime(), null, null, e.getStatus());
    }

    private StoredItem toStored(Todo e) {
        return new StoredItem(e.getId(), "todo", e.getTitle(), null, null,
                e.getContent(), null, e.getDueDate(), e.getDueTime(), null, null, e.getStatus());
    }


    /** 按类型+id 删除业务记录；返回是否真的删掉了一行。 */
    public boolean deleteById(String type, long id) {
        return switch (type) {
            case "assignment" -> assignmentMapper.deleteById(id) == 1;
            case "exam" -> examMapper.deleteById(id) == 1;
            case "todo" -> todoMapper.deleteById(id) == 1;
            case "course" -> courseMapper.deleteById(id) == 1;
            case "event" -> eventMapper.deleteById(id) == 1;
            default -> throw new IllegalArgumentException("未知类型: " + type);
        };
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
        Map<Long, String> courseWeeks = new LinkedHashMap<>();
        List<Course> courses = courseMapper.selectList(
                new QueryWrapper<Course>().eq("weekday", weekday).orderByAsc("start_time"));
        for (Course c : courses) {
            byId.put(c.getId(), new CourseOccurrence(c.getId(), c.getTitle(), c.getTeacher(), c.getLocation(),
                    c.getStartTime(), c.getEndTime(), null));
            courseWeeks.put(c.getId(), c.getWeeks());
        }

        Optional<Integer> weekOpt = weekOf(date);
        if (weekOpt.isPresent() && weekOpt.get() >= 1) {
            Optional<SemesterInfo> sem = semester();
            if (sem.isPresent() && weekOpt.get() > sem.get().totalWeeks()) {
                return List.of();
            }
            Map<Long, CourseOccurrence> filtered = new LinkedHashMap<>();
            for (Map.Entry<Long, CourseOccurrence> e : byId.entrySet()) {
                if (WeekRanges.contains(courseWeeks.get(e.getKey()), weekOpt.get())) {
                    filtered.put(e.getKey(), e.getValue());
                }
            }
            byId = filtered;
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

    /** 设置学期（单行表 id=1，有则覆盖）。 */
    public void setSemester(LocalDate start, int totalWeeks) {
        semesterMapper.deleteById(1L);
        Semester s = new Semester();
        s.setId(1L);
        s.setStartDate(start.toString());
        s.setTotalWeeks(totalWeeks);
        semesterMapper.insert(s);
    }

    public Optional<SemesterInfo> semester() {
        Semester s = semesterMapper.selectById(1L);
        if (s == null) return Optional.empty();
        return Optional.of(new SemesterInfo(LocalDate.parse(s.getStartDate()), s.getTotalWeeks()));
    }

    /** 今天是本学期第几周：-1=未设置学期；0=未开学；1..totalWeeks=学期内；>totalWeeks=假期。 */
    public Optional<Integer> weekOf(LocalDate today) {
        Optional<SemesterInfo> s = semester();
        if (s.isEmpty()) return Optional.of(-1);
        long days = ChronoUnit.DAYS.between(s.get().start(), today);
        if (days < 0) return Optional.of(0);
        return Optional.of((int) (days / 7) + 1);
    }

    /** 仅测试用：清空学期设置。 */
    public void clearSemester() {
        semesterMapper.deleteById(1L);
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

    //internship表增删改查
    public long insertInternship(String company, String position, String city, String salary,
                                 String deadline, String jd, String link, long sourceMessageId){
        Internship i = new Internship();
        i.setCompany(company);
        i.setPosition(position);
        i.setCity(city);
        i.setSalary(salary);
        i.setDeadline(deadline);
        i.setJd(jd);
        i.setLink(link);
        i.setSourceMessageId(sourceMessageId);
        internshipMapper.insert(i);
        return i.getId();
    }

    public List<InternshipItem> allInternships(){
        List<InternshipItem> list = new ArrayList<>();
        for(Internship i : internshipMapper.selectList(null)){
            list.add(new InternshipItem(i.getId(),i.getCompany(),i.getPosition(),i.getCity(),
                    i.getSalary(),i.getDeadline(),i.getJd(),i.getLink(),i.getStatus()));
        }
        return list;
    }

    public Optional<InternshipItem> findInternship(long id){
        Internship i = internshipMapper.selectById(id);
        if(i == null) return Optional.empty();
        return Optional.of(new InternshipItem(i.getId(),i.getCompany(),i.getPosition(),i.getCity(),
                i.getSalary(),i.getDeadline(),i.getJd(),i.getLink(),i.getStatus()));
    }

    public boolean deleteInternship(long id){
        return internshipMapper.deleteById(id) == 1;
    }

    //profile表读写
    public void setProfile(ExtractedProfile p){
        profileMapper.deleteById(1L);
        Profile profile = new Profile();
        profile.setId(1L);
        profile.setSkills(p.skills());
        profile.setTargetRole(p.targetRole());
        profile.setTargetCity(p.targetCity());
        profile.setGrade(p.grade());
        profile.setNote(p.note());
        profileMapper.insert(profile);
    }

    public Optional<ProfileItem> profile(){
        Profile p = profileMapper.selectById(1L);
        if(p == null) return Optional.empty();
        return Optional.of(new ProfileItem(p.getId(),p.getSkills(),p.getTargetRole(),
                p.getTargetCity(),p.getGrade(),p.getNote()));
    }

    /** 仅测试用：档案表行数（单行表应始终为 1 或 0）。 */
    public long profileCount(){
        return profileMapper.selectCount(null);
    }

    //studyProgress表读写
    //写一条打卡
    public long insertStudyProgress(LocalDate studyDate,String content,long sourceMessageId){
        StudyProgress s = new StudyProgress();
        s.setStudyDate(studyDate.toString());
        s.setContent(content);
        s.setSourceMessageId(sourceMessageId);
        studyProgressMapper.insert(s);
        return s.getId();
    }

    /** 查 [from, to] 区间内的打卡记录（含边界），按日期升序。 */
    public List<StudyProgressItem> studyProgressBetween(LocalDate from, LocalDate to){
        return studyProgressMapper.selectList(new LambdaQueryWrapper<StudyProgress>()
                        .ge(StudyProgress::getStudyDate, from.toString())
                        .le(StudyProgress::getStudyDate, to.toString())
                        .orderByAsc(StudyProgress::getStudyDate))
                .stream().map(s -> new StudyProgressItem(s.getId(),
                        LocalDate.parse(s.getStudyDate()), s.getContent()))
                .toList();
    }
}
