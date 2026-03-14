package com.example.contextengine.infrastructure.adapter.in.rest;

import com.example.contextengine.domain.port.in.ManageFeaturesUseCase;
import com.example.contextengine.domain.port.in.ManageFeaturesUseCase.FeatureView;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/features")
public class FeatureFlagController {

    private final ManageFeaturesUseCase manageFeaturesUseCase;

    public FeatureFlagController(ManageFeaturesUseCase manageFeaturesUseCase) {
        this.manageFeaturesUseCase = manageFeaturesUseCase;
    }

    @GetMapping
    public List<FeatureView> list() {
        return manageFeaturesUseCase.listAll();
    }

    @PatchMapping("/{featureName}/toggle")
    public FeatureView toggle(@PathVariable String featureName,
                              @RequestParam boolean enabled) {
        return manageFeaturesUseCase.toggle(featureName, enabled);
    }

    @PatchMapping("/{featureName}/config")
    public FeatureView updateConfig(@PathVariable String featureName,
                                    @RequestBody String configJson) {
        return manageFeaturesUseCase.updateConfig(featureName, configJson);
    }
}
