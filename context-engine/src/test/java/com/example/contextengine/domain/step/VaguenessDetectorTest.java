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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VaguenessDetectorTest {

    @Mock private LlmPort llmPort;
    @Mock private WebSearchPort webSearchPort;

    private final VaguenessDetector detector = new VaguenessDetector();

    private PipelineContext context(String prompt) {
        return new PipelineContext(prompt, null, null, false, llmPort, webSearchPort);
    }

    @Test
    @DisplayName("retourne CONTINUE pour un prompt clair")
    void clearPromptContinues() {
        when(llmPort.analyze(anyString(), anyString()))
                .thenReturn("{\"vague\":false,\"confidence\":0.95,\"reason\":\"\",\"suggestions\":[]}");

        StepResult result = detector.execute(context("C'est quoi Java ?"));

        assertThat(result.shouldContinue()).isTrue();
    }

    @Test
    @DisplayName("retourne INTERRUPT pour un prompt vague")
    void vaguePromptInterrupts() {
        when(llmPort.analyze(anyString(), anyString()))
                .thenReturn("{\"vague\":true,\"confidence\":0.9,\"reason\":\"Aucun sujet\",\"suggestions\":[\"Aide-moi avec quoi ?\"]}");

        StepResult result = detector.execute(context("Aide-moi"));

        assertThat(result.shouldContinue()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Aucun sujet");
        assertThat(result.getSuggestions()).containsExactly("Aide-moi avec quoi ?");
        assertThat(result.getConfidence()).isEqualTo(0.9);
    }

    @Test
    @DisplayName("gere les code fences markdown dans la reponse LLM")
    void handlesMarkdownCodeFences() {
        when(llmPort.analyze(anyString(), anyString()))
                .thenReturn("```json\n{\"vague\":false,\"confidence\":0.95,\"reason\":\"\",\"suggestions\":[]}\n```");

        StepResult result = detector.execute(context("Bonjour"));

        assertThat(result.shouldContinue()).isTrue();
    }

    @Test
    @DisplayName("fail-open sur JSON malforme")
    void malformedJsonFailsOpen() {
        when(llmPort.analyze(anyString(), anyString()))
                .thenReturn("ceci n'est pas du JSON");

        StepResult result = detector.execute(context("Test"));

        assertThat(result.shouldContinue()).isTrue();
    }

    @Test
    @DisplayName("fail-open sur exception LLM")
    void llmExceptionFailsOpen() {
        when(llmPort.analyze(anyString(), anyString()))
                .thenThrow(new RuntimeException("LLM unreachable"));

        StepResult result = detector.execute(context("Test"));

        assertThat(result.shouldContinue()).isTrue();
    }

    @Test
    @DisplayName("featureName retourne vagueness-detection")
    void featureNameIsCorrect() {
        assertThat(detector.featureName()).isEqualTo("vagueness-detection");
    }
}
