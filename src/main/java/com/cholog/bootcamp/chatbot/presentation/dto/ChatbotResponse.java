package com.cholog.bootcamp.chatbot.presentation.dto;

import com.cholog.bootcamp.chatbot.application.dto.ChatbotResult;

public record ChatbotResponse(
        String answer,
        TokenUsage tokenUsage
) {

    public static ChatbotResponse of(ChatbotResult result) {
        return new ChatbotResponse(
                result.answer(),
                new TokenUsage(
                        result.promptTokens(),
                        result.completionTokens(),
                        result.totalTokens()
                )
        );
    }

    record TokenUsage(
            long promptTokens,
            long completionTokens,
            long totalTokens
    ) {
    }
}
