package com.cholog.bootcamp.dto;

import java.util.List;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;

public record ChatbotResponse(
    String answer,
    TokenUsageInfo tokenUsage,
    List<String> contexts
) {

    public static ChatbotResponse from(ChatResponse chatResponse, List<Document> documents) {
        String answer = chatResponse.getResult().getOutput().getText();
        Usage usage = chatResponse.getMetadata().getUsage();
        List<String> contexts = documents.stream()
            .map(Document::getText)
            .toList();

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
