package com.campus.agent.web;

import com.campus.agent.service.AssistantService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final AssistantService service;

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
}
