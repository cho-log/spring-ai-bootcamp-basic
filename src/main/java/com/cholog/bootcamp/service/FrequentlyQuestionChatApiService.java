package com.cholog.bootcamp.service;

import com.cholog.bootcamp.data.TokenUsage;
import com.cholog.bootcamp.dto.FrequentlyQuestionChatRequestDto;
import com.cholog.bootcamp.dto.FrequentlyQuestionChatResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FrequentlyQuestionChatApiService {

    private final ChatClient chatClient;
    private final PricingCalculator pricingCalculator;
    private final VectorStore vectorStore;

    public FrequentlyQuestionChatResponseDto chatWithRag(FrequentlyQuestionChatRequestDto requestDto) {

        String question = requestDto.question();

        var hits = searchContext(question);
        try {
            var response = execute(question, hits);

            var generation = response.getResult().getOutput();
            var metadata = response.getMetadata();

            var usage = TokenUsage.from(metadata.getUsage());
            var price = calculateModelPrice(metadata.getModel(), usage);

            log.info("[{}] 토큰 사용량: {}, 토큰 비용: {}$\n결과: {}", metadata.getModel(), usage, price, generation.getText());
            return new FrequentlyQuestionChatResponseDto(generation.getText(), usage);
        } catch (Exception e) {
            log.warn("챗봇 응답 실패: {}", e.getMessage(), e);
            return new FrequentlyQuestionChatResponseDto(
                    "챗봇 응답 생성 중 오류 발생했습니다.",
                    TokenUsage.EMPTY
            );
        }
    }

    private List<Document> searchContext(String question) {
        var documents = new ArrayList<Document>();
        documents.addAll(searchByLayer(question, 3, "policy"));
        documents.addAll(searchByLayer(question, 4, "faq"));
        documents.addAll(searchByLayer(question, 1, "example"));

        log.info("hits 결과: {}",
                documents.stream()
                        .map(document -> "%s(title=%s, layer=%s, source=%s)".formatted(
                                document.getId(),
                                document.getMetadata().get("title"),
                                document.getMetadata().get("layer"),
                                document.getMetadata().get("source")
                        ))
                        .toList());

        log.info("layer 별 hits 결과: {}",
                documents.stream().collect(Collectors.groupingBy(
                        document -> document.getMetadata().get("layer"),
                        Collectors.counting()
                )));
        return documents;
    }

    private List<Document> searchByLayer(String question, int topK, String layer) {
        var filter = new FilterExpressionBuilder();

        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(topK)
                .filterExpression(filter.eq("layer", layer).build())
                .build());
    }

    private ChatResponse execute(String question, List<Document> documents) {
        var context = documents.stream()
                .map(d -> "## " + d.getMetadata().get("source") + "\n" + d.getText())
                .collect(Collectors.joining("\n\n---\n\n"));

        return chatClient.prompt()
                .user(u -> u.text("""
                                참고 문서:
                                {context}
                                
                                질문: {question}
                                """)
                        .param("context", context)
                        .param("question", question))
                .call()
                .chatResponse();
    }

    private BigDecimal calculateModelPrice(String model, TokenUsage usage) {
        try {
            return pricingCalculator.calculatePrice(model, usage);
        } catch (Exception e) {
            log.info("토큰 비용 계산에 실패했습니다. 모델: {}, 메시지: {}", model, e.getMessage(), e);
            return BigDecimal.ZERO;
        }
    }
}
