package com.cholog.bootcamp.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.stereotype.Component;

@Component
public class RerankDocumentPostProcessor implements DocumentPostProcessor {

    private final ChatClient rerankClient;

    public RerankDocumentPostProcessor(ChatClient.Builder builder) {
        this.rerankClient = builder.build();
    }

    @Override
    public List<Document> process(Query query, List<Document> documents) {
        System.out.println("========");
        for (Document document : documents) {
            System.out.println(document.getMetadata().get("filename") + " " + document.getText());
        }
        System.out.println("========");
        String documentsText = documents.stream()
            .map(document -> """
                    [[%s]]
                    source: %s
                    title: %s
                    text:
                    %s
                    """
                .formatted(
                document.getId(),
                document.getMetadata().get("filename"),
                document.getMetadata().get("title"),
                document.getText()
            ))
            .collect(Collectors.joining("\n\n"));

        String result = rerankClient.prompt("""
            # 개요
            당신은 문서들을 재정렬하는 rerank 전문가입니다.
            주어진 질문을 확인하고, 각 문서의 내용이 질문에 답하기 위해 얼마나 필요한 핵심 정보인지를 판단하세요.
            그리고 우선순위를 기준으로 내림차순 정렬하세요.(우선순위가 높은게 앞에 오게)
            
            # **주의사항**
            - 문서 간 내용이 상반되거나 충돌할 경우, 더 구체적이고 특수한 조건(예외, 제한사항 등)을 다루는 문서를 일반적인 내용보다 반드시 우선시하세요.
            일반 규칙보다 예외 규칙이 항상 높은 우선순위를 가집니다.
            - 문서 id를 임의로 누락하지 마세요. 당신은 재정렬만 수행합니다.
            
            # 질문
            %s
            
            # 문서들 [[문서 id]]
            %s
            
            # 출력 형식
            문서 id를 내림차순으로 `, `로 구분하여 출력하세요.
            문서 id와 구분자 외에 다른 문자는 절대 출력하지 않습니다.
            
            예시) id4, id2, id3, id1
        """.formatted(query.text(), documentsText)).call()
        .chatResponse()
            .getResult()
            .getOutput()
            .getText();

        System.out.println("========");
        System.out.println(result);
        System.out.println("========");

        Map<String, Document> documentMap = documents.stream()
            .collect(Collectors.toMap(Document::getId, Function.identity()));

        return Arrays.stream(result.split(", "))
            .map(documentMap::get)
            .toList();
    }
}
