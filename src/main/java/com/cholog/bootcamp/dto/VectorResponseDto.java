package com.cholog.bootcamp.dto;

import org.springframework.ai.document.Document;

import java.util.Map;

public record VectorResponseDto(
        Double score,
        String text,
        Map<String, Object> metadata
) {

    public static VectorResponseDto from(Document document) {
        return new VectorResponseDto(
                document.getScore(),
                document.getText(),
                document.getMetadata()
        );
    }
}
