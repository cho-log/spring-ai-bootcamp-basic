package com.cholog.bootcamp.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
public class VectorStoreConfig {

    @Bean
    public VectorStore vectorStore(EmbeddingModel model,
                                   @Value("classpath:prompts/faq-system.st") Resource systemTemplateResource,
                                   @Value("classpath:layer1_faq/*.md") Resource[] faqResources,
                                   @Value("classpath:layer2_policies/current/*.md") Resource[] policyResources,
                                   @Value("classpath:layer3_examples/*.md") Resource[] exampleResources) {
        var store = SimpleVectorStore.builder(model).build();

        var documents = new ArrayList<Document>();
        documents.addAll(toDocuments(faqResources, "faq"));
        documents.addAll(toDocuments(policyResources, "policy"));
        documents.addAll(toDocuments(exampleResources, "example"));

        var chunks = new TokenTextSplitter().apply(documents);

        store.add(chunks);
        log.info("vector store 적재 완료. 원본 {} -> 청크 {}", documents.size(), chunks.size());
        return store;
    }

    private List<Document> toDocuments(Resource[] resources, String layer) {
        return Arrays.stream(resources)
                .map(r -> {
                    try {
                        return new Document(
                                r.getContentAsString(StandardCharsets.UTF_8),
                                Map.of("source", r.getFilename(), "layer", layer));
                    } catch (IOException e) {
                        throw new IllegalStateException("리소스 로드 실패: " + r.getFilename(), e);
                    }
                })
                .toList();
    }
}
