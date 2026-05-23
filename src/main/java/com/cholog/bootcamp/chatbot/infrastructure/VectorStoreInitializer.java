package com.cholog.bootcamp.chatbot.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class VectorStoreInitializer implements ApplicationRunner {

    private final DocumentLoader documentLoader;
    private final VectorStore vectorStore;

    @Override
    public void run(ApplicationArguments args) {
        List<Document> faqDocs = documentLoader.loadFaq();
        List<Document> policyDocs = documentLoader.loadPolicies();
        List<Document> chatlogDocs = documentLoader.loadChatlogs();

        List<Document> all = new ArrayList<>();
        all.addAll(faqDocs);
        all.addAll(policyDocs);
        all.addAll(chatlogDocs);

        if (all.isEmpty()) {
            log.warn("적재할 문서 없음. data/ 폴더 확인 필요");
            return;
        }

        vectorStore.add(all);
        log.info("임베딩 완료: 총 {}개 (faq={}, policy={}, chatlog={})",
                all.size(), faqDocs.size(), policyDocs.size(), chatlogDocs.size());
    }
}
