package com.cholog.bootcamp.reader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
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
            List<Document> documents = new ArrayList<>();
            paths.forEach(path -> documents.addAll(readFile(path)));
            return documents;
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
        StringBuilder body = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("# ")) {
                category = line.substring(2).trim();
                continue;
            }

            if (line.startsWith("## ")) {
                continue;
            }

            if (line.startsWith("### ")) {
                addDocument(documents, path, category, question, body);
                question = line.substring(4).trim();
                body.setLength(0);
                continue;
            }

            if (question != null) {
                body.append(line).append('\n');
            }
        }

        addDocument(documents, path, category, question, body);

        return documents;
    }

    private void addDocument(
        List<Document> documents, Path path, String category, String question, StringBuilder body
    ) {
        if (question == null) {
            return;
        }

        String text = body.toString().trim();
        if (text.isBlank()) {
            return;
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("question", question);
        metadata.put("layer", "layer1_faq");
        metadata.put("filepath", path.toString());
        metadata.put("category", category);

        documents.add(new Document(text, metadata));
    }
}
