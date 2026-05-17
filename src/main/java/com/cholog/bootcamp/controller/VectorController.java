package com.cholog.bootcamp.controller;

import com.cholog.bootcamp.dto.VectorResponseDto;
import com.cholog.bootcamp.service.VectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/vector")
@Slf4j
public class VectorController {

    private final VectorService vectorService;

    @GetMapping
    public ResponseEntity<List<VectorResponseDto>> question(
            @RequestParam String keyword
    ) {
        log.info("Vector 요청이 들어왔습니다. {}", keyword);
        var response = vectorService.request(keyword);
        return ResponseEntity.ok(response);
    }
}
