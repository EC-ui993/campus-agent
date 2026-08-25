package com.campus.agent.web;

import com.campus.agent.llm.LlmClient;
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
        registry.add("app.db-path", () -> dbPath.toString());
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

    @Autowired
    MockMvc mvc;

    @Test
    void recordMessageReturnsAck() throws Exception {
        mvc.perform(post("/api/chat")
                        .header("X-Access-Token", "change-me-please")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"高数作业习题5.2，11月20日前交\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.startsWith("✅")));
    }
}
