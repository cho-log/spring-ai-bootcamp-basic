package com.cholog.bootcamp.controller;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cholog.bootcamp.dto.ChatbotRequest;
import com.cholog.bootcamp.dto.ChatbotResponse;
import com.cholog.bootcamp.service.ChatbotService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/chat")
@RestController
public class ChatbotController {

    private final ChatbotService chatbotService;

    @PostMapping
    public ResponseEntity<ChatbotResponse> chat(
        @CookieValue(required = false) String conversationId,
        @RequestBody ChatbotRequest request
    ) {
        if (conversationId == null) {
            conversationId = UUID.randomUUID().toString();
        }
        ChatbotResponse response = chatbotService.chat(conversationId, request);
        ResponseCookie responseCookie = ResponseCookie.from("conversationId", conversationId)
            .httpOnly(true)
            .secure(true)
            .build();
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, responseCookie.toString())
            .body(response);
    }

    @PostMapping("/debug")
    public ChatbotResponse debugChat(@RequestBody ChatbotRequest request) {
        return chatbotService.debugChat(request);
    }
}
