package com.example.contextengine.domain.pipeline;

import com.example.contextengine.domain.model.ContextAnalysis;
import com.example.contextengine.domain.model.StepResult;
import com.example.contextengine.domain.port.out.FeatureFlagPort;
import com.example.contextengine.domain.port.out.LlmPort;
import com.example.contextengine.domain.port.out.WebSearchPort;
import com.example.contextengine.infrastructure.config.ContextEngineProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContextPipelineTest {

    @Mock private FeatureFlagPort featureFlagPort;
    @Mock private LlmPort llmPort;
    @Mock private WebSearchPort webSearchPort;

    private ContextEngineProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ContextEngineProperties();
        properties.setMinConfidence(0.75);
        properties.setFallbackPolicy(ContextEngineProperties.FallbackPolicy.CONTINUE);
    }

    private PipelineContext context(String prompt) {
        return new PipelineContext(prompt, null, null, false, llmPort, webSearchPort);
    }

    @Test
    @DisplayName("execute tous les steps actives en sequence")
    void executesAllEnabledSteps() {
        ContextStep step1 = mock(ContextStep.class);
        when(step1.featureName()).thenReturn("step-1");
        when(step1.execute(any())).thenReturn(StepResult.continueProcessing());

        ContextStep step2 = mock(ContextStep.class);
        when(step2.featureName()).thenReturn("step-2");
        when(step2.execute(any())).thenReturn(StepResult.continueProcessing());

        when(featureFlagPort.isEnabled("step-1")).thenReturn(true);
        when(featureFlagPort.isEnabled("step-2")).thenReturn(true);

        ContextPipeline pipeline = new ContextPipeline(List.of(step1, step2), featureFlagPort, properties);
        ContextAnalysis result = pipeline.run(context("Test"));

        assertThat(result.isContinue()).isTrue();
        verify(step1).execute(any());
        verify(step2).execute(any());
    }

    @Test
    @DisplayName("skip les steps desactives par feature flag")
    void skipsDisabledSteps() {
        ContextStep step1 = mock(ContextStep.class);
        when(step1.featureName()).thenReturn("enabled-step");
        when(step1.execute(any())).thenReturn(StepResult.continueProcessing());

        ContextStep step2 = mock(ContextStep.class);
        when(step2.featureName()).thenReturn("disabled-step");

        when(featureFlagPort.isEnabled("enabled-step")).thenReturn(true);
        when(featureFlagPort.isEnabled("disabled-step")).thenReturn(false);

        ContextPipeline pipeline = new ContextPipeline(List.of(step1, step2), featureFlagPort, properties);
        ContextAnalysis result = pipeline.run(context("Test"));

        assertThat(result.isContinue()).isTrue();
        verify(step1).execute(any());
        verify(step2, never()).execute(any());
    }

    @Test
    @DisplayName("interrompt le pipeline quand un step retourne INTERRUPT avec haute confiance")
    void interruptsOnHighConfidenceInterrupt() {
        ContextStep gate = mock(ContextStep.class);
        when(gate.featureName()).thenReturn("gate");
        when(gate.execute(any())).thenReturn(
                StepResult.interrupt("clarification_needed", "Preciser SVP",
                        List.of("Option A", "Option B"), 0.9));

        ContextStep next = mock(ContextStep.class);
        when(next.featureName()).thenReturn("next");

        when(featureFlagPort.isEnabled("gate")).thenReturn(true);

        ContextPipeline pipeline = new ContextPipeline(List.of(gate, next), featureFlagPort, properties);
        ContextAnalysis result = pipeline.run(context("Test"));

        assertThat(result.isContinue()).isFalse();
        assertThat(result.getStatus()).isEqualTo("clarification_needed");
        assertThat(result.getClarificationMessage()).isEqualTo("Preciser SVP");
        assertThat(result.getSuggestions()).containsExactly("Option A", "Option B");
        verify(next, never()).execute(any());
    }

    @Test
    @DisplayName("fail-open sur INTERRUPT avec confiance basse (policy CONTINUE)")
    void failsOpenOnLowConfidenceInterrupt() {
        ContextStep gate = mock(ContextStep.class);
        when(gate.featureName()).thenReturn("gate");
        when(gate.execute(any())).thenReturn(
                StepResult.interrupt("clarification_needed", "Maybe vague", List.of(), 0.5));

        ContextStep next = mock(ContextStep.class);
        when(next.featureName()).thenReturn("next");
        when(next.execute(any())).thenReturn(StepResult.continueProcessing());

        when(featureFlagPort.isEnabled("gate")).thenReturn(true);
        when(featureFlagPort.isEnabled("next")).thenReturn(true);

        ContextPipeline pipeline = new ContextPipeline(List.of(gate, next), featureFlagPort, properties);
        ContextAnalysis result = pipeline.run(context("Test"));

        assertThat(result.isContinue()).isTrue();
        verify(next).execute(any());
    }

    @Test
    @DisplayName("bloque sur INTERRUPT avec confiance basse si policy BLOCK")
    void blocksOnLowConfidenceWhenPolicyBlock() {
        properties.setFallbackPolicy(ContextEngineProperties.FallbackPolicy.BLOCK);

        ContextStep gate = mock(ContextStep.class);
        when(gate.featureName()).thenReturn("gate");
        when(gate.execute(any())).thenReturn(
                StepResult.interrupt("clarification_needed", "Maybe vague", List.of(), 0.5));

        when(featureFlagPort.isEnabled("gate")).thenReturn(true);

        ContextPipeline pipeline = new ContextPipeline(List.of(gate), featureFlagPort, properties);
        ContextAnalysis result = pipeline.run(context("Test"));

        assertThat(result.isContinue()).isFalse();
        assertThat(result.getClarificationMessage()).isEqualTo("Maybe vague");
    }

    @Test
    @DisplayName("retourne les enrichissements accumules dans le contexte")
    void returnsAccumulatedEnrichments() {
        ContextStep enricher = mock(ContextStep.class);
        when(enricher.featureName()).thenReturn("enricher");
        when(enricher.execute(any())).thenAnswer(invocation -> {
            PipelineContext ctx = invocation.getArgument(0);
            ctx.setRewrittenQuery("Rewritten");
            return StepResult.continueProcessing();
        });

        when(featureFlagPort.isEnabled("enricher")).thenReturn(true);

        ContextPipeline pipeline = new ContextPipeline(List.of(enricher), featureFlagPort, properties);
        ContextAnalysis result = pipeline.run(context("Original"));

        assertThat(result.isContinue()).isTrue();
        assertThat(result.getRewrittenQuery()).isEqualTo("Rewritten");
    }
}
