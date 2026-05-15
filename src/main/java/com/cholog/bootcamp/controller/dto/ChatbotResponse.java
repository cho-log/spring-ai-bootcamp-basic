package com.cholog.bootcamp.controller.dto;

import org.springframework.ai.chat.metadata.Usage;

public record ChatbotResponse(
    String answer,
    TokenUsageInfo tokenUsage
) {

    public static ChatbotResponse from(String answer, Usage usage) {
        return new ChatbotResponse(
            answer,
            new TokenUsageInfo(
                usage.getPromptTokens(),
                usage.getCompletionTokens(),
                usage.getTotalTokens()
            )
        );
    }

    private record TokenUsageInfo(
        int promptTokens,
        int completionTokens,
        int totalTokens
    ) {
    }
}
