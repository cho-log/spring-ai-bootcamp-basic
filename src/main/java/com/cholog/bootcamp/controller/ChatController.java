package com.cholog.bootcamp.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cholog.bootcamp.dto.QuestionAskRequest;
import com.cholog.bootcamp.dto.QuestionAskResponse;
import com.cholog.bootcamp.service.ChatService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<QuestionAskResponse> askQuestion(@RequestBody QuestionAskRequest request) {
        QuestionAskResponse response = chatService.askQuestion(request);
        return ResponseEntity.ok(response);
    }
}
