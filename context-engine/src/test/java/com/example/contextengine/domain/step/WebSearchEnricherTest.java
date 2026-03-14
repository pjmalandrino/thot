package com.example.contextengine.domain.step;

import com.example.contextengine.domain.model.SearchResult;
import com.example.contextengine.domain.model.StepResult;
import com.example.contextengine.domain.pipeline.PipelineContext;
import com.example.contextengine.domain.port.out.LlmPort;
import com.example.contextengine.domain.port.out.WebSearchPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSearchEnricherTest {

    @Mock private LlmPort llmPort;
    @Mock private WebSearchPort webSearchPort;

    private final WebSearchEnricher enricher = new WebSearchEnricher();

    private PipelineContext context(String prompt, boolean webSearchRequested) {
        return new PipelineContext(prompt, null, null, webSearchRequested, llmPort, webSearchPort);
    }

    @Test
    @DisplayName("skip si web search non demande")
    void skipsWhenNotRequested() {
        PipelineContext ctx = context("Test", false);
        StepResult result = enricher.execute(ctx);

        assertThat(result.shouldContinue()).isTrue();
        assertThat(ctx.getWebSearchResults()).isEmpty();
        verifyNoInteractions(webSearchPort);
    }

    @Test
    @DisplayName("enrichit le contexte avec les resultats de recherche")
    void enrichesContextWithSearchResults() {
        List<SearchResult> results = List.of(
                new SearchResult("1", "https://example.com", "Example", "Some content"));

        when(webSearchPort.searchAndExtract(anyString())).thenReturn(results);
        when(webSearchPort.buildContextPrompt(results)).thenReturn("Web context prompt");

        PipelineContext ctx = context("Java performance tips", true);
        StepResult result = enricher.execute(ctx);

        assertThat(result.shouldContinue()).isTrue();
        assertThat(ctx.getWebSearchResults()).hasSize(1);
        assertThat(ctx.getWebSearchContext()).isEqualTo("Web context prompt");
    }

    @Test
    @DisplayName("utilise la query reecrite si disponible")
    void usesRewrittenQuery() {
        when(webSearchPort.searchAndExtract("Rewritten query")).thenReturn(List.of());
        when(webSearchPort.buildContextPrompt(List.of())).thenReturn("");

        PipelineContext ctx = context("Original query", true);
        ctx.setRewrittenQuery("Rewritten query");
        enricher.execute(ctx);

        verify(webSearchPort).searchAndExtract("Rewritten query");
    }

    @Test
    @DisplayName("fail-open sur exception WebSearchPort")
    void webSearchExceptionFailsOpen() {
        when(webSearchPort.searchAndExtract(anyString()))
                .thenThrow(new RuntimeException("Tavily down"));

        PipelineContext ctx = context("Test", true);
        StepResult result = enricher.execute(ctx);

        assertThat(result.shouldContinue()).isTrue();
        assertThat(ctx.getWebSearchResults()).isEmpty();
    }

    @Test
    @DisplayName("featureName retourne web-search")
    void featureNameIsCorrect() {
        assertThat(enricher.featureName()).isEqualTo("web-search");
    }
}
