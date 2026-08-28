package com.campus.agent.web;

import com.campus.agent.service.AssistantService;
import com.campus.agent.store.StoredItem;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/api")
public class ChatController {

    private final AssistantService service;
    private final java.util.concurrent.ExecutorService chatExecutor = java.util.concurrent.Executors.newFixedThreadPool(4);

    public ChatController(AssistantService service) {
        this.service = service;
    }

    public record ChatRequest(String message) {
    }

    public record ChatReply(String reply) {
    }

    @PostMapping("/chat")
    public ChatReply chat(@RequestBody ChatRequest req) {
        return new ChatReply(service.handle(req.message()));
    }

    @GetMapping("/items")
    public List<Map<String, Object>> items() {
        return service.recordsWithSeq();
    }


    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter chatStream(@RequestBody ChatRequest req) {
        SseEmitter emitter = new SseEmitter(120_000L);
        chatExecutor.submit(() -> {
            try {
                String full = service.handleStreaming(req.message(), delta -> {
                    try {
                        emitter.send(SseEmitter.event().data(delta));
                    } catch (Exception e) {
                        throw new RuntimeException("SSE 发送失败", e);
                    }
                });
                emitter.send(SseEmitter.event().name("done").data(full));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }
}
