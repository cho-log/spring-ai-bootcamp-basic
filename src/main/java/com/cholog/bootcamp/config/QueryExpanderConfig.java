package com.cholog.bootcamp.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QueryExpanderConfig {

    @Bean
    public QueryExpander queryExpander(ChatClient.Builder builder) {
        return MultiQueryExpander.builder()
            .chatClientBuilder(builder)
            .promptTemplate(PromptTemplate.builder()
                .template("""
                    You are an expert at information retrieval and search optimization.
                    Your task is to generate {number} different versions of the given query.
        
                    Each variant must cover different perspectives or aspects of the topic,
                    while maintaining the core intent of the original query. The goal is to
                    expand the search space and improve the chances of finding relevant information.
        
                    Do not explain your choices or add any other text.
                    Provide the query variants separated by newlines.
        
                    Original query: {query}
        
                    Query variants:
			
                    -----동의어-----
                    회원탈퇴 - 계정삭제
                    --------------
                """)
                .build())
            .numberOfQueries(4)
            .build();
    }
}
