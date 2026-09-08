package com.campus.agent.web;

import com.campus.agent.llm.LlmClient;
import com.campus.agent.store.ItemRepository;
import com.campus.agent.testing.FakeLlm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerTest {

    private static Path dbPath;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws Exception {
        dbPath = Files.createTempFile("agent-web-test", ".db");
        Files.deleteIfExists(dbPath); // SQLite 会重新创建
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dbPath);
    }

    @TestConfiguration
    static class FakeLlmConfig {
        @Bean
        @Primary
        LlmClient fakeLlm() {
            return new FakeLlm()
                    .json("{\"intent\":\"record\"}")
                    .json("""
                            {"type":"assignment","title":"习题5.2","course":"高数","teacher":"",
                             "content":"","location":"","dueDate":"2026-11-20","dueTime":"23:59"}
                            """);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ItemRepository repo;

    @Test
    void recordMessageReturnsAck() throws Exception {
        mvc.perform(post("/api/chat")
                        .header("X-Access-Token", "xinside")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"高数作业习题5.2，11月20日前交\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.startsWith("✅")));
    }

    @Test
    void internshipsReturnsList() throws Exception{
        repo.insertInternship("字节跳动", "Java后端实习生", "北京", "200-300/天",
                "2026-09-15", "要求熟悉Spring Boot", "https://example.com/jd/1", 1);
        mvc.perform(get("/api/internships")
                .header("X-Access-Token","xinside"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].company").value("字节跳动"))
                .andExpect(jsonPath("$[0].position").value("Java后端实习生"))
                .andExpect(jsonPath("$[0].seq").value(1));
    }
}
