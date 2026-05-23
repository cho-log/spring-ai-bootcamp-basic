package com.cholog.bootcamp.chatbot.application;

import com.cholog.bootcamp.chatbot.application.dto.ChatbotResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotService {

    private static final int FAQ_TOP_K = 4;
    private static final int POLICY_TOP_K = 4;
    private static final int CHATLOG_TOP_K = 3;
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
        List<Document> docs = new java.util.ArrayList<>();
        docs.addAll(searchByLayer(question, "faq", FAQ_TOP_K));
        docs.addAll(searchByLayer(question, "policy", POLICY_TOP_K));
        docs.addAll(searchByLayer(question, "chatlog", CHATLOG_TOP_K));

        loggingSearchedDocs(question, docs);

        return docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));
    }

    private List<Document> searchByLayer(String question, String layer, int topK) {
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(topK)
                        .filterExpression("layer == '" + layer + "'")
                        .build()
        );
    }

    private static void loggingSearchedDocs(String question, List<Document> docs) {
        log.info("=== [RAG] 검색된 문서 ({}개) for: {} ===", docs.size(), question);
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            String preview = doc.getText().substring(0, Math.min(120, doc.getText().length())).replace("\n", " ");
            log.info("[{}] metadata={} | text={}", i + 1, doc.getMetadata(), preview);
        }
    }

}
