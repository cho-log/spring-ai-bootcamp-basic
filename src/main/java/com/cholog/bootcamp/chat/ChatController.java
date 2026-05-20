package com.cholog.bootcamp.chat;

import com.cholog.bootcamp.chat.dto.ChatRequest;
import com.cholog.bootcamp.chat.dto.ChatAnswerResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/api/chat")
    public ChatAnswerResponse chat(@RequestBody ChatRequest request) {
        return chatService.ask(request.question());
    }
}
