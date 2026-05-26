package com.cholog.bootcamp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
public class VectorStoreConfig {

    @Bean
    public VectorStore vectorStore(EmbeddingModel model,
                                   @Value("classpath:layer1_faq/*.md") Resource[] faqResources,
                                   @Value("classpath:layer2_policies/current/*.md") Resource[] policyResources,
                                   @Value("classpath:layer3_examples/*.md") Resource[] exampleResources) {
        var store = SimpleVectorStore.builder(model).build();

        var documents = new ArrayList<Document>();
        documents.addAll(toMarkdownDocuments(faqResources, "faq"));
        documents.addAll(toMarkdownDocuments(policyResources, "policy"));
        documents.addAll(toMarkdownDocuments(exampleResources, "example"));

        documents.forEach(document -> {
            if (document.getText() != null) {
                log.info("document ID: {}, file: {}, metadata: {}, TEXT: {}",
                        document.getId(),
                        document.getMetadata().get("source"),
                        document.getMetadata(),
                        document.getText().substring(0, Math.min(80, document.getText().length())).replace("\n", " "));
            }
        });

        store.add(documents);
        log.info("vector store 적재 완료. markdown 문서 {}", documents.size());
        return store;
    }

    private List<Document> toMarkdownDocuments(Resource[] resources, String layer) {
        var documents = new ArrayList<Document>();

        for (Resource resource : resources) {
            var config = MarkdownDocumentReaderConfig.builder()
                    .withIncludeCodeBlock(false)
                    .withIncludeBlockquote(false)
                    .withHorizontalRuleCreateDocument(true)
                    .withAdditionalMetadata(Map.of(
                            "source", resource.getFilename(),
                            "layer", layer
                    ))
                    .build();

            documents.addAll(new MarkdownDocumentReader(resource, config).get());
        }

        return documents;
    }
}
