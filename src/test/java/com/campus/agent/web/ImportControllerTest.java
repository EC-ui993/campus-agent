package com.campus.agent.web;

import com.alibaba.excel.EasyExcel;
import com.campus.agent.llm.LlmClient;
import com.campus.agent.testing.FakeLlm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ImportControllerTest {

    private static Path dbPath;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws Exception {
        dbPath = Files.createTempFile("import-test", ".db");
        Files.deleteIfExists(dbPath);
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dbPath);
    }

    @TestConfiguration
    static class FakeLlmConfig {
        @Bean
        @Primary
        LlmClient fakeLlm() {
            FakeLlm f = new FakeLlm();
            f.json("""
                    {"type":"course","title":"高数","course":"","teacher":"","content":"",
                     "location":"A201","dueDate":"","dueTime":"","startTime":"08:00","endTime":"09:40",
                     "weekday":1,"weeks":"1-16"}
                    """);
            f.json("""
                    {"type":"course","title":"英语","course":"","teacher":"","content":"",
                     "location":"B101","dueDate":"","dueTime":"","startTime":"10:00","endTime":"11:40",
                     "weekday":3,"weeks":"1-16"}
                    """);
            return f;
        }
    }

    @Autowired
    MockMvc mvc;

    @Test
    void importExcelInsertsCoursesAndReportsCounts() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out).sheet("课表").doWrite(List.of(
                List.of("课程名", "星期", "开始", "结束", "教室", "周次"),
                List.of("高数", "周一", "08:00", "09:40", "A201", "1-16"),
                List.of("英语", "周三", "10:00", "11:40", "B101", "1-16")));
        MockMultipartFile file = new MockMultipartFile("file", "schedule.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());

        mvc.perform(multipart("/api/import/excel").file(file)
                        .header("X-Access-Token", "xinside"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.ok").value(2))
                .andExpect(jsonPath("$.failed").value(0));
    }
}
