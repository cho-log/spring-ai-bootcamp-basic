package com.cholog.bootcamp.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    private static final String CONVERSATION_ID_COOKIE = "conversationId";
    private final ChatbotService chatbotService;

    @PostMapping("/conversation")
    public ResponseEntity<ChatbotResponse> startConversation(
        @CookieValue(value = CONVERSATION_ID_COOKIE, required = false) String conversationId
    ) {
        if (conversationId != null) {
            chatbotService.clearConversation(conversationId);
        }
        ResponseCookie responseCookie = createConversationCookie(chatbotService.createConversationId());
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, responseCookie.toString())
            .build();
    }

    @DeleteMapping("/conversation")
    public ResponseEntity<ChatbotResponse> endConversation(
        @CookieValue(value = CONVERSATION_ID_COOKIE, required = false) String conversationId
    ) {
        if (conversationId != null) {
            chatbotService.clearConversation(conversationId);
        }
        ResponseCookie deleteCookie = expireConversationCookie();
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
            .build();
    }

    @PostMapping
    public ResponseEntity<ChatbotResponse> chat(
        @CookieValue(value = CONVERSATION_ID_COOKIE, required = false) String conversationId,
        @RequestBody ChatbotRequest request
    ) {
        if (conversationId == null) {
            conversationId = chatbotService.createConversationId();
        }
        ChatbotResponse response = chatbotService.chat(conversationId, request);
        ResponseCookie responseCookie = createConversationCookie(conversationId);
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, responseCookie.toString())
            .body(response);
    }

    @PostMapping("/debug")
    public ChatbotResponse debugChat(@RequestBody ChatbotRequest request) {
        return chatbotService.debugChat(request);
    }

    private ResponseCookie createConversationCookie(String conversationId) {
        return ResponseCookie.from(CONVERSATION_ID_COOKIE, conversationId)
            .httpOnly(true)
            .secure(false)
            .path("/")
            .sameSite("Lax")
            .build();
    }

    private ResponseCookie expireConversationCookie() {
        return ResponseCookie.from(CONVERSATION_ID_COOKIE, "")
            .httpOnly(true)
            .secure(false)
            .path("/")
            .sameSite("Lax")
            .maxAge(0)
            .build();
    }
}
