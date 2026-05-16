package com.cholog.bootcamp;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public QuestionAskResponse askQuestion(QuestionAskRequest request) {
        SearchRequest searchRequest = SearchRequest.builder()
            .query(request.question())
            .build();
        List<Document> documents = vectorStore.similaritySearch(searchRequest);
        String context = documents.stream()
            .map(Document::getText)
            .collect(Collectors.joining("\n\n"));

        ChatResponse chatResponse = chatClient.prompt()
            .system("""
                당신은 초록 코퍼레이션에서 고객지원을 담당하고 있습니다.
                제공된 참고자료를 바탕으로 질문에 대한 답변을 진행해주세요.
                
                답변 간 유의 사항은 다음과 같습니다.
                - 초록 코퍼레이션과 무관한 내용은 답변하지 마세요.
                - 참고자료에 없는 내용을 추측해서 답변하지 마세요.
                - 단답식 대답은 지양하고, 제공된 참고자료에서 추가로 제공할 수 있는 정보가 최대한 많이 전달해주세요.
                    - Bad Answer : VIP가 될려면 100,000원을 사용해야 합니다.
                    - Good Answer : VIP가 될려면 100,000원을 사용해야 합니다. VIP가 되면 무료 배송의 혜택을 얻을 수 있습니다.
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
}
