package com.cholog.bootcamp.service;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import com.cholog.bootcamp.dto.ChatbotRequest;
import com.cholog.bootcamp.dto.ChatbotResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ChatbotService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final ResourcePatternResolver resolver;

    public ChatbotService(
        VectorStore vectorStore,
        MarkdownReader markdownReader,
        ChatClient.Builder chatClientBuilder
    ) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        vectorStore.add(markdownReader.loadAll());
        this.resolver = new PathMatchingResourcePatternResolver();
    }

    public ChatbotResponse chat(ChatbotRequest request) {
        // 검색
        SearchRequest searchRequest = getSearchRequest(request.question(), 4);
        List<Document> documents = vectorStore.similaritySearch(searchRequest);

        // 증강 & 생성
        documents = getFullDocuments(documents);
        String context = getContext(documents);
        ChatResponse chatResponse = chatClient.prompt()
            .system("""
                당신은 초록 고객센터의 챗봇입니다.
                주어진 [컨텍스트]를 기반으로 [사용자 질문]에 답변해주세요.
                
                답변 규칙
                - 제공된 컨텍스트를 기반으로만 답변하세요. 절대 일반 상식으로 추론하지 마세요.
                - 만약 주어진 컨텍스트로 답변할 수 없다면 모르겠다고 안내하세요.
                - 내용이 충돌하는 경우 다음 우선순위를 따라 답변 합니다.
                    - 질문 도메인과 가장 근접한 내용
                    - 더 구체적인 상황을 다루는 내용
                    - 더 최신 버전의 내용
            """)
            .user("""
                [사용자 질문]
                %s
                
                [컨텍스트]
                %s
            """.formatted(request.question(), context))
            .call()
            .chatResponse();

        String answer = chatResponse.getResult().getOutput().getText();
        Usage usage = chatResponse.getMetadata().getUsage();
        return ChatbotResponse.from(answer, usage);
    }

    private List<Document> getFullDocuments(List<Document> documents) {
        return documents.stream()
            .map(document -> document.getMetadata().get("filename").toString())
            .distinct()
            .map(filename -> {
                try {
                    return resolver.getResources("classpath:data/**/" + filename)[0];
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            })
            .map(TextReader::new)
            .flatMap(reader -> reader.get().stream())
            .toList();
    }

    private static String getContext(List<Document> documents) {
        return documents.stream()
            .map(Document::getText)
            .collect(Collectors.joining("\n\n"));
    }

    private SearchRequest getSearchRequest(String query, int k) {
        return SearchRequest.builder()
            .query(query)
            .topK(k)
            .build();
    }
}
