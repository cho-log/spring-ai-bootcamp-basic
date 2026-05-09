package com.cholog.bootcamp;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/chat")
@RestController
public class ChatbotController {

    private final ChatClient chatClient;

    public ChatbotController(ChatClient.Builder builder) {
        String prompt = "다음은 답변에 참고할 내용입니다: {data} \\n\\n 질문: {question}\n";
        this.chatClient = builder.defaultUser(prompt).build();
    }

    @PostMapping
    public String chat(@RequestBody String question) {
        Map<String, Object> promptParams = Map.of("data", "주문 금액과 상관없이 Priority 배송 무료다.", "question", question);
        return chatClient.prompt()
            .user(userSpec -> userSpec.params(promptParams))
            .call()
            .content();
    }
}
