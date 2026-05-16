package com.cholog.bootcamp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class FaqReader {

    private static final Path FAQ_DIRECTORY = Path.of("data/layer1_faq");

    public List<Document> read() {
        try (Stream<Path> paths = Files.list(FAQ_DIRECTORY)) {
            return paths
                .flatMap(path -> readFile(path).stream())
                .toList();
        } catch (IOException e) {
            throw new IllegalArgumentException("");
        }
    }

    private List<Document> readFile(Path path) {
        try {
            return parse(path, Files.readAllLines(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalArgumentException("");
        }
    }

    private List<Document> parse(Path path, List<String> lines) {
        List<Document> documents = new ArrayList<>();
        String category = null;
        String question = null;
        StringBuilder answer = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("# ")) {
                category = line.substring(2).trim();
                continue;
            }

            if (line.startsWith("## ")) {
                continue;
            }

            if (line.startsWith("### ")) {
                addDocument(documents, path, category, question, answer);
                question = line.substring(4).trim();
                answer.setLength(0);
                continue;
            }

            if (question != null) {
                answer.append(line).append('\n');
            }
        }

        addDocument(documents, path, category, question, answer);

        return documents;
    }

    private void addDocument(
        List<Document> documents, Path path, String category, String question, StringBuilder answer
    ) {
        if (question == null) {
            return;
        }

        documents.add(new Document("""
            Question: %s
            Answer: %s
            """.formatted(question, answer), Map.of(
            "layer", "layer1_faq",
            "source", path.getFileName().toString(),
            "category", category,
            "question", question
        )));
    }
}
