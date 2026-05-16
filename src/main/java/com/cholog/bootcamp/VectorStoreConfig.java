package com.cholog.bootcamp;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class VectorStoreConfig {

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    public ApplicationRunner vectorStoreInitializer(
        FaqReader faqReader,
        CurrentPolicyReader currentPolicyReader,
        InternalPolicyReader internalPolicyReader,
        VectorStore vectorStore
    ) {
        return args -> {
            List<Document> documents = new ArrayList<>();
            documents.addAll(faqReader.read());
            documents.addAll(currentPolicyReader.read());
            documents.addAll(internalPolicyReader.read());

            vectorStore.add(documents);
            log.info("Loaded {} documents into VectorStore", documents.size());
        };
    }
}
