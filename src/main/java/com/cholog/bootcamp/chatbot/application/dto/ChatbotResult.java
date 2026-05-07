package com.cholog.bootcamp.chatbot.application.dto;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

public record ChatbotResult(
        String answer,
        long promptTokens,
        long completionTokens,
        long totalTokens
) {

    public static ChatbotResult of(ChatResponse response) {
        Usage usage = response.getMetadata().getUsage();
        return new ChatbotResult(
                response.getResult().getOutput().getText(),
                usage.getPromptTokens(),
                usage.getCompletionTokens(),
                usage.getTotalTokens()
        );
    }
}
