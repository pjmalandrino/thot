package com.example.chatinterface.repository;

import com.example.chatinterface.model.LlmInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LlmInteractionRepository extends JpaRepository<LlmInteraction, Long> {
    List<LlmInteraction> findByConversationIdOrderByCreatedAtAsc(Long conversationId);
    void deleteByConversationId(Long conversationId);
}
