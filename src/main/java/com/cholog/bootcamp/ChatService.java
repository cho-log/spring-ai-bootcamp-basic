package com.cholog.bootcamp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ChatService {

    private final ChatClient chatClient;

    public ChatService(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    public QuestionAskResponse askQuestion(QuestionAskRequest request) {
        ChatResponse chatResponse = chatClient.prompt()
            .system("""
                당신은 초록 코퍼레이션에서 고객지원 챗봇을 담당하는 역할입니다.
                질문으로 들어오는 내용과 관련된 문서를 참고하고, 제공된 정보만을 사용하여 최대한 자세하고 구체적으로 답변해주세요.
                요청이 한국어로 들어오면 영어로 생각하고 한국어로 응답해주시고, 초록 코퍼레이션과 무관한 내용은 다루지 마세요.
                """)
            .user(request.question())
            .call()
            .chatResponse();
        Usage usage = chatResponse.getMetadata().getUsage();

        log.info("""
            request : {}
            response : {}
            promptTokens : {} completionTokens : {}, totalTokens : {}
            """,
            request.question(),
            chatResponse.getResults().get(0).getOutput().getText(),
            usage.getPromptTokens(),
            usage.getCompletionTokens(),
            usage.getTotalTokens()
        );

        return QuestionAskResponse.from(
            chatResponse.getResult().getOutput().getText(),
            usage.getPromptTokens(),
            usage.getCompletionTokens(),
            usage.getTotalTokens()
        );
    }
}
