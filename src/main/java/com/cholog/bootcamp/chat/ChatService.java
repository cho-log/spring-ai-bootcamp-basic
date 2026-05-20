package com.cholog.bootcamp.chat;

import com.cholog.bootcamp.chat.dto.ChatResponse;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final RagProperties ragProperties;
    private final DocumentLoader documentLoader;

    @PostConstruct
    void loadFaqContext() {
        vectorStore.add(documentLoader.load());
    }

    public ChatResponse ask(String question) {
        List<Document> retrievedDocuments = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(question)
                .topK(ragProperties.getTopK())
                .build()
        );

        logSearchResults(question, retrievedDocuments);

        String supportContext = retrievedDocuments
            .stream()
            .map(Document::getText)
            .collect(Collectors.joining("\n\n===\n\n"));

        org.springframework.ai.chat.model.ChatResponse response = chatClient.prompt()
            .system("""
                    - 당신은 Cholog Corporation의 고객 전용 챗봇 서비스이다.
                    - 제공된 컨텍스트만을 활용하라.
                    - 제공된 컨텍스트로 답할 수 없다면, '고객센터에 문의해주세요'라고 답하라.
                    - 한국어로 답하라.
                """)
            .user("""
                    Customer question:
                    %s
                
                    Support context:
                    %s
                """.formatted(question, supportContext))
            .call()
            .chatResponse();

        Usage usage = response.getMetadata().getUsage();

        return new ChatResponse(
            response.getResult().getOutput().getText(),
            new ChatResponse.TokenUsage(
                usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens(),
                usage == null || usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens(),
                usage == null || usage.getTotalTokens() == null ? 0 : usage.getTotalTokens()
            )
        );
    }

    private void logSearchResults(String question, List<Document> documents) {
        String resultSummary = documents.isEmpty()
            ? "no documents retrieved"
            : documents.stream()
                .map(this::formatDocumentSummary)
                .collect(Collectors.joining(" | "));

        log.info("RAG search question='{}' topK={} results={}",
            question,
            ragProperties.getTopK(),
            resultSummary
        );
    }

    private String formatDocumentSummary(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        String sourceType = String.valueOf(metadata.getOrDefault("sourceType", "UNKNOWN"));
        String source = String.valueOf(metadata.getOrDefault("source", "UNKNOWN"));
        Object sectionTitle = metadata.get("sectionTitle");

        if (sectionTitle == null) {
            return "%s/%s".formatted(sourceType, source);
        }

        return "%s/%s#%s".formatted(sourceType, source, sectionTitle);
    }
}
