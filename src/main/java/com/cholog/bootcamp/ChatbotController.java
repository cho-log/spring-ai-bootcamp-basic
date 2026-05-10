package com.cholog.bootcamp;

import java.util.Map;

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

    public ChatbotController(ChatClient.Builder builder, EmbeddingModel embeddingModel, MarkdownReader markdownReader) {
        VectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        vectorStore.add(markdownReader.loadAll());
        this.chatClient = builder.defaultAdvisors(
            QuestionAnswerAdvisor.builder(vectorStore).build()
        ).build();
    }

    @PostMapping
    public Map<String, String> chat(@RequestBody ChatRequest request) {
        String answer = chatClient.prompt()
            .user(request.question())
            .call()
            .content();
        return Map.of("answer", answer);
    }
}
