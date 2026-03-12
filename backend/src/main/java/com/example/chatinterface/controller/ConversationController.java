package com.example.chatinterface.controller;

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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

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
    public List<LlmInteraction> getCompletions(@PathVariable Long id) {
        return llmService.getCompletions(id);
    }

    @PostMapping("/{id}/completions")
    @ResponseStatus(HttpStatus.CREATED)
    public LlmInteraction complete(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return llmService.complete(id, body.get("prompt"));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        llmService.deleteConversation(id);
    }
}
