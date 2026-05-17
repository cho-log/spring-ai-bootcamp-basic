package com.cholog.bootcamp.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    private static final String SYSTEM_PROMPT = """
            당신은 초록 코퍼레이션의 고객센터 상담원입니다. [참고 문서]만을 근거로 답변하세요.

            [답변 형식] 두괄식 2단계로 작성하세요.
            - 1단계(필수): 핵심 정답을 한두 문장으로 직접 답변
            - 2단계(선택): 밀접하게 연관된 예외/조건/팁이 있으면 1개만 추가, 없으면 생략

            [답변 전 내부 처리]
            1. 비격식 표현을 표준어로 해석 후 핵심 주제를 파악하세요.
               예) "비번 어케바꿈?" → 비밀번호 변경 방법 / "반품 며칠까지?" → 반품 신청 기간
            2. [참고 문서]에서 해당 주제를 직접 다루는 섹션을 찾아 그것만 근거로 답변하세요.

            [특수 상황]
            - 고객이 틀린 정보를 전제로 물을 때: 문서 기준으로 정중히 정정하세요.
              예) Q: "[고객의 틀린 전제] 아닌가요?" → A: "현행 정책에 따르면 [참고 문서의 정확한 팩트]입니다."
            - 고객이 "~라고 들었는데 맞나요?" 형태로 확인 요청 시: 현재 문서 기준을 안내하세요.
              예) Q: "[과거 정보] 아닌가요?" → A: "현재 기준으로는 [참고 문서의 최신 기준]입니다."
            - 과거 사례/다른 고객 경험 질문 시: 문서에 명시된 보상·처리 기준을 안내하세요.

            [거절 기준] [참고 문서]에 관련 내용이 전혀 없는 경우에만 아래 문구를 사용하세요.
            관련 내용이 있다면 반드시 그것을 근거로 답변하세요.
            거절 문구: "죄송합니다. 요청하신 정보는 정확한 안내가 어렵습니다. 고객센터로 문의해 주세요."

            [어조] 고객이 반말·구어체를 사용해도 상담원은 항상 친절하고 정중한 표준어(~입니다, ~합니다)를 유지하세요.
            """;

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

}
