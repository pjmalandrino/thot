package com.example.chatinterface.llm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LlmModelRepository extends JpaRepository<LlmModel, Long> {

    List<LlmModel> findAllByEnabledTrue();

    Optional<LlmModel> findFirstByEnabledTrue();

    List<LlmModel> findByProviderId(Long providerId);
}
