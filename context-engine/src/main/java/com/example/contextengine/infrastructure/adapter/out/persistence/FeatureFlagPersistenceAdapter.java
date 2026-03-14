package com.example.contextengine.infrastructure.adapter.out.persistence;

import com.example.contextengine.domain.port.out.FeatureFlagPort;
import org.springframework.stereotype.Component;

@Component
public class FeatureFlagPersistenceAdapter implements FeatureFlagPort {

    private final FeatureFlagJpaRepository repository;

    public FeatureFlagPersistenceAdapter(FeatureFlagJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean isEnabled(String featureName) {
        return repository.findByFeatureName(featureName)
                .map(FeatureFlagEntity::isEnabled)
                .orElse(false);
    }
}
