package com.example.contextengine.domain.step;

import com.example.contextengine.domain.model.ConversationMessage;
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

@ExtendWith(MockitoExtension.class)
class ContextBudgetManagerTest {

    @Mock private LlmPort llmPort;
    @Mock private WebSearchPort webSearchPort;

    private final ContextBudgetManager manager = new ContextBudgetManager();

    private PipelineContext context(String prompt, String docContext, String webContext, int maxTokens) {
        PipelineContext ctx = new PipelineContext(prompt, null, docContext, false, llmPort, webSearchPort, maxTokens);
        if (webContext != null) {
            ctx.setWebSearchContext(webContext);
        }
        return ctx;
    }

    @Test
    @DisplayName("calcule le budget tokens pour chaque section")
    void computesTokenBudget() {
        PipelineContext ctx = context("Hello world", "Document text here", "Web results here", 8192);
        StepResult result = manager.execute(ctx);

        assertThat(result.shouldContinue()).isTrue();
        assertThat(ctx.getTokenBudget()).containsKey("prompt");
        assertThat(ctx.getTokenBudget()).containsKey("documentContext");
        assertThat(ctx.getTokenBudget()).containsKey("webSearchContext");
        assertThat(ctx.getTokenBudget()).containsKey("maxContextTokens");
        assertThat(ctx.getTotalEstimatedTokens()).isPositive();
    }

    @Test
    @DisplayName("ne tronque pas si sous le budget")
    void doesNotTrimUnderBudget() {
        String doc = "Short doc";
        PipelineContext ctx = context("Hello", doc, "Web", 8192);
        manager.execute(ctx);

        assertThat(ctx.getDocumentContext()).isEqualTo(doc);
    }

    @Test
    @DisplayName("tronque le contexte web en premier si over budget")
    void trimsWebContextFirst() {
        String longWeb = "x".repeat(10000);
        PipelineContext ctx = context("Hello", "Short doc", longWeb, 100);
        manager.execute(ctx);

        assertThat(ctx.getWebSearchContext().length()).isLessThan(longWeb.length());
        assertThat(ctx.getTotalEstimatedTokens()).isLessThanOrEqualTo(100);
    }

    @Test
    @DisplayName("tronque les documents si web ne suffit pas")
    void trimsDocIfWebNotEnough() {
        String longDoc = "d".repeat(10000);
        PipelineContext ctx = context("Hello", longDoc, null, 50);
        manager.execute(ctx);

        assertThat(ctx.getTotalEstimatedTokens()).isLessThanOrEqualTo(50);
    }

    @Test
    @DisplayName("inclut l'historique dans le calcul")
    void includesHistoryInBudget() {
        PipelineContext ctx = new PipelineContext(
                "Hello",
                List.of(new ConversationMessage("user", "Previous question"),
                        new ConversationMessage("assistant", "Previous answer")),
                null, false, llmPort, webSearchPort, 8192);
        manager.execute(ctx);

        assertThat(ctx.getTokenBudget()).containsKey("conversationHistory");
        assertThat(ctx.getTokenBudget().get("conversationHistory")).isPositive();
    }

    @Test
    @DisplayName("estimateTokens utilise l'heuristique chars/4")
    void estimateTokensHeuristic() {
        assertThat(ContextBudgetManager.estimateTokens("test")).isEqualTo(1); // 4 chars / 4
        assertThat(ContextBudgetManager.estimateTokens("twelve chars")).isEqualTo(3); // 12 / 4
        assertThat(ContextBudgetManager.estimateTokens(null)).isEqualTo(0);
        assertThat(ContextBudgetManager.estimateTokens("")).isEqualTo(0);
    }

    @Test
    @DisplayName("featureName retourne context-budget")
    void featureNameIsCorrect() {
        assertThat(manager.featureName()).isEqualTo("context-budget");
    }
}
