package com.campus.agent.service;

import com.campus.agent.model.ExtractedItem;
import com.campus.agent.store.ItemRepository;
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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DailyReportServiceTest {

    private static Path dbPath;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws Exception {
        dbPath = Files.createTempFile("daily-report-test", ".db");
        Files.deleteIfExists(dbPath);
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dbPath);
    }

    @Autowired ItemRepository repo;
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
    void reportContainsTodayCoursesAndDueItems() {
        DailyReportService svc = new DailyReportService(repo);
        LocalDate today = LocalDate.of(2026, 8, 31); // 周一
        repo.setSemester(LocalDate.of(2026, 8, 31), 16);
        repo.insert(new ExtractedItem("course", "高数", null, null, null, "A201", null, null,
                "08:00", "09:40", 1, "1-16"), 1);
        repo.insert(new ExtractedItem("assignment", "习题5.2", "高数", null, null, null,
                today.toString(), "23:59", null, null, null, null), 2);
        repo.insert(new ExtractedItem("exam", "期中", "英语", null, null, "D105",
                today.plusDays(5).toString(), "09:00", null, null, null, null), 3);

        String report = svc.buildReport(today);
        assertTrue(report.contains("高数"), report);
        assertTrue(report.contains("08:00"), report);
        assertTrue(report.contains("习题5.2"), report);
        assertTrue(report.contains("期中"), report);
    }

    @Test
    void emptyDayStillBuilds() {
        DailyReportService svc = new DailyReportService(repo);
        String report = svc.buildReport(LocalDate.of(2026, 8, 31));
        assertTrue(report.contains("今日课程"));
        assertTrue(report.contains("（无）"), report);
    }
}
