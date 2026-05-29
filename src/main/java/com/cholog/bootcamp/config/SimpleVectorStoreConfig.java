package com.cholog.bootcamp.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SimpleVectorStoreConfig {

    private static final String MODE_OPTION = "mode";
    private static final String EMBEDDING_MODE = "embedding";

    @Bean
    public SimpleVectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    /**
     * VectorStore 임베딩 runner
     * 애플리케이션 실행 시 --mode=embedding 옵션이 존재하면 임베딩을 새로 수행한다.
     *
     * bootrun 실행 시
     * `./gradlew bootrun --args='--mode=embedding'
     *
     * jar 실행 시
     * `java -jar <jar 파일> --mode=embedding`
     */
    @Bean
    public ApplicationRunner applicationRunner(SimpleVectorStore vectorStore, MarkdownReader markdownReader) {
        File vectorFile = Paths.get("data", "vector-store.json").toFile();
        createNewVectorFile(vectorFile);

        return args -> {
            List<String> options = args.getOptionValues(MODE_OPTION);
            if (options == null || options.isEmpty()) {
                vectorStore.load(vectorFile);
                return;
            }

            for (String option : options) {
                if (option.equals(EMBEDDING_MODE)) {
                    doEmbedding(vectorStore, markdownReader, vectorFile);
                    return;
                }
            }

            vectorStore.load(vectorFile);
        };
    }

    private void doEmbedding(SimpleVectorStore vectorStore, MarkdownReader markdownReader, File vectorFile) {
        vectorFile.delete();
        createNewVectorFile(vectorFile);
        vectorStore.doAdd(markdownReader.loadAll());
        vectorStore.save(vectorFile);
    }

    private void createNewVectorFile(File vectorFile) {
        try {
            vectorFile.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException("vector-store.json 생성 실패", e);
        }
    }
}
