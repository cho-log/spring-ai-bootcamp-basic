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
        ChatClient.Builder chatClientBuilder
    ) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.resolver = new PathMatchingResourcePatternResolver();
    }

    public ChatbotResponse chat(ChatbotRequest request) {
        // 검색
        SearchRequest searchRequest = getSearchRequest(request.question(), 4);
        List<Document> documents = vectorStore.similaritySearch(searchRequest);

        // 증강 & 생성
        // documents = getFullDocuments(documents);
        String context = getContext(documents);
        ChatResponse chatResponse = chatClient.prompt()
            .system("""
                당신은 초록 고객센터의 챗봇입니다.
                [답변 규칙]을 준수하며 주어진 [컨텍스트]를 기반으로 [사용자 질문]에 답변해주세요.
                
                [답변 규칙]
                - 모든 답변에는 `내부 문서`나 `컨텍스트`를 언급하지 말아주세요.
                - 모든 답변은 제공된 컨텍스트를 기반으로만 하세요. 절대 일반 상식으로 추론하지 마세요.
                - [답변 불가 유형]의 질문일 경우 답변할 수 없음을 안내하고 [추가 문의 안내]를 그대로 출력하세요.
                - 내용이 충돌하는 경우 다음 우선순위를 따라 답변 합니다.
                    - 질문 도메인과 가장 근접한 내용
                    - 더 구체적인 상황을 다루는 내용
                    - 더 최신 버전의 내용
                
                [답변 불가 유형]
                - 컨텍스트에 관련 내용이 없는 질문
                - 개인 정보가 요구되는 질문
                
                [추가 문의 안내]
                ```
                📝 문의 접수
                앱/웹 고객센터 > 문의하기에서 문의를 남겨주세요.
                
                💬 상담원 연결
                실시간 채팅: 매일 08:00 ~ 22:00
                전화 상담: 평일 09:00 ~ 18:00
                    일반 고객센터: 1588-0000
                    VIP 전용 상담: 1588-0002
                ```
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
        return ChatbotResponse.from(answer, usage, getContextList(documents));
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

    private String getContext(List<Document> documents) {
        return documents.stream()
            .map(Document::getText)
            .collect(Collectors.joining("\n\n"));
    }

    private List<String> getContextList(List<Document> documents) {
        return documents.stream()
            .map(Document::getText)
            .toList();
    }

    private SearchRequest getSearchRequest(String query, int k) {
        return SearchRequest.builder()
            .query(query)
            .topK(k)
            .build();
    }
}
