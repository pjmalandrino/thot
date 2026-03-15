package com.example.contextengine.application.support;

import com.example.contextengine.domain.model.ConversationMessage;
import com.example.contextengine.domain.model.SearchResult;
import com.example.contextengine.domain.port.out.LlmPort;
import com.example.contextengine.domain.port.out.WebSearchPort;
import com.example.contextengine.infrastructure.config.ContextEngineProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResearchPhaseExecutorTest {

    @Mock private LlmPort llmPort;
    @Mock private WebSearchPort webSearchPort;

    private ContextEngineProperties properties;
    private SseEmitterHelper sseHelper;
    private ResearchPhaseExecutor executor;

    @BeforeEach
    void setUp() {
        sseHelper = new SseEmitterHelper();
        properties = new ContextEngineProperties();
        properties.setMaxContextTokens(8192);
        executor = new ResearchPhaseExecutor(llmPort, webSearchPort, properties, sseHelper);
    }

    // ── generateResearchPlan() ───────────────────────────────────────────────

    @Nested
    @DisplayName("generateResearchPlan()")
    class GenerateResearchPlan {

        @Test
        @DisplayName("parse les sous-questions depuis un JSON valide")
        void validJson() {
            when(llmPort.analyze(anyString(), anyString()))
                    .thenReturn("{\"subQuestions\":[\"q1\",\"q2\",\"q3\"]}");

            List<String> plan = executor.generateResearchPlan("ma question");

            assertThat(plan).containsExactly("q1", "q2", "q3");
        }

        @Test
        @DisplayName("gere le JSON entoure de markdown")
        void jsonInMarkdown() {
            when(llmPort.analyze(anyString(), anyString()))
                    .thenReturn("Voici:\n```json\n{\"subQuestions\":[\"q1\"]}\n```");

            List<String> plan = executor.generateResearchPlan("question");

            assertThat(plan).containsExactly("q1");
        }

        @Test
        @DisplayName("fallback sur la query originale si JSON invalide")
        void invalidJson() {
            when(llmPort.analyze(anyString(), anyString()))
                    .thenReturn("Ceci n'est pas du JSON");

            List<String> plan = executor.generateResearchPlan("ma question");

            assertThat(plan).containsExactly("ma question");
        }

        @Test
        @DisplayName("fallback si subQuestions est vide")
        void emptySubQuestions() {
            when(llmPort.analyze(anyString(), anyString()))
                    .thenReturn("{\"subQuestions\":[]}");

            List<String> plan = executor.generateResearchPlan("ma question");

            assertThat(plan).containsExactly("ma question");
        }

        @Test
        @DisplayName("fallback si le LLM lance une exception")
        void llmThrows() {
            when(llmPort.analyze(anyString(), anyString()))
                    .thenThrow(new RuntimeException("LLM timeout"));

            List<String> plan = executor.generateResearchPlan("ma question");

            assertThat(plan).containsExactly("ma question");
        }

        @Test
        @DisplayName("filtre les sous-questions vides/blank")
        void filtersBlankSubQuestions() {
            when(llmPort.analyze(anyString(), anyString()))
                    .thenReturn("{\"subQuestions\":[\"q1\",\"  \",\"\",\"q2\"]}");

            List<String> plan = executor.generateResearchPlan("question");

            assertThat(plan).containsExactly("q1", "q2");
        }
    }

    // ── executeSubQuery() ────────────────────────────────────────────────────

    @Nested
    @DisplayName("executeSubQuery()")
    class ExecuteSubQuery {

        @Test
        @DisplayName("retourne une liste vide si les resultats sont null")
        void nullResults() {
            when(webSearchPort.searchAndExtract("query")).thenReturn(null);

            List<SearchResult> results = executor.executeSubQuery("query", 0);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("retourne une liste vide si les resultats sont vides")
        void emptyResults() {
            when(webSearchPort.searchAndExtract("query")).thenReturn(List.of());

            List<SearchResult> results = executor.executeSubQuery("query", 0);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("renumerote les citations avec l'offset")
        void renumbersCitations() {
            when(webSearchPort.searchAndExtract("java spring"))
                    .thenReturn(List.of(
                            new SearchResult("[1]", "https://a.com", "Spring Guide",
                                    "Spring Boot java application framework tutorial"),
                            new SearchResult("[2]", "https://b.com", "Java Docs",
                                    "Java Spring documentation and examples")));

            List<SearchResult> results = executor.executeSubQuery("java spring", 5);

            assertThat(results).isNotEmpty();
            // Les citations doivent commencer a offset+1
            assertThat(results.get(0).citationId()).isEqualTo("[6]");
        }

        @Test
        @DisplayName("garde les originaux si tout est filtre par pertinence")
        void keepsOriginalsWhenAllFiltered() {
            when(webSearchPort.searchAndExtract("xyz123"))
                    .thenReturn(List.of(
                            new SearchResult("[1]", "https://a.com", "Unrelated",
                                    "Nothing matching at all")));

            List<SearchResult> results = executor.executeSubQuery("xyz123", 0);

            // When relevance filter removes all, originals are kept
            assertThat(results).hasSize(1);
            assertThat(results.get(0).citationId()).isEqualTo("[1]");
        }
    }

    // ── deduplicateByUrl() ───────────────────────────────────────────────────

    @Nested
    @DisplayName("deduplicateByUrl()")
    class DeduplicateByUrl {

        @Test
        @DisplayName("retourne une liste vide pour une entree vide")
        void emptyInput() {
            assertThat(executor.deduplicateByUrl(List.of())).isEmpty();
        }

        @Test
        @DisplayName("garde tous les resultats sans doublons")
        void noDuplicates() {
            List<SearchResult> input = List.of(
                    new SearchResult("[1]", "https://a.com", "A", "text"),
                    new SearchResult("[2]", "https://b.com", "B", "text"));

            List<SearchResult> result = executor.deduplicateByUrl(input);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).citationId()).isEqualTo("[1]");
            assertThat(result.get(1).citationId()).isEqualTo("[2]");
        }

        @Test
        @DisplayName("deduplique par URL et renumerote")
        void deduplicatesAndRenumbers() {
            List<SearchResult> input = List.of(
                    new SearchResult("[1]", "https://a.com", "A", "text"),
                    new SearchResult("[2]", "https://a.com", "A duplicate", "text2"),
                    new SearchResult("[3]", "https://b.com", "B", "text"));

            List<SearchResult> result = executor.deduplicateByUrl(input);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).sourceUrl()).isEqualTo("https://a.com");
            assertThat(result.get(0).sourceTitle()).isEqualTo("A"); // first occurrence kept
            assertThat(result.get(0).citationId()).isEqualTo("[1]");
            assertThat(result.get(1).sourceUrl()).isEqualTo("https://b.com");
            assertThat(result.get(1).citationId()).isEqualTo("[2]");
        }
    }

    // ── applyContextBudget() ─────────────────────────────────────────────────

    @Nested
    @DisplayName("applyContextBudget()")
    class ApplyContextBudget {

        @Test
        @DisplayName("retourne null si webSearchContext est null")
        void nullWebContext() {
            String result = executor.applyContextBudget("prompt", List.of(), null, null);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("retourne le webContext intact si dans le budget")
        void withinBudget() {
            properties.setMaxContextTokens(100_000);
            String webCtx = "short web context";

            String result = executor.applyContextBudget("prompt", List.of(), null, webCtx);

            assertThat(result).isEqualTo(webCtx);
        }

        @Test
        @DisplayName("tronque le webContext si hors budget")
        void overBudget() {
            properties.setMaxContextTokens(10); // 10 tokens = ~40 chars total max
            String longWeb = "x".repeat(200);

            String result = executor.applyContextBudget("prompt", List.of(), null, longWeb);

            assertThat(result).contains("[...tronque]");
            assertThat(result.length()).isLessThan(longWeb.length());
        }
    }

    // ── buildPlanThinking() ──────────────────────────────────────────────────

    @Nested
    @DisplayName("buildPlanThinking()")
    class BuildPlanThinking {

        @Test
        @DisplayName("formate le plan de recherche")
        void formatsCorrectly() {
            String thinking = executor.buildPlanThinking(List.of("Q1", "Q2"));

            assertThat(thinking).contains("Plan de recherche");
            assertThat(thinking).contains("2 axes");
            assertThat(thinking).contains("1. Q1");
            assertThat(thinking).contains("2. Q2");
        }
    }

    // ── buildSubQueryThinking() ──────────────────────────────────────────────

    @Nested
    @DisplayName("buildSubQueryThinking()")
    class BuildSubQueryThinking {

        @Test
        @DisplayName("formate avec resultats")
        void withResults() {
            List<SearchResult> results = List.of(
                    new SearchResult("[1]", "https://a.com", "Title", "Some text content"));

            String thinking = executor.buildSubQueryThinking("my query", 0, 3, results);

            assertThat(thinking).contains("[1/3]");
            assertThat(thinking).contains("my query");
            assertThat(thinking).contains("1 source(s)");
            assertThat(thinking).contains("Title");
        }

        @Test
        @DisplayName("formate sans resultats")
        void noResults() {
            String thinking = executor.buildSubQueryThinking("query", 1, 2, List.of());

            assertThat(thinking).contains("[2/2]");
            assertThat(thinking).contains("Aucune source");
        }
    }
}
