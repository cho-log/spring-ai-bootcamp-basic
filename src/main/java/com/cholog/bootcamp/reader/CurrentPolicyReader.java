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

@Component
public class CurrentPolicyReader {

    private static final Path DIRECTORY = Path.of("data/layer2_policies/current");

    public List<Document> read() {
        try (Stream<Path> paths = Files.list(DIRECTORY)) {
            List<Document> documents = new ArrayList<>();
            paths.sorted()
                .forEach(path -> documents.addAll(readFile(path)));
            return documents;
        } catch (IOException e) {
            throw new RuntimeException("정책 디렉토리를 읽는 중 오류가 발생했습니다.", e);
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
        String section = null;
        StringBuilder sectionBody = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("# ")) {
                category = line.substring(2).trim();
                continue;
            }

            if (category == null) {
                continue;
            }

            if (line.startsWith("## ")) {
                addDocument(documents, path, category, section, sectionBody);
                section = line.substring(3).trim();
                sectionBody.setLength(0);
                continue;
            }

            if (section != null) {
                sectionBody.append(line).append('\n');
            }
        }

        addDocument(documents, path, category, section, sectionBody);

        return documents;
    }

    private void addDocument(
        List<Document> documents,
        Path path,
        String category,
        String section,
        StringBuilder sectionBody
    ) {
        if (section == null) {
            return;
        }

        String body = sectionBody.toString().trim();
        if (body.isBlank()) {
            return;
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("layer", "layer2_policies");
        metadata.put("policy_scope", "current");
        metadata.put("filepath", path.toString());
        metadata.put("category", category);
        metadata.put("section", section);

        documents.add(new Document(body, metadata));
    }
}
