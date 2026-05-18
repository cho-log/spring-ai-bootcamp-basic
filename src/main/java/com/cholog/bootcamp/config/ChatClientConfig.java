package com.cholog.bootcamp.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(
        ChatClient.Builder builder,
        VectorStore vectorStore,
        ChatMemory chatMemory
    ) {
        // 프롬프트
        PromptTemplate customPromptTemplate = getPromptTemplate();

        // 어드바이저
        QuestionAnswerAdvisor qaAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
            .promptTemplate(customPromptTemplate)
            .searchRequest(SearchRequest.builder().topK(6).build())
            .build();
        MessageChatMemoryAdvisor messageChatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        SimpleLoggerAdvisor simpleLoggerAdvisor = new SimpleLoggerAdvisor();

        return builder.defaultAdvisors(qaAdvisor, messageChatMemoryAdvisor, simpleLoggerAdvisor).build();


    }

    private PromptTemplate getPromptTemplate() {
        return PromptTemplate.builder()
            .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
            .template("""
                당신은 초록 고객센터의 챗봇입니다.
                다음 주어진 내용을 참고해 [사용자의 질문]에 답변해주세요.
                
                답변 규칙
                - 만약 주어진 내용으로 답변할 수 없다면 모르겠다고 안내하세요.
                - 내용이 충돌하는 경우 다음 우선순위를 따라 답변 합니다.
                    - 질문 도메인과 가장 근접한 내용
                    - 더 구체적인 상황을 다루는 내용
                    - 더 최신 버전의 내용
                             
                ---
                
                [관련 내용]
                <question_answer_context>
                
                ---
                
                [사용자 질문]
                <query>
            """)
            .build();
    }
}
