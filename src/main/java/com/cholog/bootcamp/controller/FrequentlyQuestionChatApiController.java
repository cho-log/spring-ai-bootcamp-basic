package com.cholog.bootcamp.controller;

import com.cholog.bootcamp.dto.FrequentlyQuestionChatRequestDto;
import com.cholog.bootcamp.dto.FrequentlyQuestionChatResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class FrequentlyQuestionChatApiController {

    private static final Logger log = LoggerFactory.getLogger(FrequentlyQuestionChatApiController.class);

    @PostMapping
    public ResponseEntity<FrequentlyQuestionChatResponseDto> question(
            @RequestBody FrequentlyQuestionChatRequestDto dto
    ) {
        log.info("FAQ 요청이 들어왔습니다. {}", dto.question());
        // TODO 서비스 레이어 및 응답 구현
        return ResponseEntity.ok(null);
    }
}
