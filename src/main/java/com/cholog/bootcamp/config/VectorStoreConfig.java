package com.cholog.bootcamp.config;

import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.cholog.bootcamp.service.MarkdownReader;

@Configuration
public class VectorStoreConfig {

    private static final String MODE_OPTION = "mode";
    private static final String EMBEDDING_MODE = "embedding";

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    /**
     * VectorStore 임베딩 runner
     * 애플리케이션 실행 시 --mode=embedding 옵션이 존재하면 임베딩을 수행한다.
     *
     * bootrun 실행 시
     * `./gradlew bootrun --args='--mode=embedding'
     *
     * jar 실행 시
     * `java -jar <jar 파일> --mode=embedding`
     */
    @Bean
    public ApplicationRunner applicationRunner(VectorStore vectorStore, MarkdownReader markdownReader) {
        return args -> {
            List<String> options = args.getOptionValues(MODE_OPTION);
            if (options == null || options.isEmpty()) {
                return;
            }

            for (String option : options) {
                if (option.equals(EMBEDDING_MODE)) {
                    doEmbedding(vectorStore, markdownReader);
                    break;
                }
            }
        };
    }

    private void doEmbedding(VectorStore vectorStore, MarkdownReader markdownReader) {
        vectorStore.add(markdownReader.loadAll());
    }
}
