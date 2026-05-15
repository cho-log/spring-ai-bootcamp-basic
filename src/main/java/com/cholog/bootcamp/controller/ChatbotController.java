package com.cholog.bootcamp.controller;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cholog.bootcamp.dto.ChatbotRequest;
import com.cholog.bootcamp.dto.ChatbotResponse;
import com.cholog.bootcamp.MarkdownReader;
import com.cholog.bootcamp.service.ChatbotService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/chat")
@RestController
public class ChatbotController {

    private final ChatbotService chatbotService;

    @PostMapping
    public ChatbotResponse chat(@RequestBody ChatbotRequest request) {
        return chatbotService.chat(request);
    }

    @PostMapping("/debug")
    public ChatbotResponse debugChat(@RequestBody ChatbotRequest request) {
        return chatbotService.debugChat(request);
    }
}
