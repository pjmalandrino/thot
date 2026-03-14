package com.example.contextengine.application.service;

import com.example.contextengine.domain.port.in.ManageFeaturesUseCase.FeatureView;
import com.example.contextengine.infrastructure.adapter.out.persistence.FeatureFlagEntity;
import com.example.contextengine.infrastructure.adapter.out.persistence.FeatureFlagJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeatureFlagServiceTest {

    @Mock private FeatureFlagJpaRepository repository;
    @InjectMocks private FeatureFlagService service;

    private FeatureFlagEntity entity(String name, boolean enabled) {
        FeatureFlagEntity e = new FeatureFlagEntity();
        e.setId(1L);
        e.setFeatureName(name);
        e.setEnabled(enabled);
        e.setConfigJson("{}");
        e.setDescription("Test feature");
        return e;
    }

    @Test
    @DisplayName("listAll retourne toutes les features")
    void listAllReturnsAllFeatures() {
        when(repository.findAll()).thenReturn(List.of(
                entity("vagueness-detection", true),
                entity("query-rewriting", false)));

        List<FeatureView> views = service.listAll();

        assertThat(views).hasSize(2);
        assertThat(views.get(0).featureName()).isEqualTo("vagueness-detection");
        assertThat(views.get(0).enabled()).isTrue();
        assertThat(views.get(1).featureName()).isEqualTo("query-rewriting");
        assertThat(views.get(1).enabled()).isFalse();
    }

    @Test
    @DisplayName("toggle active/desactive une feature")
    void toggleFeature() {
        FeatureFlagEntity e = entity("query-rewriting", true);
        when(repository.findByFeatureName("query-rewriting")).thenReturn(Optional.of(e));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FeatureView view = service.toggle("query-rewriting", false);

        assertThat(view.featureName()).isEqualTo("query-rewriting");
        assertThat(view.enabled()).isFalse();
    }

    @Test
    @DisplayName("toggle leve une exception si feature inconnue")
    void toggleUnknownFeatureThrows() {
        when(repository.findByFeatureName("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggle("unknown", true))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Feature not found");
    }

    @Test
    @DisplayName("updateConfig met a jour la config JSON")
    void updateConfig() {
        FeatureFlagEntity e = entity("web-search", true);
        when(repository.findByFeatureName("web-search")).thenReturn(Optional.of(e));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FeatureView view = service.updateConfig("web-search", "{\"maxResults\":5}");

        assertThat(view.configJson()).isEqualTo("{\"maxResults\":5}");
    }

    @Test
    @DisplayName("updateConfig leve une exception si feature inconnue")
    void updateConfigUnknownFeatureThrows() {
        when(repository.findByFeatureName("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateConfig("unknown", "{}"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Feature not found");
    }
}
