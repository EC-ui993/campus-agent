package com.campus.agent.store;

import com.campus.agent.model.ExtractedItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CourseScheduleTest {

    // 2026-08-24 是周一
    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 24);

    private static ExtractedItem gaoshu() {
        return new ExtractedItem("course", "高数", null, "王老师", null,
                "A201", null, null, "08:00", "09:40", 1, null);
    }

    @Test
    void weeklyCourseAppearsOnItsWeekday(@TempDir Path tmp) throws Exception {
        try (Database db = new Database(tmp.resolve("s.db"))) {
            ItemRepository repo = new ItemRepository(db);
            repo.insert(gaoshu(), 1);
            List<CourseOccurrence> mondayCourses = repo.coursesOn(MONDAY);
            assertEquals(1, mondayCourses.size());
            assertEquals("高数", mondayCourses.get(0).title());
            assertEquals("08:00", mondayCourses.get(0).startTime());
            assertTrue(repo.coursesOn(MONDAY.plusDays(1)).isEmpty(), "周二不该有高数");
        }
    }

    @Test
    void cancelOverrideAffectsOnlyThatDate(@TempDir Path tmp) throws Exception {
        try (Database db = new Database(tmp.resolve("s2.db"))) {
            ItemRepository repo = new ItemRepository(db);
            repo.insert(gaoshu(), 1);
            // 下周一停课
            repo.insertOverrideByTitle("高数", MONDAY.plusDays(7), "cancel", null, null, null, null, 2);
            assertEquals(1, repo.coursesOn(MONDAY).size(), "本周一照常");
            assertTrue(repo.coursesOn(MONDAY.plusDays(7)).isEmpty(), "下周一被取消");
            assertEquals(1, repo.coursesOn(MONDAY.plusDays(14)).size(), "再下周自动恢复");
        }
    }

    @Test
    void moveOverrideReplacesTimeAndLocation(@TempDir Path tmp) throws Exception {
        try (Database db = new Database(tmp.resolve("s3.db"))) {
            ItemRepository repo = new ItemRepository(db);
            repo.insert(gaoshu(), 1);
            repo.insertOverrideByTitle("高数", MONDAY, "move", "10:00", "11:40", "B302", null, 2);
            CourseOccurrence c = repo.coursesOn(MONDAY).get(0);
            assertEquals("10:00", c.startTime());
            assertEquals("11:40", c.endTime());
            assertEquals("B302", c.location());
        }
    }

    @Test
    void overrideTitleUnmatchedIsIgnored(@TempDir Path tmp) throws Exception {
        try (Database db = new Database(tmp.resolve("s4.db"))) {
            ItemRepository repo = new ItemRepository(db);
            repo.insert(gaoshu(), 1);
            // 课程名写错 → 找不到 course_id，变动被忽略（不报错）
            repo.insertOverrideByTitle("高树", MONDAY, "cancel", null, null, null, null, 2);
            assertEquals(1, repo.coursesOn(MONDAY).size());
        }
    }
}
