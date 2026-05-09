package com.cholog.bootcamp;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
