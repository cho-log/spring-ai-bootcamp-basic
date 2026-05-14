package com.cholog.bootcamp.chatbot.infrastructure;

import org.springframework.ai.document.Document;

import java.util.Arrays;
import java.util.List;

public class MarkdownHeadingSplitter {

    private final String heading;

    public MarkdownHeadingSplitter(String heading) {
        this.heading = heading;
    }

    public List<Document> split(Document document) {
        String[] sections = document.getText().split("(?m)^(?=" + heading + " )");
        return Arrays.stream(sections)
                .map(String::strip)
                .filter(s -> s.startsWith(heading))
                .map(Document::new)
                .toList();
    }
}