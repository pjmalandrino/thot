package com.example.chatinterface.conversation;

import com.example.chatinterface.thotspace.Thotspace;

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
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "thotspace_id", nullable = false)
    private Thotspace thotspace;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Conversation() {}

    public Conversation(String title, Thotspace thotspace) {
        this.title = title;
        this.thotspace = thotspace;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Thotspace getThotspace() { return thotspace; }
    public void setThotspace(Thotspace thotspace) { this.thotspace = thotspace; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
