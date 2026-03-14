package com.example.contextengine.infrastructure.adapter.in.rest;

import com.example.contextengine.domain.model.ContextAnalysis;
import com.example.contextengine.domain.port.in.AnalyzeContextUseCase;
import com.example.contextengine.infrastructure.adapter.in.rest.dto.ContextRequestDto;
import com.example.contextengine.infrastructure.adapter.in.rest.dto.ContextResponseDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/context")
public class ContextAnalyzeController {

    private final AnalyzeContextUseCase analyzeContextUseCase;

    public ContextAnalyzeController(AnalyzeContextUseCase analyzeContextUseCase) {
        this.analyzeContextUseCase = analyzeContextUseCase;
    }

    @PostMapping("/analyze")
    public ContextResponseDto analyze(@RequestBody ContextRequestDto request) {
        ContextAnalysis result = analyzeContextUseCase.analyze(
                request.getPrompt(),
                request.toDomainHistory(),
                request.getDocumentContext(),
                request.isWebSearchRequested());
        return ContextResponseDto.from(result);
    }
}
