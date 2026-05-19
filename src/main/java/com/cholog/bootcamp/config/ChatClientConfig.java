package com.cholog.bootcamp.config;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * FAQ 챗봇용 {@link ChatClient} 구성.
 *
 * <p>3계층 지식 베이스를 system prompt에 임베드한다:
 * <ul>
 *   <li><b>FAQ</b>: 공식 FAQ 문서 (layer1_faq)</li>
 *   <li><b>Policies</b>: 현행 사내 정책 (layer2_policies/current) — FAQ보다 우선</li>
 *   <li><b>Examples</b>: 과거 상담 샘플 (layer3_examples/sample.md) — 톤/구조 참고용</li>
 * </ul>
 *
 * <p>의도적으로 제외된 자료:
 * <ul>
 *   <li>{@code layer2_policies/deprecated/*} — 폐기 정책. 모델이 옛 기준으로 답하면 위험.</li>
 *   <li>{@code layer2_policies/internal/*} — CS 팀 내부 문서. 고객 응답에 부적합.</li>
 *   <li>{@code layer3_chatlogs/*.jsonl} 전체(268KB) — 토큰 폭발. 샘플만 사용. 본격 활용은 RAG 단계.</li>
 * </ul>
 */
@Slf4j
@Configuration
@NoArgsConstructor
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 @Value("classpath:prompts/faq-system.st") Resource systemTemplateResource,
                                 @Value("classpath:layer1_faq/*.md") Resource[] faqResources,
                                 @Value("classpath:layer2_policies/current/*.md") Resource[] policyResources,
                                 @Value("classpath:layer3_examples/*.md") Resource[] exampleResources) {

        var systemTemplate = loadSystemTemplate(systemTemplateResource);
        var faq = concatResources(faqResources, "faq");
        var policies = concatResources(policyResources, "policies");
        var examples = concatResources(exampleResources, "examples");

        return builder
                .defaultSystem(spec -> spec
                        .text(systemTemplate))
                .build();
    }

    private String loadSystemTemplate(Resource systemTemplate) {
        try {
            String content = systemTemplate.getContentAsString(StandardCharsets.UTF_8);
            log.info("faq 시스템 프롬프트를 로드합니다. 길이: {}", content.length());
            return content;
        } catch (Exception e) {
            log.warn("faq 시스템 프롬프트 로드중 문제가 발생했습니다.", e);
            return "";
        }
    }

    private String concatResources(Resource[] resources, String label) {
        try {
            log.info("{} layer 파일을 로드합니다. 개수: {}", label, resources.length);

            var sb = new StringBuilder();
            for (Resource r : resources) {
                sb.append("## ").append(r.getFilename()).append(System.lineSeparator());
                sb.append(r.getContentAsString(StandardCharsets.UTF_8));
                sb.append(System.lineSeparator()).append(System.lineSeparator());
                sb.append("---");
                sb.append(System.lineSeparator()).append(System.lineSeparator());
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("{} layer 로드중 문제가 발생했습니다.", label, e);
            return "";
        }
    }
}
