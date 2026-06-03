package com.cholog.bootcamp.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QueryTransformerConfig {

    @Bean
    public QueryTransformer getQueryTransformer(ChatClient.Builder builder) {
        return RewriteQueryTransformer.builder()
            .chatClientBuilder(builder)
            .build();
    }
}
