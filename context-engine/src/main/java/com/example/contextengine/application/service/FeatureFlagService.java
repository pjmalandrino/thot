package com.example.contextengine.application.service;

import com.example.contextengine.domain.port.in.ManageFeaturesUseCase;
import com.example.contextengine.infrastructure.adapter.out.persistence.FeatureFlagEntity;
import com.example.contextengine.infrastructure.adapter.out.persistence.FeatureFlagJpaRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FeatureFlagService implements ManageFeaturesUseCase {

    private final FeatureFlagJpaRepository repository;

    public FeatureFlagService(FeatureFlagJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<FeatureView> listAll() {
        return repository.findAll().stream()
                .map(this::toView)
                .toList();
    }

    @Override
    public FeatureView toggle(String featureName, boolean enabled) {
        FeatureFlagEntity entity = repository.findByFeatureName(featureName)
                .orElseThrow(() -> new RuntimeException("Feature not found: " + featureName));
        entity.setEnabled(enabled);
        entity.setUpdatedAt(LocalDateTime.now());
        return toView(repository.save(entity));
    }

    @Override
    public FeatureView updateConfig(String featureName, String configJson) {
        FeatureFlagEntity entity = repository.findByFeatureName(featureName)
                .orElseThrow(() -> new RuntimeException("Feature not found: " + featureName));
        entity.setConfigJson(configJson);
        entity.setUpdatedAt(LocalDateTime.now());
        return toView(repository.save(entity));
    }

    private FeatureView toView(FeatureFlagEntity entity) {
        return new FeatureView(
                entity.getId(),
                entity.getFeatureName(),
                entity.isEnabled(),
                entity.getConfigJson(),
                entity.getDescription());
    }
}
