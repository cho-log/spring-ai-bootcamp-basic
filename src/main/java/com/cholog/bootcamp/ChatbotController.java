package com.cholog.bootcamp;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/chat")
@RestController
public class ChatbotController {

    private final ChatClient chatClient;

    public ChatbotController(ChatClient.Builder builder, EmbeddingModel embeddingModel) {
        VectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        this.chatClient = builder.defaultAdvisors(
            QuestionAnswerAdvisor.builder(vectorStore).build()
        ).build();
    }

    @PostMapping
    public String chat(@RequestBody String question) {
        return chatClient.prompt()
            .user(question)
            .call()
            .content();
    }
}
