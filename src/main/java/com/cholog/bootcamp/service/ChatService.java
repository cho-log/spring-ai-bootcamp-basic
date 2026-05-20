package com.cholog.bootcamp.service;

import java.util.List;
import java.util.stream.Collectors;

import javax.print.Doc;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import com.cholog.bootcamp.dto.QuestionAskRequest;
import com.cholog.bootcamp.dto.QuestionAskResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int FAQ_TOP_K = 4;
    private static final int CURRENT_POLICY_TOP_K = 3;
    private static final int INTERNAL_POLICY_TOP_K = 2;
    private static final int CHAT_LOG_TOP_K = 2;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public QuestionAskResponse askQuestion(QuestionAskRequest request) {
        String context = getContext(request.question());

        ChatResponse chatResponse = chatClient.prompt()
            .system("""
                당신은 초록 코퍼레이션에서 고객지원을 담당하고 있습니다.
                제공된 참고자료를 바탕으로 질문에 대한 답변을 진행해주세요.
                
                답변 간 유의 사항은 다음과 같습니다.
                - 초록 코퍼레이션과 무관한 내용은 답변하지 마세요.
                - 참고자료에 없는 내용을 추측해서 답변하지 마세요.
                """)
            .user("""
                참고 자료
                %s
                
                질문
                %s
                """.formatted(context, request.question()))
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

    private String getContext(String question) {
        return """
            FAQ
            %s

            Current Policies
            %s

            Internal Policies
            %s

            Chat Logs
            %s
            """.formatted(
            toContext(searchDocuments(question, FAQ_TOP_K, "'layer1_faq'")),
            toContext(searchDocuments(question, CURRENT_POLICY_TOP_K, "'layer2_policies'")),
            toContext(searchDocuments(question, INTERNAL_POLICY_TOP_K, "'layer2_internal")),
            toContext(searchDocuments(question, CHAT_LOG_TOP_K, "'layer3_chatlogs'"))
        );
    }

    private List<Document> searchDocuments(String question, int topK, String layer) {
        return search(question, topK, "layer == " + layer);
    }

    private List<Document> search(String question, int topK, String filterExpression) {
        SearchRequest searchRequest = SearchRequest.builder()
            .query(question)
            .topK(topK)
            .filterExpression(filterExpression)
            .build();

        return vectorStore.similaritySearch(searchRequest);
    }

    private String toContext(List<Document> documents) {
        return documents.stream()
            .map(Document::getText)
            .collect(Collectors.joining("\n\n"));
    }
}
