package com.example.contextengine.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeatureFlagJpaRepository extends JpaRepository<FeatureFlagEntity, Long> {
    Optional<FeatureFlagEntity> findByFeatureName(String featureName);
}
