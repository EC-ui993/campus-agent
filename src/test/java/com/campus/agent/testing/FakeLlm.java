package com.campus.agent.testing;

import com.campus.agent.llm.LlmClient;

import java.util.ArrayDeque;
import java.util.Deque;

/** 按入队顺序吐出预设响应的假 LLM。json() 进 chatJson 队列，chat() 进 chat 队列。 */
public class FakeLlm implements LlmClient {

    private final Deque<String> jsonResponses = new ArrayDeque<>();
    private final Deque<String> chatResponses = new ArrayDeque<>();
    private final Deque<String> streamChunks = new ArrayDeque<>();
    public String lastUserPrompt;

    public FakeLlm streamChunks(String... chunks){
        for(String c : chunks) streamChunks.add(c);
        return this;
    }

    public FakeLlm json(String response) {
        jsonResponses.add(response);
        return this;
    }

    public FakeLlm chat(String response) {
        chatResponses.add(response);
        return this;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        lastUserPrompt = userPrompt;
        return chatResponses.isEmpty() ? "" : chatResponses.poll();
    }

    @Override
    public String chatJson(String systemPrompt, String userPrompt) {
        lastUserPrompt = userPrompt;
        return jsonResponses.isEmpty() ? "{}" : jsonResponses.poll();
    }

    @Override
    public void chatStream(String systemPrompt, String userPrompt,java.util.function.Consumer<String> onDelta) {
        while(!streamChunks.isEmpty()) onDelta.accept(streamChunks.poll());
    }
}
