package com.cholog.bootcamp.service;

import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import com.cholog.bootcamp.dto.ChatbotRequest;
import com.cholog.bootcamp.dto.ChatbotResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ChatbotService {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    public ChatbotService(
        VectorStore vectorStore,
        MarkdownReader markdownReader,
        ChatMemory chatMemory,
        ChatClient chatClient
    ) {
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
        vectorStore.add(markdownReader.loadAll());
    }

    public ChatbotResponse chat(String conversationId, ChatbotRequest request) {
        ChatResponse chatResponse = chatClient.prompt()
            .user(request.question())
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
            .call()
            .chatResponse();

        String answer = chatResponse.getResult().getOutput().getText();
        Usage usage = chatResponse.getMetadata().getUsage();
        return ChatbotResponse.from(answer, usage);
    }

    public ChatbotResponse debugChat(ChatbotRequest request) {
        ChatClientResponse chatClientResponse = chatClient.prompt()
            .user(request.question())
            .call()
            .chatClientResponse();

        Object RETRIEVED_DOCUMENTS = chatClientResponse.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        log.info("RETRIEVED_DOCUMENTS: {}", RETRIEVED_DOCUMENTS);

        ChatResponse chatResponse = chatClientResponse.chatResponse();
        String answer = chatResponse.getResult().getOutput().getText();
        Usage usage = chatResponse.getMetadata().getUsage();
        return ChatbotResponse.from(answer, usage);
    }

    public String createConversationId() {
        return UUID.randomUUID().toString();
    }

    public void clearConversation(String conversationId) {
        if (conversationId == null) {
            throw new NullPointerException("conversationId는 null일 수 없습니다.");
        }
        chatMemory.clear(conversationId);
    }
}
