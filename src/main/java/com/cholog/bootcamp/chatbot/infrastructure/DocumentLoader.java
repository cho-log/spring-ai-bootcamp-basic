package com.cholog.bootcamp.chatbot.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentLoader {

    private static final String FAQ_PATTERN = "file:data/layer1_faq/*.md";
    private static final String POLICY_PATTERN = "file:data/layer2_policies/current/*.md";
    private static final String CHATLOG_PATTERN = "file:data/layer3_chatlogs/*.jsonl";
    private static final String LAYER_FAQ = "faq";
    private static final String LAYER_POLICY = "policy";

    private final ChatlogParser chatlogParser;
    private final PathMatchingResourcePatternResolver resolver =
            new PathMatchingResourcePatternResolver();
    private final MarkdownHeadingSplitter faqSplitter = new MarkdownHeadingSplitter("###");
    private final MarkdownHeadingSplitter policySplitter = new MarkdownHeadingSplitter("##");

    public List<Document> loadFaq() {
        return load(FAQ_PATTERN, LAYER_FAQ, faqSplitter);
    }

    public List<Document> loadPolicies() {
        return load(POLICY_PATTERN, LAYER_POLICY, policySplitter);
    }

    public List<Document> loadChatlogs() {
        try {
            Resource[] resources = resolver.getResources(CHATLOG_PATTERN);
            if (resources.length == 0) {
                log.warn("챗로그 파일 없음");
                return List.of();
            }

            List<Document> result = new ArrayList<>();
            for (Resource resource : resources) {
                result.addAll(chatlogParser.parse(resource));
            }

            log.info("챗로그 로드 완료: 총 {}개 청크", result.size());
            return result;

        } catch (IOException e) {
            throw new IllegalStateException("챗로그 로딩 실패", e);
        }
    }

    private List<Document> load(String pattern, String layer, MarkdownHeadingSplitter splitter) {
        try {
            Resource[] resources = resolver.getResources(pattern);

            if (resources.length == 0) {
                log.warn("문서 없음: layer={}", layer);
                return List.of();
            }

            List<Document> result = new ArrayList<>();
            for (Resource resource : resources) {
                result.addAll(toDocuments(resource, layer, splitter));
            }

            log.info("문서 로드 완료: layer={}, 총 {}개 청크", layer, result.size());
            return result;

        } catch (IOException e) {
            throw new IllegalStateException("문서 로딩 실패: pattern=" + pattern, e);
        }
    }

    private List<Document> toDocuments(Resource resource, String layer, MarkdownHeadingSplitter splitter) {
        List<Document> raw = new TextReader(resource).get();
        List<Document> chunks = raw.stream()
                .flatMap(doc -> splitter.split(doc).stream())
                .toList();
        chunks.forEach(doc -> attachMetadata(doc, resource, layer));
        log.debug("파일 청킹: {} → {}개 청크", resource.getFilename(), chunks.size());
        return chunks;
    }

    private void attachMetadata(Document doc, Resource resource, String layer) {
        doc.getMetadata().put("source", resource.getFilename());
        doc.getMetadata().put("layer", layer);
    }
}
