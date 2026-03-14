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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSearchRelevanceFilterTest {

    @Mock private LlmPort llmPort;
    @Mock private WebSearchPort webSearchPort;

    private final WebSearchRelevanceFilter filter = new WebSearchRelevanceFilter();

    private PipelineContext context(String prompt) {
        return new PipelineContext(prompt, null, null, true, llmPort, webSearchPort);
    }

    @Test
    @DisplayName("skip si aucun resultat web")
    void skipsWhenNoResults() {
        PipelineContext ctx = context("Test");
        StepResult result = filter.execute(ctx);

        assertThat(result.shouldContinue()).isTrue();
    }

    @Test
    @DisplayName("filtre les resultats non pertinents")
    void filtersIrrelevantResults() {
        when(webSearchPort.buildContextPrompt(anyList())).thenReturn("filtered context");

        PipelineContext ctx = context("Java Spring Boot tutorial");
        List<SearchResult> results = new ArrayList<>(List.of(
                new SearchResult("[1]", "https://spring.io", "Spring Boot Guide",
                        "Spring Boot makes it easy to create Java applications"),
                new SearchResult("[2]", "https://cooking.com", "Best pasta recipes",
                        "How to cook perfect pasta with tomato sauce")));
        ctx.setWebSearchResults(results);

        StepResult result = filter.execute(ctx);

        assertThat(result.shouldContinue()).isTrue();
        assertThat(ctx.getWebSearchResults()).hasSize(1);
        assertThat(ctx.getWebSearchResults().get(0).sourceUrl()).isEqualTo("https://spring.io");
        assertThat(ctx.getWebSearchResults().get(0).citationId()).isEqualTo("[1]");
    }

    @Test
    @DisplayName("garde tous les resultats si tous pertinents")
    void keepsAllWhenAllRelevant() {
        PipelineContext ctx = context("Java performance optimization");
        List<SearchResult> results = new ArrayList<>(List.of(
                new SearchResult("[1]", "https://a.com", "Java Performance",
                        "Java performance tuning and optimization techniques"),
                new SearchResult("[2]", "https://b.com", "JVM Optimization",
                        "Optimize Java JVM performance with these tips")));
        ctx.setWebSearchResults(results);

        StepResult result = filter.execute(ctx);

        assertThat(result.shouldContinue()).isTrue();
        assertThat(ctx.getWebSearchResults()).hasSize(2);
    }

    @Test
    @DisplayName("garde les originaux si tout est filtre")
    void keepsOriginalsIfAllFiltered() {
        PipelineContext ctx = context("xyz123abc456");
        List<SearchResult> results = new ArrayList<>(List.of(
                new SearchResult("[1]", "https://a.com", "Unrelated", "Nothing matching")));
        ctx.setWebSearchResults(results);

        StepResult result = filter.execute(ctx);

        assertThat(result.shouldContinue()).isTrue();
        assertThat(ctx.getWebSearchResults()).hasSize(1); // kept originals
    }

    @Test
    @DisplayName("tokenize extrait les mots significatifs")
    void tokenizeExtractsSignificantWords() {
        Set<String> tokens = WebSearchRelevanceFilter.tokenize("C'est quoi Java et Python ?");
        assertThat(tokens).contains("java", "python", "quoi");
        assertThat(tokens).doesNotContain("et"); // too short (2 chars)
    }

    @Test
    @DisplayName("featureName retourne web-search-relevance")
    void featureNameIsCorrect() {
        assertThat(filter.featureName()).isEqualTo("web-search-relevance");
    }
}
