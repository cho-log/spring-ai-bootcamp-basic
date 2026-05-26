package com.cholog.bootcamp.dto;

import com.cholog.bootcamp.data.TokenUsage;

public record FrequentlyQuestionChatResponseDto(String answer, TokenUsage usage) {
}
