package com.example.contextengine.domain.port.in;

import java.util.List;
import java.util.Map;

public interface ManageFeaturesUseCase {

    record FeatureView(Long id, String featureName, boolean enabled, String configJson, String description) {}

    List<FeatureView> listAll();

    FeatureView toggle(String featureName, boolean enabled);

    FeatureView updateConfig(String featureName, String configJson);
}
