package com.cholog.bootcamp.chatbot.util;

import org.springframework.ai.document.Document;

import java.util.Arrays;
import java.util.List;

public class MarkdownHeadingSplitter {

    private final String heading;

    public MarkdownHeadingSplitter(String heading) {
        this.heading = heading;
    }

    public List<Document> split(Document document) {
        String text = document.getText();
        String title = extractTitle(text);

        String[] sections = text.split("(?m)^(?=" + heading + " )");
        return Arrays.stream(sections)
                .map(String::strip)
                .filter(s -> s.startsWith(heading))
                .map(s -> new Document(title + "\n" + s))
                .toList();
    }

    private String extractTitle(String text) {
        return text.lines()
                .filter(line -> line.startsWith("# "))
                .findFirst()
                .orElse("");
    }
}