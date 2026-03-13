package com.example.chatinterface.controller;

import com.example.chatinterface.dto.CompletionRequest;
import com.example.chatinterface.dto.CompletionResponse;
import com.example.chatinterface.model.Conversation;
import com.example.chatinterface.model.LlmInteraction;
import com.example.chatinterface.service.LlmService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private static final Logger log = LoggerFactory.getLogger(ConversationController.class);

    private final LlmService llmService;

    public ConversationController(LlmService llmService) {
        this.llmService = llmService;
    }

    @GetMapping
    public List<Conversation> getAll() {
        return llmService.getConversations();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Conversation create() {
        return llmService.createConversation();
    }

    @GetMapping("/{id}/completions")
    public List<CompletionResponse> getCompletions(@PathVariable Long id) {
        return llmService.getCompletions(id).stream()
                .map(CompletionResponse::from)
                .toList();
    }

    @PostMapping("/{id}/completions")
    @ResponseStatus(HttpStatus.CREATED)
    public CompletionResponse complete(@PathVariable Long id, @RequestBody CompletionRequest request) {
        log.info("[CONTROLLER] POST /api/conversations/{}/completions | prompt='{}' | webSearch={}",
                id, request.getPrompt(), request.isWebSearch());
        LlmInteraction interaction;
        if (request.isWebSearch()) {
            interaction = llmService.completeWithWebSearch(id, request.getPrompt());
        } else {
            interaction = llmService.complete(id, request.getPrompt());
        }
        return CompletionResponse.from(interaction);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        llmService.deleteConversation(id);
    }
}
