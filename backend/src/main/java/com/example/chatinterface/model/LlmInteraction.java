package com.example.chatinterface.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "llm_interactions")
public class LlmInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    @JsonIgnore
    private Conversation conversation;

    @Column(nullable = false, length = 2000)
    private String prompt;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String response;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public LlmInteraction() {}

    public LlmInteraction(Conversation conversation, String prompt, String response) {
        this.conversation = conversation;
        this.prompt = prompt;
        this.response = response;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Conversation getConversation() { return conversation; }
    public String getPrompt() { return prompt; }
    public String getResponse() { return response; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
