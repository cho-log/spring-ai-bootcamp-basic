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
        String documentsText = documents.stream()
            .map(document -> "[[%s]] %s".formatted(document.getId(), document.getText()))
            .collect(Collectors.joining("\n\n"));

        String result = rerankClient.prompt("""
            # 개요
            당신은 문서들을 재정렬하는 rerank 전문가입니다.
            주어진 질문을 확인하고, 각 문서의 내용이 질문에 답하기 위해 얼마나 필요한 핵심 정보인지를 판단하세요.
            그리고 우선순위를 기준으로 내림차순 정렬하세요.
            
            문서 간에 내용이 상반될 경우, 더 구체적이고 세부적인 정보를 담은 문서를 우선순위로 판단하세요.
            관련성이 동일한 경우 원래 순서를 유지하세요.
            
            # 질문
            %s
            
            # 문서들 (형식 : [[문서 id]] 문서내용)
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
