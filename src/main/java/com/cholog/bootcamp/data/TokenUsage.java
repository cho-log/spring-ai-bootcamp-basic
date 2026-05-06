package com.cholog.bootcamp.data;

public record TokenUsage(
        int promptTokens,
        int completionTokens,
        int totalTokens
) {
}