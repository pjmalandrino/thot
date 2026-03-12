package com.example.chatinterface.service;

import com.example.chatinterface.model.Conversation;
import com.example.chatinterface.model.LlmInteraction;
import com.example.chatinterface.repository.ConversationRepository;
import com.example.chatinterface.repository.LlmInteractionRepository;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LlmService {

    private final ConversationRepository conversationRepository;
    private final LlmInteractionRepository interactionRepository;
    private final OllamaChatModel chatModel;

    public LlmService(
            ConversationRepository conversationRepository,
            LlmInteractionRepository interactionRepository,
            @Value("${ollama.base-url}") String baseUrl,
            @Value("${ollama.model}") String modelName
    ) {
        this.conversationRepository = conversationRepository;
        this.interactionRepository = interactionRepository;
        this.chatModel = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .build();
    }

    public List<Conversation> getConversations() {
        return conversationRepository.findAllByOrderByCreatedAtDesc();
    }

    public Conversation createConversation() {
        return conversationRepository.save(new Conversation("Nouvelle conversation"));
    }

    public List<LlmInteraction> getCompletions(Long conversationId) {
        return interactionRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    public LlmInteraction complete(Long conversationId, String prompt) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        String response = chatModel.generate(prompt);
        LlmInteraction interaction = interactionRepository.save(
                new LlmInteraction(conversation, prompt, response));

        // Auto-title from first prompt
        if ("Nouvelle conversation".equals(conversation.getTitle())) {
            String title = prompt.length() > 30 ? prompt.substring(0, 30) + "..." : prompt;
            conversation.setTitle(title);
            conversationRepository.save(conversation);
        }

        return interaction;
    }

    @Transactional
    public void deleteConversation(Long conversationId) {
        interactionRepository.deleteByConversationId(conversationId);
        conversationRepository.deleteById(conversationId);
    }
}
