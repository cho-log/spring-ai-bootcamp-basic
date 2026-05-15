package com.cholog.bootcamp;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class MarkdownReader {

    private final Resource[] resources;

    public MarkdownReader(@Value("classpath:data/**/*.md") Resource[] resources) {
        this.resources = resources;
    }

    public List<Document> loadAll() {
        List<Document> allDocuments = new ArrayList<>();
        for (Resource resource : resources) {
            MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                .withHorizontalRuleCreateDocument(true)
                .withIncludeCodeBlock(false)
                .withIncludeBlockquote(false)
                .withAdditionalMetadata("filename", resource.getFilename())
                .build();

            MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);
            allDocuments.addAll(reader.get());
        }
        return allDocuments;
    }
}
