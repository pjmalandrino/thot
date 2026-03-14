package com.example.contextengine.domain.step;

import com.example.contextengine.domain.model.StepResult;
import com.example.contextengine.domain.pipeline.PipelineContext;
import com.example.contextengine.domain.port.out.LlmPort;
import com.example.contextengine.domain.port.out.WebSearchPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoWebSearchTriggerTest {

    @Mock private LlmPort llmPort;
    @Mock private WebSearchPort webSearchPort;

    private final AutoWebSearchTrigger trigger = new AutoWebSearchTrigger();

    private PipelineContext context(String prompt, boolean webSearchRequested) {
        return new PipelineContext(prompt, null, null, webSearchRequested, llmPort, webSearchPort);
    }

    @Test
    @DisplayName("skip si web search deja demande par l'utilisateur")
    void skipsWhenAlreadyRequested() {
        PipelineContext ctx = context("Test", true);
        StepResult result = trigger.execute(ctx);

        assertThat(result.shouldContinue()).isTrue();
        assertThat(ctx.isAutoWebSearchTriggered()).isFalse();
        verifyNoInteractions(llmPort);
    }

    @Test
    @DisplayName("active la recherche web quand le LLM le recommande")
    void triggersWebSearchWhenNeeded() {
        when(llmPort.analyze(anyString(), anyString()))
                .thenReturn("{\"needsWebSearch\":true,\"reason\":\"Question sur un evenement recent\"}");

        PipelineContext ctx = context("Quels sont les resultats des elections 2025 ?", false);
        StepResult result = trigger.execute(ctx);

        assertThat(result.shouldContinue()).isTrue();
        assertThat(ctx.isWebSearchRequested()).isTrue();
        assertThat(ctx.isAutoWebSearchTriggered()).isTrue();
    }

    @Test
    @DisplayName("ne declenche pas la recherche pour une question stable")
    void doesNotTriggerForStableQuestion() {
        when(llmPort.analyze(anyString(), anyString()))
                .thenReturn("{\"needsWebSearch\":false,\"reason\":\"\"}");

        PipelineContext ctx = context("Explique le theoreme de Pythagore", false);
        StepResult result = trigger.execute(ctx);

        assertThat(result.shouldContinue()).isTrue();
        assertThat(ctx.isWebSearchRequested()).isFalse();
        assertThat(ctx.isAutoWebSearchTriggered()).isFalse();
    }

    @Test
    @DisplayName("fail-open sur exception LLM")
    void llmExceptionFailsOpen() {
        when(llmPort.analyze(anyString(), anyString()))
                .thenThrow(new RuntimeException("LLM down"));

        PipelineContext ctx = context("Test", false);
        StepResult result = trigger.execute(ctx);

        assertThat(result.shouldContinue()).isTrue();
        assertThat(ctx.isWebSearchRequested()).isFalse();
    }

    @Test
    @DisplayName("featureName retourne auto-web-search")
    void featureNameIsCorrect() {
        assertThat(trigger.featureName()).isEqualTo("auto-web-search");
    }
}
