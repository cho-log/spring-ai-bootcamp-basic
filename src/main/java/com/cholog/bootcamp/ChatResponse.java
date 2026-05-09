package com.cholog.bootcamp;

public record ChatResponse(
    String answer,
    InnerTokenUsageResponse tokenUsage
) {
    public record InnerTokenUsageResponse(
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens
    ) {

    }
}
