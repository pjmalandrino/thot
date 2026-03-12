package com.example.chatinterface.controller;

import com.example.chatinterface.model.LlmInteraction;
import com.example.chatinterface.service.LlmService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/completions")
public class CompletionController {

    private final LlmService llmService;

    public CompletionController(LlmService llmService) {
        this.llmService = llmService;
    }

    @GetMapping
    public List<LlmInteraction> getAll() {
        return llmService.getAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LlmInteraction complete(@RequestBody Map<String, String> body) {
        return llmService.complete(body.get("prompt"));
    }
}
