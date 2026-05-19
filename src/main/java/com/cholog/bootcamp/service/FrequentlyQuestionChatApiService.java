package com.cholog.bootcamp.service;

import com.cholog.bootcamp.data.TokenUsage;
import com.cholog.bootcamp.dto.FrequentlyQuestionChatRequestDto;
import com.cholog.bootcamp.dto.FrequentlyQuestionChatResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FrequentlyQuestionChatApiService {

    private final ChatClient chatClient;
    private final PricingCalculator pricingCalculator;
    private final VectorStore vectorStore;

    public FrequentlyQuestionChatResponseDto chat(FrequentlyQuestionChatRequestDto requestDto) {
        var prompt = Prompt.builder()
                .content(requestDto.question())
                .build();

        var response = chatClient.prompt(prompt)
                .call()
                .chatResponse();

        if (response == null) {
            return new FrequentlyQuestionChatResponseDto(
                    "응답이 없습니다.", TokenUsage.EMPTY
            );
        }

        var generation = response.getResult().getOutput();
        var metadata = response.getMetadata();

        var usage = TokenUsage.from(metadata.getUsage());
        var price = calculateModelPrice(metadata.getModel(), usage);

        log.info("[{}] 토큰 사용량: {}, 토큰 비용: {}$\n결과: {}", metadata.getModel(), usage, price, generation.getText());
        return new FrequentlyQuestionChatResponseDto(generation.getText(), usage);
    }

    public FrequentlyQuestionChatResponseDto chatWithRag(FrequentlyQuestionChatRequestDto requestDto) {

        String question = requestDto.question();

        var hits = vectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(8)
                .build());
        log.info("hits 결과: {}", hits.stream().map(Document::getId).toList());

        var context = hits.stream()
                .map(d -> "## " + d.getMetadata().get("source") + "\n" + d.getText())
                .collect(Collectors.joining("\n\n---\n\n"));

        var response = chatClient.prompt(context)
                .user(u -> u.text("""
                                참고 문서:
                                {context}
                                
                                질문: {question}
                                """)
                        .param("context", context)
                        .param("question", question))
                .call()
                .chatResponse();

        if (response == null) {
            return new FrequentlyQuestionChatResponseDto(
                    "응답이 없습니다.", TokenUsage.EMPTY
            );
        }

        var generation = response.getResult().getOutput();
        var metadata = response.getMetadata();

        var usage = TokenUsage.from(metadata.getUsage());
        var price = calculateModelPrice(metadata.getModel(), usage);

        log.info("[{}] 토큰 사용량: {}, 토큰 비용: {}$\n결과: {}", metadata.getModel(), usage, price, generation.getText());
        return new FrequentlyQuestionChatResponseDto(generation.getText(), usage);
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
