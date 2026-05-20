package com.cholog.bootcamp.chatbot.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class ChatlogParser {

    private static final String LAYER_CHATLOG = "chatlog";
    private static final String ACCURACY_CORRECT = "correct";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Document> parse(Resource resource) throws IOException {
        List<Document> result = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                Document doc = parseLine(line, resource.getFilename());
                if (doc != null) result.add(doc);
            }
        }

        log.debug("챗로그 파싱: {} → {}개 청크", resource.getFilename(), result.size());
        return result;
    }

    private Document parseLine(String line, String filename) throws IOException {
        JsonNode node = objectMapper.readTree(line);
        if (!ACCURACY_CORRECT.equals(node.path("agent_accuracy").asText())) return null;

        String text = buildText(node);
        if (text == null) return null;

        return toDocument(text, filename);
    }

    private Document toDocument(String text, String filename) {
        Document doc = new Document(text);
        doc.getMetadata().put("layer", LAYER_CHATLOG);
        doc.getMetadata().put("source", filename);
        return doc;
    }

    private String buildText(JsonNode node) {
        StringBuilder sb = new StringBuilder();
        appendTags(sb, node);
        boolean hasAgentTurn = appendTurns(sb, node);
        return hasAgentTurn ? sb.toString().trim() : null;
    }

    private void appendTags(StringBuilder sb, JsonNode node) {
        List<String> tags = new ArrayList<>();
        node.path("tags").forEach(tag -> tags.add(tag.asText()));
        if (!tags.isEmpty()) {
            sb.append("[태그: ").append(String.join(", ", tags)).append("]\n");
        }
    }

    private boolean appendTurns(StringBuilder sb, JsonNode node) {
        boolean hasAgentTurn = false;
        for (JsonNode turn : node.path("turns")) {
            String text = turn.path("text").asText();

            String role = turn.path("role").asText();
            String prefix = "customer".equals(role) ? "고객" : "상담원";
            sb.append(prefix).append(": ").append(text).append("\n");

            if ("agent".equals(role)) hasAgentTurn = true;
        }
        return hasAgentTurn;
    }

}
