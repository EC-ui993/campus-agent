package com.campus.agent.web;

import com.campus.agent.model.ExtractedItem;
import com.campus.agent.store.ItemRepository;
import com.campus.agent.store.mapper.AssignmentMapper;
import com.campus.agent.store.mapper.CourseMapper;
import com.campus.agent.store.mapper.CourseOverrideMapper;
import com.campus.agent.store.mapper.EventMapper;
import com.campus.agent.store.mapper.ExamMapper;
import com.campus.agent.store.mapper.ReportMapper;
import com.campus.agent.store.mapper.SemesterMapper;
import com.campus.agent.store.mapper.TodoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ReportControllerTest {

    private static Path dbPath;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws Exception {
        dbPath = Files.createTempFile("report-controller-test", ".db");
        Files.deleteIfExists(dbPath);
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dbPath);
    }

    @Autowired MockMvc mvc;
    @Autowired ItemRepository repo;
    @Autowired AssignmentMapper assignmentMapper;
    @Autowired ExamMapper examMapper;
    @Autowired TodoMapper todoMapper;
    @Autowired CourseMapper courseMapper;
    @Autowired EventMapper eventMapper;
    @Autowired CourseOverrideMapper courseOverrideMapper;
    @Autowired SemesterMapper semesterMapper;
    @Autowired ReportMapper reportMapper;

    @BeforeEach
    void cleanDb() {
        reportMapper.delete(null);
        semesterMapper.delete(null);
        courseOverrideMapper.delete(null);
        assignmentMapper.delete(null);
        examMapper.delete(null);
        todoMapper.delete(null);
        courseMapper.delete(null);
        eventMapper.delete(null);
    }

    @Test
    void todayReportContainsCourseAndIsIdempotent() throws Exception {
        int weekday = LocalDate.now().getDayOfWeek().getValue();
        repo.insert(new ExtractedItem("course", "高数", null, null, null, "A201", null, null,
                "08:00", "09:40", weekday, null), 1);

        mvc.perform(get("/api/report/today").header("X-Access-Token", "xinside"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString("高数")));

        mvc.perform(get("/api/report/today").header("X-Access-Token", "xinside"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString("高数")));

        assertEquals(1L, reportMapper.selectCount(null));
    }
}
