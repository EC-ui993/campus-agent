package com.campus.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeepSeekClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void buildBodyContainsExpectedFields() throws Exception {
        String body = DeepSeekClient.buildBody("deepseek-chat", "你是助手", "你好", "json_object");
        JsonNode n = MAPPER.readTree(body);
        assertEquals("deepseek-chat", n.path("model").asText());
        assertEquals("json_object", n.path("response_format").path("type").asText());
        assertEquals(2, n.path("messages").size());
        assertEquals("你是助手", n.path("messages").path(0).path("content").asText());
    }

    @Test
    void buildBodyOmitsResponseFormatWhenNull() throws Exception {
        JsonNode n = MAPPER.readTree(DeepSeekClient.buildBody("deepseek-chat", "s", "u", null));
        assertTrue(n.path("response_format").isMissingNode());
    }

    @Test
    void parseContentExtractsText() {
        String body = "{\"choices\":[{\"message\":{\"content\":\"你好，同学\"}}]}";
        assertEquals("你好，同学", DeepSeekClient.parseContent(body));
    }

    @Test
    void parseContentThrowsOnMissingContent() {
        assertThrows(LlmException.class, () -> DeepSeekClient.parseContent("{}"));
        assertThrows(LlmException.class, () -> DeepSeekClient.parseContent("不是JSON"));
    }
}
