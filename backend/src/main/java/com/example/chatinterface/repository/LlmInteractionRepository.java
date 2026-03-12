package com.example.chatinterface.repository;

import com.example.chatinterface.model.LlmInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LlmInteractionRepository extends JpaRepository<LlmInteraction, Long> {
}
