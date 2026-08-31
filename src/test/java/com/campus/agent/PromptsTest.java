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

    @Test
    void extractPromptDefinesTitleAndContentSemantics() {
        String p = Prompts.extract();
        assertTrue(p.contains("不超过 15 字"), "title 应限定短标题");
        assertTrue(p.contains("完整内容"), "content 应明确为完整内容");
    }

    @Test
    void correctPromptDefinesReferenceRules() {
        assertTrue(Prompts.CORRECT.contains("标题/名字改成"), "CORRECT 应定义指代规则");
        assertTrue(Prompts.CORRECT.contains("未明确指代"), "CORRECT 应定义默认规则");
    }
}


