package com.cholog.bootcamp.chat;

import com.cholog.bootcamp.chat.dto.ChatResponse;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

@Service
public class ChatService {

    private static final Path FAQ_DIRECTORY = Path.of("data/layer1_faq");
    private static final Path CURRENT_POLICY_DIRECTORY = Path.of("data/layer2_policies/current");
    private static final Path CHATLOG_DIRECTORY = Path.of("data/layer3_chatlogs");
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final RagProperties ragProperties;

    public ChatService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore, RagProperties ragProperties) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.ragProperties = ragProperties;
    }

    @PostConstruct
    void loadFaqContext() {
        try {
            List<Document> documents = Stream.of(
                    readTextDirectory(FAQ_DIRECTORY, "FAQ"),
                    readTextDirectory(CURRENT_POLICY_DIRECTORY, "CURRENT_POLICY"),
                    readChatlogDirectory()
                )
                .flatMap(List::stream)
                .toList();

            vectorStore.add(documents);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load support documents", e);
        }
    }

    public ChatResponse ask(String question) {
        List<Document> retrievedDocuments = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(question)
                .topK(ragProperties.getTopK())
                .build()
        );

        logSearchResults(question, retrievedDocuments);

        String supportContext = retrievedDocuments
            .stream()
            .map(Document::getText)
            .collect(Collectors.joining("\n\n===\n\n"));

        org.springframework.ai.chat.model.ChatResponse response = chatClient.prompt()
            .system("""
                    - 당신은 Cholog Corporation의 고객 전용 챗봇 서비스이다.
                    - 제공된 컨텍스트만을 활용하라.
                    - current policy를 가장 우선하고, 그 다음 FAQ, 마지막으로 correct chatlog를 참고하라.
                    - chatlog는 보조 참고 자료이며 policy나 FAQ보다 신뢰도가 낮다.
                    - 제공된 컨텍스트로 답할 수 없다면, '고객센터에 문의해주세요'라고 답하라.
                    - 한국어로 답하라.
                """)
            .user("""
                    Customer question:
                    %s
                
                    Support context:
                    %s
                """.formatted(question, supportContext))
            .call()
            .chatResponse();

        Usage usage = response.getMetadata().getUsage();

        return new ChatResponse(
            response.getResult().getOutput().getText(),
            new ChatResponse.TokenUsage(
                usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens(),
                usage == null || usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens(),
                usage == null || usage.getTotalTokens() == null ? 0 : usage.getTotalTokens()
            )
        );
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

            return List.of(new Document(
                "# Source Type: %s\n# Source: %s\n%s"
                    .formatted(sourceType, path.getFileName(), content),
                Map.of(
                    "sourceType", sourceType,
                    "source", path.getFileName().toString()
                )
            ));
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
                : createDocument("FAQ", path, "### " + section, extractFaqQuestion(section)))
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

    private String extractFaqQuestion(String section) {
        int newlineIndex = section.indexOf('\n');
        return newlineIndex >= 0 ? section.substring(0, newlineIndex).trim() : section.trim();
    }

    private String extractSectionTitle(String section) {
        int newlineIndex = section.indexOf('\n');
        return newlineIndex >= 0 ? section.substring(0, newlineIndex).trim() : section.trim();
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
                .filter(line -> line.contains("\"agent_accuracy\":\"correct\""))
                .map(line -> new Document(
                    "# Source Type: CHATLOG\n# Source: %s\n%s".formatted(path.getFileName(), line),
                    Map.of(
                        "sourceType", "CHATLOG",
                        "source", path.getFileName().toString()
                    )
                ))
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read chatlog file: " + path.getFileName(), e);
        }
    }

    private void logSearchResults(String question, List<Document> documents) {
        String resultSummary = documents.isEmpty()
            ? "no documents retrieved"
            : documents.stream()
                .map(this::formatDocumentSummary)
                .collect(Collectors.joining(" | "));

        log.info("RAG search question='{}' topK={} results={}",
            question,
            ragProperties.getTopK(),
            resultSummary
        );
    }

    private String formatDocumentSummary(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        String sourceType = String.valueOf(metadata.getOrDefault("sourceType", "UNKNOWN"));
        String source = String.valueOf(metadata.getOrDefault("source", "UNKNOWN"));
        Object sectionTitle = metadata.get("sectionTitle");

        if (sectionTitle == null) {
            return "%s/%s".formatted(sourceType, source);
        }

        return "%s/%s#%s".formatted(sourceType, source, sectionTitle);
    }
}
