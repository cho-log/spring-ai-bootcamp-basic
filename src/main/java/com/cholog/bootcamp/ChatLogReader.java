package com.cholog.bootcamp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

@Component
public class ChatLogReader {

    private static final Path DIRECTORY = Path.of("data/layer3_chatlogs");

    private final ObjectMapper objectMapper = new ObjectMapper();

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
        try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
            return lines
                .map(line -> parse(path, line))
                .flatMap(List::stream)
                .toList();
        } catch (IOException e) {
            throw new IllegalArgumentException("");
        }
    }

    private List<Document> parse(Path path, String line) {
        try {
            JsonNode root = objectMapper.readTree(line);
            if (!"correct".equals(root.path("agent_accuracy").asText())) {
                return List.of();
            }

            return List.of(toDocument(path, root));
        } catch (IOException e) {
            throw new IllegalArgumentException("");
        }
    }

    private Document toDocument(Path path, JsonNode root) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("layer", "layer3_chatlogs");
        metadata.put("source", path.getFileName().toString());
        metadata.put("conversation_id", root.path("conversation_id").asText());
        metadata.put("timestamp", root.path("timestamp").asText());
        metadata.put("channel", root.path("channel").asText());
        metadata.put("customer_tier", root.path("customer_tier").asText());
        metadata.put("primary_intent", root.path("primary_intent").asText());
        metadata.put("agent_accuracy", root.path("agent_accuracy").asText());
        metadata.put("tags", tags(root.path("tags")));

        return new Document("""
            Conversation ID: %s
            Primary Intent: %s
            Tags: %s
            Conversation:
            %s
            """.formatted(
            root.path("conversation_id").asText(),
            root.path("primary_intent").asText(),
            tags(root.path("tags")),
            turns(root.path("turns"))
        ).trim(), metadata);
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

    private String tags(JsonNode tags) {
        StringBuilder sb = new StringBuilder();

        for (JsonNode tag : tags) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(tag.asText());
        }

        return sb.toString();
    }
}
