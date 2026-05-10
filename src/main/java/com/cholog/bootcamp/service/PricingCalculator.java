package com.cholog.bootcamp.service;

import com.cholog.bootcamp.data.ModelPrice;
import com.cholog.bootcamp.data.TokenUsage;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * 모델별 토큰 사용량 기반으로 API 호출 비용을 계산하는 서비스.
 *
 * <p>가격 정보는 클래스패스의 {@code model_prices.json} 파일에서 로드된다.
 * 이 파일은 BerriAI/litellm 저장소의 가격 데이터를 그대로 사용하며,
 * <b>빌드 전 아래 명령으로 수동 갱신해야 한다.</b>
 *
 * <pre>{@code
 * curl -o src/main/resources/model_prices.json \
 *   https://raw.githubusercontent.com/BerriAI/litellm/main/model_prices_and_context_window.json
 * }</pre>
 *
 * <p>출처:
 * <a href="https://raw.githubusercontent.com/BerriAI/litellm/main/model_prices_and_context_window.json">
 * model_prices_and_context_window.json (BerriAI/litellm)</a>
 *
 * <p>{@link PostConstruct} 시점에 한 번 로드되어 메모리에 캐시되며,
 * 런타임 중에는 외부 네트워크 호출이 발생하지 않는다.
 */
@Slf4j
@Service
public class PricingCalculator {

    private final ObjectMapper mapper = new ObjectMapper();
    private Map<String, ModelPrice> prices;

    private static final String CLASSPATH_FILE = "model_prices.json";

    @PostConstruct
    public void init() throws Exception {
        var resource = new ClassPathResource(CLASSPATH_FILE);
        try (var is = resource.getInputStream()) {
            prices = mapper.readValue(is, mapper.getTypeFactory()
                    .constructMapType(Map.class, String.class, ModelPrice.class));
        }
        log.info("모델 JSON 을 로딩완료. 개수:{}", prices.size());
    }

    /**
     * 지정한 모델의 토큰 사용량으로 비용을 계산한다.
     *
     * @param model 모델명 (예: {@code "gpt-4o-mini"})
     * @param usage 입출력 토큰 사용량
     * @return 계산된 비용 (USD)
     * @throws IllegalArgumentException {@code model}이 가격 데이터에 없을 때
     */
    public BigDecimal calculatePrice(String model, TokenUsage usage) {
        var modelPrice = find(model);
        return modelPrice.calculate(usage);
    }

    /**
     * 모델명으로 가격 정보를 조회한다.
     *
     * @param model 모델명
     * @return 해당 모델의 가격 정보
     * @throws IllegalArgumentException {@code model}이 가격 데이터에 없을 때
     */
    public ModelPrice find(String model) {
        return Optional.ofNullable(prices.get(model))
                .orElseThrow(() -> new IllegalArgumentException("데이터에 해당 모델이 없습니다. 모델명: %s".formatted(model)));
    }
}
