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

@Service
public class ChatService {

    private final ChatClient chatClient;

    public ChatService(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    public QuestionAskResponse askQuestion(QuestionAskRequest request) {
        String faq = getFAQ();

        ChatResponse chatResponse = chatClient.prompt()
            .system("""
                당신은 초록 코퍼레이션에서 고객지원 챗봇을 담당하는 역할입니다.
                제공된 문서를 참고하여 고객에게 답변을 해주세요.
                모든 응답은 한국어로 해야하며, 초록 코퍼레이션과 무관한 내용은 다루지 마세요.
                
                %s
                """.formatted(faq))
            .user(request.question())
            .call()
            .chatResponse();
        Usage usage = chatResponse.getMetadata().getUsage();

        return QuestionAskResponse.from(
            chatResponse.getResults().get(0).getOutput().getText(),
            usage.getPromptTokens(),
            usage.getCompletionTokens(),
            usage.getTotalTokens()
        );
    }

    private String getFAQ() {
        StringBuilder sb = new StringBuilder();

        File file = new File("data/layer1_faq");
        File[] files = file.listFiles();

        for (File fs : files) {
            sb.append(file.getName()).append('\n');
            try (BufferedReader br = new BufferedReader(new FileReader(fs))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return sb.toString();
    }
}
