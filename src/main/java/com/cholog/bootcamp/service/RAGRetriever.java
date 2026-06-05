package com.cholog.bootcamp.service;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class RAGRetriever {

    private final VectorStore vectorStore;
    private final QueryTransformer queryTransformer;
    private final QueryExpander queryExpander;

    public RAGRetriever(
        VectorStore vectorStore,
        QueryTransformer queryTransformer,
        QueryExpander queryExpander
    ) {
        this.vectorStore = vectorStore;
        this.queryTransformer = queryTransformer;
        this.queryExpander = queryExpander;
    }

    public List<Document> retrieve(String question, int topK) {
        Query query = getQuery(question);
        SearchRequest searchRequest = getSearchRequest(query, topK);
        return vectorStore.similaritySearch(searchRequest);
    }

    private Query getQuery(String question) {
        Query originalQuery = new Query(question);
        Query transformedQuery = queryTransformer.transform(originalQuery);
        List<Query> expand = queryExpander.expand(originalQuery);
        for (Query query : expand) {
            log.info("Expanding query {}", query.text());
        }
        log.info("Original Query: {}", originalQuery.text());
        log.info("Transformed Query: {}", transformedQuery.text());
        return transformedQuery;
    }

    private SearchRequest getSearchRequest(Query query, int topK) {
        return SearchRequest.builder()
            .query(query.text())
            .topK(topK)
            .build();
    }
}
