package com.example.chatinterface.llm;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/llm")
public class LlmModelController {

    private final LlmModelRepository modelRepository;

    public LlmModelController(LlmModelRepository modelRepository) {
        this.modelRepository = modelRepository;
    }

    @GetMapping("/models")
    public List<ModelResponse> getEnabledModels() {
        return modelRepository.findAllByEnabledTrue().stream()
                .map(ModelResponse::from)
                .toList();
    }
}
