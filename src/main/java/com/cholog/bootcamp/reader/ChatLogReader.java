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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ChatLogReader {

    private static final Path DIRECTORY = Path.of("data/layer3_chatlogs");

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Document> read() {
        try (Stream<Path> paths = Files.list(DIRECTORY)) {
            List<Document> documents = new ArrayList<>();
            paths.forEach(path -> documents.addAll(readFile(path)));
            return documents;
        } catch (IOException e) {
            throw new RuntimeException("채팅 로그 디렉토리를 읽는 중 오류가 발생했습니다.", e);
        }
    }

    private List<Document> readFile(Path path) {
        try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
            List<Document> documents = new ArrayList<>();
            lines.forEach(line -> {
                Document document = parse(path, line);
                if (document != null) {
                    documents.add(document);
                }
            });
            return documents;
        } catch (IOException e) {
            throw new IllegalArgumentException("");
        }
    }

    private Document parse(Path path, String line) {
        try {
            JsonNode root = objectMapper.readTree(line);
            if (!"correct".equals(root.path("agent_accuracy").asText())) {
                return null;
            }

            return toDocument(path, root);
        } catch (IOException e) {
            throw new IllegalArgumentException("");
        }
    }

    private Document toDocument(Path path, JsonNode root) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("layer", "layer3_chatlogs");
        metadata.put("filepath", path.toString());
        metadata.put("conversation_id", root.path("conversation_id").asText());
        metadata.put("primary_intent", root.path("primary_intent").asText());
        metadata.put("agent_accuracy", root.path("agent_accuracy").asText());

        return new Document(turns(root.path("turns")), metadata);
    }

    private String turns(JsonNode turns) {
        StringBuilder sb = new StringBuilder();

        for (JsonNode turn : turns) {
            sb.append(turn.path("role").asText())
                .append(": ")
                .append(turn.path("text").asText())
                .append('\n');
        }

        return sb.toString().trim();
    }
}
