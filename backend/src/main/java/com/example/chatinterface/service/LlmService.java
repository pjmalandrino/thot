package com.example.chatinterface.service;

import com.example.chatinterface.model.LlmInteraction;
import com.example.chatinterface.repository.LlmInteractionRepository;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LlmService {

    private final LlmInteractionRepository repository;
    private final OllamaChatModel chatModel;

    public LlmService(
            LlmInteractionRepository repository,
            @Value("${ollama.base-url}") String baseUrl,
            @Value("${ollama.model}") String modelName
    ) {
        this.repository = repository;
        this.chatModel = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .build();
    }

    public List<LlmInteraction> getAll() {
        return repository.findAll();
    }

    public LlmInteraction complete(String prompt) {
        String response = chatModel.generate(prompt);
        return repository.save(new LlmInteraction(prompt, response));
    }
}
