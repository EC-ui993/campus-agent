package com.campus.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromptsTest {

    @Test
    void extractPromptInjectsToday() {
        String prompt = Prompts.extract();
        assertTrue(prompt.contains("yyyy-MM-dd"), "抽取提示词应说明日期格式");
        assertTrue(prompt.matches("(?s).*\\d{4}-\\d{2}-\\d{2}.*"), "应注入今天日期: " + prompt);
    }
}
