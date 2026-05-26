package com.cholog.bootcamp.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelPrice(
        @JsonProperty("input_cost_per_token") BigDecimal inputCostPerToken,
        @JsonProperty("output_cost_per_token") BigDecimal outputCostPerToken,
        @Nullable @JsonProperty("cache_read_input_token_cost") BigDecimal cacheReadCost,
        @Nullable @JsonProperty("cache_creation_input_token_cost") BigDecimal cacheWriteCost,
        @JsonProperty("supports_prompt_caching") Boolean supportsPromptCaching
) {
    public BigDecimal calculate(TokenUsage usage) {
        return inputCostPerToken.multiply(BigDecimal.valueOf(usage.promptTokens()))
                .add(outputCostPerToken.multiply(BigDecimal.valueOf(usage.completionTokens())));
    }
}