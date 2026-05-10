package com.cholog.bootcamp.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.ai.chat.metadata.Usage;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenUsage(
        int promptTokens,
        int completionTokens,
        int totalTokens
) {

    public static TokenUsage EMPTY = new TokenUsage(0, 0, 0);

    public static TokenUsage from(Usage usage) {
        return new TokenUsage(
                usage.getPromptTokens(),
                usage.getCompletionTokens(),
                usage.getTotalTokens()
        );
    }
}