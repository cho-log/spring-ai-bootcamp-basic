package com.cholog.bootcamp;

public record QuestionAskResponse(
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
