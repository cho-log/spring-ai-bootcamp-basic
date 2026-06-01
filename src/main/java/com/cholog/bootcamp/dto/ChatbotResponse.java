package com.cholog.bootcamp.dto;

import java.util.List;

import org.springframework.ai.chat.metadata.Usage;

public record ChatbotResponse(
    String answer,
    TokenUsageInfo tokenUsage,
    List<String> contexts
) {

    public static ChatbotResponse from(String answer, Usage usage, List<String> contexts) {
        return new ChatbotResponse(
            answer,
            new TokenUsageInfo(
                usage.getPromptTokens(),
                usage.getCompletionTokens(),
                usage.getTotalTokens()
            ),
            contexts
        );
    }

    private record TokenUsageInfo(
        int promptTokens,
        int completionTokens,
        int totalTokens
    ) {
    }
}
