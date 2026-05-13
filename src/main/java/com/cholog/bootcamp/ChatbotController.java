package com.cholog.bootcamp;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequestMapping("/api/chat")
@RestController
public class ChatbotController {

    private final ChatClient chatClient;

    public ChatbotController(ChatClient.Builder builder, EmbeddingModel embeddingModel, MarkdownReader markdownReader) {
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
        this.chatClient = builder
            .defaultAdvisors(qaAdvisor, new SimpleLoggerAdvisor())
            .build();
    }

    @PostMapping
    public Map<String, String> chat(@RequestBody ChatRequest request) {
        String answer = chatClient.prompt()
            .user(request.question())
            .call()
            .content();
        return Map.of("answer", answer);
    }

    @PostMapping("/debug")
    public Map<String, String> debugChat(@RequestBody ChatRequest request) {
        Object RETRIEVED_DOCUMENTS = chatClient.prompt()
            .user(request.question())
            .call()
            .chatClientResponse()
            .context()
            .get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        log.info("RETRIEVED_DOCUMENTS: {}", RETRIEVED_DOCUMENTS);
        return Map.of("answer", "answer");
    }
}
