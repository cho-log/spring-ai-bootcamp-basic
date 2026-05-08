package com.cholog.bootcamp;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/chat")
@RestController
public class ChatbotController {

    private final ChatClient chatClient;

    public ChatbotController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @PostMapping
    public String chat(@RequestBody String userInput) {
        return chatClient.prompt()
            .user(userInput)
            .call()
            .content();
    }
}
