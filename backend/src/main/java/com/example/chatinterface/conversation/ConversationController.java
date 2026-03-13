package com.example.chatinterface.conversation;

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

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping
    public List<Conversation> getAll() {
        return conversationService.getConversations();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Conversation create() {
        return conversationService.createConversation();
    }

    @GetMapping("/{id}/completions")
    public List<CompletionResponse> getCompletions(@PathVariable Long id) {
        return conversationService.getCompletions(id).stream()
                .map(CompletionResponse::from)
                .toList();
    }

    @PostMapping("/{id}/completions")
    @ResponseStatus(HttpStatus.CREATED)
    public CompletionResponse complete(@PathVariable Long id, @RequestBody CompletionRequest request) {
        LlmInteraction interaction = request.isWebSearch()
                ? conversationService.completeWithWebSearch(id, request.getPrompt(), request.getModelId())
                : conversationService.complete(id, request.getPrompt(), request.getModelId());
        return CompletionResponse.from(interaction);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        conversationService.deleteConversation(id);
    }
}
