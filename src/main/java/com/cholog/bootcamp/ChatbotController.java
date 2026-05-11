package com.cholog.bootcamp;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequestMapping("/api/chat")
@RestController
public class ChatbotController {

    private final ChatClient chatClient;

    public ChatbotController(ChatClient.Builder builder, EmbeddingModel embeddingModel, MarkdownReader markdownReader) {
        VectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        vectorStore.add(markdownReader.loadAll());
        QuestionAnswerAdvisor questionAnswerAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
            .searchRequest(SearchRequest.builder().topK(8).build())
            .build();
        this.chatClient = builder
            .defaultAdvisors(questionAnswerAdvisor, new SimpleLoggerAdvisor())
            .build();
    }

    @PostMapping
    public Map<String, String> chat(@RequestBody ChatRequest request) {
        String answer = chatClient.prompt()
            .user(request.question())
            .call()
            .content();
        return Map.of("answer", answer);
    }

    @PostMapping("/debug")
    public Map<String, String> debugChat(@RequestBody ChatRequest request) {
        Object RETRIEVED_DOCUMENTS = chatClient.prompt()
            .user(request.question())
            .call()
            .chatClientResponse()
            .context()
            .get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        log.info("RETRIEVED_DOCUMENTS: {}", RETRIEVED_DOCUMENTS);
        return Map.of("answer", "answer");
    }
}
