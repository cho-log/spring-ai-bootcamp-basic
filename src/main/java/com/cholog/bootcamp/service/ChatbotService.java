package com.cholog.bootcamp.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import com.cholog.bootcamp.dto.ChatbotRequest;
import com.cholog.bootcamp.dto.ChatbotResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ChatbotService {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final VectorStore vectorStore;
    private final ChatClient.Builder chatClientBuilder;
    private final DocumentPostProcessor documentPostProcessor;
    private final ResourcePatternResolver resolver;

    public ChatbotService(
        VectorStore vectorStore,
        MarkdownReader markdownReader,
        ChatMemory chatMemory,
        ChatClient.Builder chatClientBuilder,
        DocumentPostProcessor documentPostProcessor
    ) {
        MessageChatMemoryAdvisor messageChatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        this.chatClient = chatClientBuilder
            // .defaultAdvisors(messageChatMemoryAdvisor)
            .build();
        this.chatMemory = chatMemory;
        this.chatClientBuilder = chatClientBuilder;
        this.documentPostProcessor = documentPostProcessor;
        this.vectorStore = vectorStore;
        vectorStore.add(markdownReader.loadAll());
        this.resolver = new PathMatchingResourcePatternResolver();
    }

    public ChatbotResponse chat(String conversationId, ChatbotRequest request) {
        SearchRequest searchRequest = getSearchRequest(request.question(), 4);
        List<Document> documents = vectorStore.similaritySearch(searchRequest);
        documents = documents.stream()
            .map(document -> document.getMetadata().get("filename").toString())
            .distinct()
            .map(filename -> {
                try {
                    return resolver.getResources("classpath:data/**/" + filename)[0];
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            })
            .map(TextReader::new)
            .flatMap(reader -> reader.get().stream())
            .toList();

        String context = getContext(documents);
        System.out.println(context);
        ChatResponse chatResponse = chatClient.prompt()
            .system("""
                당신은 초록 고객센터의 챗봇입니다.
                주어진 [컨텍스트]를 기반으로 [사용자 질문]에 답변해주세요.
                
                답변 규칙
                - 제공된 컨텍스트를 기반으로만 답변하세요. 절대 일반 상식으로 추론하지 마세요.
                - 만약 주어진 컨텍스트로 답변할 수 없다면 모르겠다고 안내하세요.
                - 내용이 충돌하는 경우 다음 우선순위를 따라 답변 합니다.
                    - 질문 도메인과 가장 근접한 내용
                    - 더 구체적인 상황을 다루는 내용
                    - 더 최신 버전의 내용
            """)
            .user("""
                [사용자 질문]
                %s
                
                [컨텍스트]
                %s
            """.formatted(request.question(), context))
            // .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
            .call()
            .chatResponse();

        String answer = chatResponse.getResult().getOutput().getText();
        Usage usage = chatResponse.getMetadata().getUsage();
        return ChatbotResponse.from(answer, usage);
    }

    private static String getContext(List<Document> documents) {
        String context = documents.stream()
            .map(Document::getText)
            .collect(Collectors.joining("\n\n"));
        return context;
    }

    private SearchRequest getSearchRequest(String query, int k) {
        return SearchRequest.builder()
            .query(query)
            .topK(k)
            .build();
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

    private List<Document> getDocumentsWithQueryExpansion(String question) {
        MultiQueryExpander queryExpander = MultiQueryExpander.builder()
            .chatClientBuilder(chatClientBuilder)
            .numberOfQueries(4)
            .build();
        List<Query> queries = queryExpander.expand(new Query(question));
        log.info("query expansion(4): ");
        for (Query query : queries) {
            log.info("query: {}", query.text());
        }
        List<Document> rawDocuments = queries.stream()
            .flatMap(query -> vectorStore.similaritySearch(getSearchRequest(query.text(), 4)).stream())
            .toList();
        Map<String, Document> bestScoreByText = rawDocuments.stream()
            .collect(Collectors.toMap(
                Document::getText,
                Function.identity(),
                (d1, d2) -> d1.getScore() >= d2.getScore() ? d1 : d2 // 충돌 시 높은 score 선택
            ));
        return new ArrayList<>(bestScoreByText.values());
    }
}
