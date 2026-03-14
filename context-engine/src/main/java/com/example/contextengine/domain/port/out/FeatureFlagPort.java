package com.example.contextengine.domain.port.out;

public interface FeatureFlagPort {

    boolean isEnabled(String featureName);
}
