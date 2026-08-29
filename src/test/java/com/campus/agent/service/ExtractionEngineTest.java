package com.campus.agent.service;

import com.campus.agent.store.ItemRepository;
import com.campus.agent.store.mapper.AssignmentMapper;
import com.campus.agent.store.mapper.CourseMapper;
import com.campus.agent.store.mapper.CourseOverrideMapper;
import com.campus.agent.store.mapper.EventMapper;
import com.campus.agent.store.mapper.ExamMapper;
import com.campus.agent.store.mapper.SemesterMapper;
import com.campus.agent.store.mapper.TodoMapper;
import com.campus.agent.testing.FakeLlm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ExtractionEngineTest {

    private static Path dbPath;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws Exception {
        dbPath = Files.createTempFile("extraction-engine-test", ".db");
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

    private ExtractionEngine engine(FakeLlm llm) {
        return new ExtractionEngine(llm, repo);
    }

    @Test
    void validExtractionStoresAndOk() {
        FakeLlm llm = new FakeLlm().json("""
                {"type":"assignment","title":"习题5.2","course":"高数","teacher":"",
                 "content":"","location":"","dueDate":"2026-11-20","dueTime":"23:59"}
                """);
        ExtractionEngine.Outcome o = engine(llm).extractAndStore("高数作业", 1);
        assertTrue(o.ok());
        assertEquals(1, repo.allItems().size());
    }

    @Test
    void twoBadJsonGoesToRawInboxAndFail() {
        FakeLlm llm = new FakeLlm().json("不是JSON").json("不是JSON");
        ExtractionEngine.Outcome o = engine(llm).extractAndStore("坏消息", 1);
        assertFalse(o.ok());
        assertTrue(repo.allItems().isEmpty());
    }

    @Test
    void semesterSettingBranchWritesSemester() {
        FakeLlm llm = new FakeLlm().json("""
                {"type":"semester_setting","startDate":"2026-09-01","totalWeeks":16,"note":""}
                """);
        ExtractionEngine.Outcome o = engine(llm).extractAndStore("设置学期", 1);
        assertTrue(o.ok());
        assertTrue(repo.semester().isPresent());
        assertEquals(16, repo.semester().orElseThrow().totalWeeks());
    }

    @Test
    void courseOverrideBranchWritesOverride() {
        FakeLlm llm = new FakeLlm().json("""
                {"type":"course_override","courseTitle":"高数","overrideDate":"2026-09-01","kind":"cancel",
                 "newStartTime":"","newEndTime":"","newLocation":"","note":""}
                """);
        ExtractionEngine.Outcome o = engine(llm).extractAndStore("高数停课", 1);
        assertTrue(o.ok());
    }
}
