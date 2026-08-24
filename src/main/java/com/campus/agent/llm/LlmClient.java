package com.campus.agent.llm;

/** LLM 客户端抽象。生产用 DeepSeekClient，测试用 FakeLlm。 */
public interface LlmClient {

    /** 普通对话，返回文本。 */
    String chat(String systemPrompt, String userPrompt);

    /** 要求模型严格输出 JSON（DeepSeek json_object 模式），返回 JSON 字符串。 */
    String chatJson(String systemPrompt, String userPrompt);

    void chatStream(String systemPrompt, String userPrompt,java.util.function.Consumer<String> onDelta);
}
