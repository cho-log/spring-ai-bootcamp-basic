package com.cholog.bootcamp.chat.dto;

public record ChatAnswerResponse(
    String answer,
    TokenUsage tokenUsage
) {

    public record TokenUsage(
        int promptTokens,
        int completionTokens,
        int totalTokens
    ) {
    }
}
