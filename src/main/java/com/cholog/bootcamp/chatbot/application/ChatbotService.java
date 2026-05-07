package com.cholog.bootcamp.chatbot.application;

import com.cholog.bootcamp.chatbot.application.dto.ChatbotResult;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatbotService {

    private final ChatClient chatClient;

    public ChatbotResult chat(String question) {
        ChatResponse aiResponse = chatClient.prompt()
                .user(question)
                .call()
                .chatResponse();

        return ChatbotResult.of(aiResponse);
    }

}
