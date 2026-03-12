package com.example.chatinterface.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "llm_interactions")
public class LlmInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2000)
    private String prompt;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String response;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public LlmInteraction() {}

    public LlmInteraction(String prompt, String response) {
        this.prompt = prompt;
        this.response = response;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getPrompt() { return prompt; }
    public String getResponse() { return response; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
