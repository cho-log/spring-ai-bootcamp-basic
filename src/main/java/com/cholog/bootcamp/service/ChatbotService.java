package com.cholog.bootcamp.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.stereotype.Service;

import com.cholog.bootcamp.dto.ChatbotRequest;
import com.cholog.bootcamp.dto.ChatbotResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ChatbotService {

    private final RAGRetriever ragRetriever;
    private final RAGGenerator ragGenerator;

    public ChatbotService(
        RAGRetriever ragRetriever,
        RAGGenerator ragGenerator
    ) {
        this.ragRetriever = ragRetriever;
        this.ragGenerator = ragGenerator;
    }

    public ChatbotResponse chat(ChatbotRequest request) {
        List<Document> documents = ragRetriever.retrieve(request.question(), 10);
        ChatResponse chatResponse = ragGenerator.generate(documents, request.question());
        return ChatbotResponse.from(chatResponse, documents);
    }
}
