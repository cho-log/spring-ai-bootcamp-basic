package com.cholog.bootcamp.chatbot.infrastructure;

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
public class DocumentLoader {

    private static final String FAQ_PATTERN = "file:data/layer1_faq/*.md";
    private static final String POLICY_PATTERN = "file:data/layer2_policies/current/*.md";
    private static final String LAYER_FAQ = "faq";
    private static final String LAYER_POLICY = "policy";

    private final PathMatchingResourcePatternResolver resolver =
            new PathMatchingResourcePatternResolver();

    public List<Document> loadFaq() {
        return load(FAQ_PATTERN, LAYER_FAQ);
    }

    public List<Document> loadPolicies() {
        return load(POLICY_PATTERN, LAYER_POLICY);
    }

    private List<Document> load(String pattern, String layer) {
        try {
            Resource[] resources = resolver.getResources(pattern);

            if (resources.length == 0) {
                log.warn("문서 없음: layer={}", layer);
                return List.of();
            }

            List<Document> result = new ArrayList<>();
            for (Resource resource : resources) {
                result.addAll(toDocuments(resource, layer));
            }

            log.info("문서 로드 완료: layer={}, 총 {}개", layer, result.size());
            return result;

        } catch (IOException e) {
            throw new IllegalStateException("문서 로딩 실패: pattern=" + pattern, e);
        }
    }

    private List<Document> toDocuments(Resource resource, String layer) {
        List<Document> docs = new TextReader(resource).get();
        docs.forEach(doc -> attachMetadata(doc, resource, layer));
        log.debug("파일 로드: {}", resource.getFilename());
        return docs;
    }

    private void attachMetadata(Document doc, Resource resource, String layer) {
        doc.getMetadata().put("source", resource.getFilename());
        doc.getMetadata().put("layer", layer);
    }
}
