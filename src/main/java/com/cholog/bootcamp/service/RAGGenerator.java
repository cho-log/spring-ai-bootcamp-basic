package com.cholog.bootcamp.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class RAGGenerator {

    private static final String SYSTEM_PROMPT = """
        당신은 제공된 [컨텍스트]만을 기반으로 [사용자 질문]에 답변하는 신뢰할 수 있는 초록 고객센터의 챗봇입니다.
        
        아래의 규칙을 엄격하게 준수하십시오.
        - 반드시 모든 답변은 [컨텍스트]에 있는 정보를 기반으로 생성하세요. 절대 [컨텍스트]에 없는 정보를 추론하거나 사실인 것처럼 대답하지 마세요.
        - [컨텍스트]에 참고할 정보가 없어 [사용자 질문]에 정확한 답변을 할 수 없는 경우에만 "죄송합니다, 제공된 문서에서 고객님이 찾으시는 정보를 찾지 못했어요. 더 자세한 확인이 필요하시다면 상담원에게 문의해주세요."라고 답합니다.
        - 내용이 충돌하는 경우 다음 우선순위를 따라 답변 합니다.
            - 질문 도메인과 가장 근접한 내용
            - 더 구체적인 상황을 다루는 내용
            - 더 최신 버전의 내용
        """;
    private final ChatClient chatClient;

    public RAGGenerator(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public ChatResponse generate(List<Document> documents, String question) {
        return chatClient.prompt()
            .system(SYSTEM_PROMPT)
            .user("""
                [사용자 질문]
                %s
                
                [컨텍스트]
                %s
            """.formatted(question, getContext(documents)))
            .call()
            .chatResponse();
    }

    private String getContext(List<Document> documents) {
        return documents.stream()
            .map(Document::getText)
            .collect(Collectors.joining("\n\n"));
    }
}
