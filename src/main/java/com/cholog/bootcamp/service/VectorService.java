package com.cholog.bootcamp.service;

import com.cholog.bootcamp.dto.VectorResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VectorService {

    private final VectorStore vectorStore;

    public List<VectorResponseDto> request(String keyword) {
        var hits = vectorStore.similaritySearch(keyword);
        log.info("{} 에 대한 결과: {}개", keyword, hits.size());
        return hits.stream().map(VectorResponseDto::from).toList();
    }
}
