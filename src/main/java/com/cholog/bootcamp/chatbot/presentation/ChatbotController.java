package com.cholog.bootcamp.chatbot.presentation;

import com.cholog.bootcamp.chatbot.application.ChatbotService;
import com.cholog.bootcamp.chatbot.application.dto.ChatbotResult;
import com.cholog.bootcamp.chatbot.presentation.dto.ChatbotRequest;
import com.cholog.bootcamp.chatbot.presentation.dto.ChatbotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;

    @PostMapping
    public ResponseEntity<ChatbotResponse> chat(@RequestBody ChatbotRequest request) {
        ChatbotResult chatbotResult = chatbotService.chat(request.question());
        return ResponseEntity.ok().body(ChatbotResponse.of(chatbotResult));
    }

}
