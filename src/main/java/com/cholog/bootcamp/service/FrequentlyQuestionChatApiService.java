package com.cholog.bootcamp.service;

import com.cholog.bootcamp.data.TokenUsage;
import com.cholog.bootcamp.dto.FrequentlyQuestionChatRequestDto;
import com.cholog.bootcamp.dto.FrequentlyQuestionChatResponseDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FrequentlyQuestionChatApiService {

    private final ChatClient chatClient;

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
        Usage usage = metadata.getUsage();

        log.info("[{}] 결과: {}, 토큰 사용량: {}", metadata.getModel(), generation.getText(), usage);
        return new FrequentlyQuestionChatResponseDto(generation.getText(), TokenUsage.from(usage));
    }
}
