package com.campus.agent.store;

import com.campus.agent.model.ExtractedItem;
import com.campus.agent.store.mapper.AssignmentMapper;
import com.campus.agent.store.mapper.CourseMapper;
import com.campus.agent.store.mapper.CourseOverrideMapper;
import com.campus.agent.store.mapper.EventMapper;
import com.campus.agent.store.mapper.ExamMapper;
import com.campus.agent.store.mapper.SemesterMapper;
import com.campus.agent.store.mapper.TodoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ItemRepositoryTest {

    private static Path dbPath;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws Exception {
        dbPath = Files.createTempFile("agent-repo-test", ".db");
        Files.deleteIfExists(dbPath);
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dbPath);
    }

    @Autowired
    ItemRepository repo;

    @Autowired AssignmentMapper assignmentMapper;
    @Autowired ExamMapper examMapper;
    @Autowired TodoMapper todoMapper;
    @Autowired CourseMapper courseMapper;
    @Autowired EventMapper eventMapper;
    @Autowired CourseOverrideMapper courseOverrideMapper;
    @Autowired SemesterMapper semesterMapper;

    @BeforeEach
    void cleanDb() {
        semesterMapper.delete(null);
        courseOverrideMapper.delete(null);
        assignmentMapper.delete(null);
        examMapper.delete(null);
        todoMapper.delete(null);
        courseMapper.delete(null);
        eventMapper.delete(null);
    }

    @Test
    void insertAndListRoundTrip() throws Exception {
        long id = repo.insert(new ExtractedItem(
                "assignment", "习题5.2", "高数", "王老师", "完成5.2全部", null, "2026-11-20", "23:59", null, null, null, null), 1);
        assertTrue(id > 0);

        List<StoredItem> all = repo.allItems();
        assertEquals(1, all.size());
        StoredItem s = all.get(0);
        assertEquals("assignment", s.type());
        assertEquals("习题5.2", s.title());
        assertEquals("2026-11-20", s.dueDate());
        assertEquals("pending", s.status());
    }

    @Test
    void updateByIdMergesFields() throws Exception {
        long id = repo.insert(new ExtractedItem(
                "todo", "领材料", null, null, "辅导员办公室", null, "2026-11-21", "10:00", null, null, null, null), 1);

        boolean updated = repo.updateById(new ExtractedItem(
                "todo", "领材料", null, null, "改去教务处", null, "2026-11-22", "10:00", null, null, null, null), id);
        assertTrue(updated);

        Optional<StoredItem> found = repo.getById("todo", id);
        assertTrue(found.isPresent());
        assertEquals("2026-11-22", found.get().dueDate());
    }

    @Test
    void insertMessageAndRawInbox() throws Exception {
        long mid = repo.insertMessage("user", "老师通知原文");
        assertTrue(mid > 0);

        long rid = repo.insertRaw("解析失败的原文", "title 不能为空");
        assertTrue(rid > 0);

        assertEquals(0, repo.allItems().size());
    }

    @Test
    void allItemsCoversEveryType() throws Exception {
        repo.insert(new ExtractedItem("assignment", "作业1", null, null, null, null, null, null, null, null, null, null), 1);
        repo.insert(new ExtractedItem("exam", "考试1", null, null, null, "D105", "2026-12-01", "19:00", null, null, null, null), 1);
        repo.insert(new ExtractedItem("todo", "待办1", null, null, null, null, null, null, null, null, null, null), 1);
        repo.insert(new ExtractedItem("course", "数据结构", "张老师", "B302", null, null, null, null, "10:00", "11:40", 1, null), 1);
        repo.insert(new ExtractedItem("event", "组会", null, null, "实验室", null, "2026-11-27", "16:00", null, null, null, null), 1);
        assertEquals(5, repo.allItems().size());
    }

    // 课程表：周重复 + 临时变动（原 CourseScheduleTest 场景）

    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 24);

    private static ExtractedItem gaoshu() {
        return new ExtractedItem("course", "高数", null, "王老师", null,
                "A201", null, null, "08:00", "09:40", 1, null);
    }

    @Test
    void weeklyCourseAppearsOnItsWeekday() throws Exception {
        repo.insert(gaoshu(), 1);
        List<CourseOccurrence> mondayCourses = repo.coursesOn(MONDAY);
        assertEquals(1, mondayCourses.size());
        assertEquals("高数", mondayCourses.get(0).title());
        assertEquals("08:00", mondayCourses.get(0).startTime());
        assertTrue(repo.coursesOn(MONDAY.plusDays(1)).isEmpty(), "周二不该有高数");
    }

    @Test
    void cancelOverrideAffectsOnlyThatDate() throws Exception {
        repo.insert(gaoshu(), 1);
        repo.insertOverrideByTitle("高数", MONDAY.plusDays(7), "cancel", null, null, null, null, 2);
        assertEquals(1, repo.coursesOn(MONDAY).size(), "本周一照常");
        assertTrue(repo.coursesOn(MONDAY.plusDays(7)).isEmpty(), "下周一被取消");
        assertEquals(1, repo.coursesOn(MONDAY.plusDays(14)).size(), "再下周自动恢复");
    }

    @Test
    void moveOverrideReplacesTimeAndLocation() throws Exception {
        repo.insert(gaoshu(), 1);
        repo.insertOverrideByTitle("高数", MONDAY, "move", "10:00", "11:40", "B302", null, 2);
        CourseOccurrence c = repo.coursesOn(MONDAY).get(0);
        assertEquals("10:00", c.startTime());
        assertEquals("11:40", c.endTime());
        assertEquals("B302", c.location());
    }

    @Test
    void overrideTitleUnmatchedIsIgnored() throws Exception {
        repo.insert(gaoshu(), 1);
        repo.insertOverrideByTitle("高树", MONDAY, "cancel", null, null, null, null, 2);
        assertEquals(1, repo.coursesOn(MONDAY).size());
    }

    @Test
    void weeksFilterByCurrentWeek() throws Exception {
        repo.setSemester(LocalDate.of(2026, 8, 31), 16);
        repo.insert(new ExtractedItem("course", "高数", null, null, null,
                "A201", null, null, "08:00", "09:40", 1, "1-8"), 1);
        repo.insert(new ExtractedItem("course", "英语", null, null, null,
                "B101", null, null, "10:00", "11:40", 1, "10-16"), 2);
        repo.insert(new ExtractedItem("course", "体育", null, null, null,
                "操场", null, null, "14:00", "15:40", 1, null), 3);

        List<CourseOccurrence> w1 = repo.coursesOn(LocalDate.of(2026, 8, 31));
        assertEquals(2, w1.size());

        List<CourseOccurrence> w10 = repo.coursesOn(LocalDate.of(2026, 11, 2));
        assertEquals(2, w10.size());
        assertEquals("英语", w10.get(0).title());

        assertTrue(repo.coursesOn(LocalDate.of(2026, 12, 21)).isEmpty());
    }

    @Test
    void noSemesterMeansNoWeeksFilter() throws Exception {
        repo.insert(new ExtractedItem("course", "高数", null, null, null,
                "A201", null, null, "08:00", "09:40", 1, "1-8"), 1);
        assertEquals(1, repo.coursesOn(LocalDate.of(2026, 8, 31)).size(), "未设置学期不过滤");
    }

    @Test
    void deleteByIdRemovesRow() {
        long id = repo.insert(new ExtractedItem("todo", "领材料", null, null, null, null, null, null, null, null, null, null), 1);
        assertTrue(repo.deleteById("todo", id));
        assertTrue(repo.getById("todo", id).isEmpty());
        assertFalse(repo.deleteById("todo", id), "再删一次应返回 false");
    }


}
