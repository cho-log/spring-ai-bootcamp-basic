package com.cholog.bootcamp.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import com.cholog.bootcamp.MarkdownReader;
import com.cholog.bootcamp.dto.ChatbotRequest;
import com.cholog.bootcamp.dto.ChatbotResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ChatbotService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public ChatbotService(
        ChatClient.Builder builder,
        EmbeddingModel embeddingModel,
        MarkdownReader markdownReader,
        ChatMemory chatMemory
    ) {
        PromptTemplate customPromptTemplate = PromptTemplate.builder()
            .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
            .template("""
            <query>

            아래는 컨텍스트 정보입니다.
            
            ---------------------
            <question_answer_context>
            ---------------------
            
            컨텍스트 정보를 바탕으로 질문에 답하세요.

			답변 시 아래 룰을 따르세요:
			1. 절대 컨텍스트에 없는 내용을 추론하거나 지어내지 마세요. 응답은 컨텍스트 정보를 기반으로 확인되는 사실만 답합니다.
			2. 사용자가 질문한 부분에 대해서만 답하기 보다는 관련 정보 중 사용자에게 유용한 정보라고 판단되면 함께 답변에 포함해주세요.
			예시)
			Q. 적립 포인트 1점은 얼마의 가치인가요?
			- 추천하지 않는 답변: "적립 포인트 1점의 가치는 1원입니다."와 같이 단순 정보만 제공하고 끝나는 답변.
			- 추천하는 답변: 적립 포인트 모으는 방법, 적립 정책, 적립금 소멸, 적립금 가치 등 적립 포인트 관련 핵심 정보를 담은 간단명료한 답변.
            """)
            .build();

        VectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        vectorStore.add(markdownReader.loadAll());
        QuestionAnswerAdvisor qaAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
            .promptTemplate(customPromptTemplate)
            .searchRequest(SearchRequest.builder().topK(8).build())
            .build();
        this.chatClient = builder.defaultAdvisors(
            qaAdvisor,
            MessageChatMemoryAdvisor.builder(chatMemory).build(),
            new SimpleLoggerAdvisor()
        ).build();
        this.vectorStore = vectorStore;
    }

    public ChatbotResponse chat(String conversationId, ChatbotRequest request) {
        ChatResponse chatResponse = chatClient.prompt()
            .user(request.question())
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
            .call()
            .chatResponse();

        String answer = chatResponse.getResult().getOutput().getText();
        Usage usage = chatResponse.getMetadata().getUsage();
        return ChatbotResponse.from(answer, usage);
    }

    public ChatbotResponse debugChat(ChatbotRequest request) {
        ChatClientResponse chatClientResponse = chatClient.prompt()
            .user(request.question())
            .call()
            .chatClientResponse();

        Object RETRIEVED_DOCUMENTS = chatClientResponse.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        log.info("RETRIEVED_DOCUMENTS: {}", RETRIEVED_DOCUMENTS);

        ChatResponse chatResponse = chatClientResponse.chatResponse();
        String answer = chatResponse.getResult().getOutput().getText();
        Usage usage = chatResponse.getMetadata().getUsage();
        return ChatbotResponse.from(answer, usage);
    }
}
