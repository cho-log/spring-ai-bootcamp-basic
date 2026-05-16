package com.cholog.bootcamp;

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
public class InternalPolicyReader {

    private static final Path DIRECTORY = Path.of("data/layer2_policies/internal");

    public List<Document> read() {
        try (Stream<Path> paths = Files.list(DIRECTORY)) {
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
        Map<String, Object> metadata = new HashMap<>();
        String title = path.getFileName().toString();
        String section = null;
        StringBuilder sectionBody = new StringBuilder();
        boolean frontMatter = false;
        boolean frontMatterDone = false;

        for (String line : lines) {
            if (!frontMatterDone && line.equals("---")) {
                if (!frontMatter) {
                    frontMatter = true;
                } else {
                    frontMatterDone = true;
                }
                continue;
            }

            if (frontMatter && !frontMatterDone) {
                parseMetadata(line, metadata);
                continue;
            }

            if (line.startsWith("# ")) {
                title = line.substring(2).trim();
                continue;
            }

            if (line.startsWith("## ")) {
                addDocument(documents, path, metadata, title, section, sectionBody);
                section = line.substring(3).trim();
                sectionBody.setLength(0);
                continue;
            }

            if (section != null) {
                sectionBody.append(line).append('\n');
            }
        }

        addDocument(documents, path, metadata, title, section, sectionBody);

        return documents;
    }

    private void parseMetadata(String line, Map<String, Object> metadata) {
        int separator = line.indexOf(':');
        if (separator < 0) {
            return;
        }

        String key = line.substring(0, separator).trim();
        String value = line.substring(separator + 1).trim();
        if (!key.isBlank() && !value.isBlank()) {
            metadata.put(key, value);
        }
    }

    private void addDocument(
        List<Document> documents,
        Path path,
        Map<String, Object> fileMetadata,
        String title,
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

        Map<String, Object> metadata = new HashMap<>(fileMetadata);
        metadata.put("layer", "layer2_policies");
        metadata.put("policy_scope", "internal");
        metadata.put("source", path.getFileName().toString());
        metadata.put("title", metadata.getOrDefault("title", title));
        metadata.put("section", section);

        documents.add(new Document("""
            Policy: %s
            Section: %s
            %s
            """.formatted(metadata.get("title"), section, body).trim(), metadata));
    }
}
