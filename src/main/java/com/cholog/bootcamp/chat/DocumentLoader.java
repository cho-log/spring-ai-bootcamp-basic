package com.cholog.bootcamp.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocumentLoader {

    private static final Path FAQ_DIRECTORY = Path.of("data/layer1_faq");
    private static final Path CURRENT_POLICY_DIRECTORY = Path.of("data/layer2_policies/current");
    private static final Path CHATLOG_DIRECTORY = Path.of("data/layer3_chatlogs");

    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper;

    public List<Document> load() {
        try {
            return Stream.of(
                    readTextDirectory(FAQ_DIRECTORY, "FAQ"),
                    readTextDirectory(CURRENT_POLICY_DIRECTORY, "CURRENT_POLICY"),
                    readChatlogDirectory()
                )
                .flatMap(List::stream)
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load support documents", e);
        }
    }

    private List<Document> readTextDirectory(Path directory, String sourceType) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files
                .filter(Files::isRegularFile)
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .flatMap(path -> readTextFile(path, sourceType).stream())
                .toList();
        }
    }

    private List<Document> readTextFile(Path path, String sourceType) {
        try {
            String content = Files.readString(path);

            if ("FAQ".equals(sourceType)) {
                return splitFaqDocument(path, content);
            }
            if ("CURRENT_POLICY".equals(sourceType)) {
                return splitPolicyDocument(path, content);
            }

            return List.of(createDocument(sourceType, path, content, null));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read file: " + path.getFileName(), e);
        }
    }

    private List<Document> splitFaqDocument(Path path, String content) {
        String[] sections = content.split(ragProperties.getFaqSplitRegex());

        return Stream.of(sections)
            .map(String::trim)
            .filter(section -> !section.isBlank())
            .map(section -> section.startsWith("#")
                ? createDocument("FAQ", path, section, null)
                : createDocument("FAQ", path, "### " + section, extractSectionTitle(section)))
            .toList();
    }

    private List<Document> splitPolicyDocument(Path path, String content) {
        String[] sections = content.split(ragProperties.getPolicySplitRegex());

        if (sections.length <= 1) {
            return List.of(createDocument("CURRENT_POLICY", path, content, null));
        }

        String prefix = sections[0].trim();

        return Stream.of(sections)
            .skip(1)
            .map(String::trim)
            .filter(section -> !section.isBlank())
            .map(section -> createDocument(
                "CURRENT_POLICY",
                path,
                prefix + "\n\n## " + section,
                extractSectionTitle(section)
            ))
            .toList();
    }

    private List<Document> readChatlogDirectory() throws IOException {
        try (Stream<Path> files = Files.list(CHATLOG_DIRECTORY)) {
            return files
                .filter(Files::isRegularFile)
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .flatMap(path -> readChatlogFile(path).stream())
                .toList();
        }
    }

    private List<Document> readChatlogFile(Path path) {
        try (Stream<String> lines = Files.lines(path)) {
            return lines
                .map(line -> parseCorrectChatlogDocument(path, line))
                .filter(document -> document != null)
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read chatlog file: " + path.getFileName(), e);
        }
    }

    private Document parseCorrectChatlogDocument(Path path, String line) {
        try {
            JsonNode root = objectMapper.readTree(line);
            if (!"correct".equals(root.path("agent_accuracy").asText())) {
                return null;
            }

            String conversationId = root.path("conversation_id").asText("");
            String primaryIntent = root.path("primary_intent").asText("");

            return new Document(
                "# Source Type: CHATLOG\n# Source: %s\n%s".formatted(path.getFileName(), line),
                Map.of(
                    "sourceType", "CHATLOG",
                    "source", path.getFileName().toString(),
                    "conversationId", conversationId,
                    "primaryIntent", primaryIntent
                )
            );
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse chatlog line in file: " + path.getFileName(), e);
        }
    }

    private Document createDocument(String sourceType, Path path, String body, String sectionTitle) {
        Map<String, Object> metadata = sectionTitle == null
            ? Map.of(
            "sourceType", sourceType,
            "source", path.getFileName().toString()
        )
            : Map.of(
                "sourceType", sourceType,
                "source", path.getFileName().toString(),
                "sectionTitle", sectionTitle
            );

        return new Document(
            "# Source Type: %s\n# Source: %s\n%s"
                .formatted(sourceType, path.getFileName(), body),
            metadata
        );
    }

    private String extractSectionTitle(String section) {
        int newlineIndex = section.indexOf('\n');
        return newlineIndex >= 0 ? section.substring(0, newlineIndex).trim() : section.trim();
    }
}
