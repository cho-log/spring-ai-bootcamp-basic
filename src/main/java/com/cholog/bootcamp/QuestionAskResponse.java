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
        public static InnerTokenUsageResponse from(
            Integer promptTokens, Integer completionTokens, Integer totalTokens
        ) {
            return new InnerTokenUsageResponse(promptTokens, completionTokens, totalTokens);
        }
    }

    public static QuestionAskResponse from(
        String answer, Integer promptTokens, Integer completionTokens, Integer totalTokens
    ) {
        return new QuestionAskResponse(
            answer,
            InnerTokenUsageResponse.from(promptTokens, completionTokens, totalTokens)
        );
    }
}
