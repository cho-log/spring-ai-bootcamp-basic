package com.cholog.bootcamp.chatbot.application;

import com.cholog.bootcamp.chatbot.application.dto.ChatbotResult;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatbotService {

    private static final String PROMPT = """
            [참고 문서]
            %s
            
            [고객 질문]
            %s
            """;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public ChatbotResult chat(String question) {
        String context = searchRelevantDocuments(question);
        String userMessage = PROMPT.formatted(context, question);

        ChatResponse aiResponse = chatClient.prompt()
                .user(userMessage)
                .call()
                .chatResponse();
        return ChatbotResult.of(aiResponse);
    }

    private String searchRelevantDocuments(String question) {
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(5)
                        .build()
        );

        return docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));
    }

}
