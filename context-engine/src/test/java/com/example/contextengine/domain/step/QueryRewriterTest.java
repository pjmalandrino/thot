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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryRewriterTest {

    @Mock private LlmPort llmPort;
    @Mock private WebSearchPort webSearchPort;

    private final QueryRewriter rewriter = new QueryRewriter();

    private PipelineContext context(String prompt) {
        return new PipelineContext(prompt, null, null, false, llmPort, webSearchPort);
    }

    private PipelineContext contextWithHistory(String prompt, List<ConversationMessage> history) {
        return new PipelineContext(prompt, history, null, false, llmPort, webSearchPort);
    }

    @Test
    @DisplayName("ne reecrit pas un prompt deja clair")
    void clearPromptNotRewritten() {
        when(llmPort.analyze(anyString(), anyString()))
                .thenReturn("{\"rewritten\":\"C'est quoi Java ?\",\"changed\":false,\"reason\":\"\"}");

        PipelineContext ctx = context("C'est quoi Java ?");
        StepResult result = rewriter.execute(ctx);

        assertThat(result.shouldContinue()).isTrue();
        assertThat(ctx.getRewrittenQuery()).isNull();
    }

    @Test
    @DisplayName("reecrit un prompt complexe")
    void complexPromptRewritten() {
        when(llmPort.analyze(anyString(), anyString()))
                .thenReturn("{\"rewritten\":\"Quels sont les avantages de Docker pour le deploiement de microservices ?\",\"changed\":true,\"reason\":\"Reformulation plus precise\"}");

        PipelineContext ctx = context("docker et microservices");
        StepResult result = rewriter.execute(ctx);

        assertThat(result.shouldContinue()).isTrue();
        assertThat(ctx.getRewrittenQuery())
                .isEqualTo("Quels sont les avantages de Docker pour le deploiement de microservices ?");
    }

    @Test
    @DisplayName("utilise l'historique de conversation pour resoudre les pronoms")
    void usesConversationHistory() {
        when(llmPort.analyze(anyString(), anyString()))
                .thenReturn("{\"rewritten\":\"Comment configurer Spring Security ?\",\"changed\":true,\"reason\":\"Resolution de pronom\"}");

        List<ConversationMessage> history = List.of(
                new ConversationMessage("user", "Parle-moi de Spring Security"),
                new ConversationMessage("assistant", "Spring Security est un framework..."));

        PipelineContext ctx = contextWithHistory("Comment le configurer ?", history);
        StepResult result = rewriter.execute(ctx);

        assertThat(result.shouldContinue()).isTrue();
        assertThat(ctx.getRewrittenQuery()).isEqualTo("Comment configurer Spring Security ?");
    }

    @Test
    @DisplayName("fail-open sur exception LLM")
    void llmExceptionFailsOpen() {
        when(llmPort.analyze(anyString(), anyString()))
                .thenThrow(new RuntimeException("LLM unreachable"));

        PipelineContext ctx = context("Test");
        StepResult result = rewriter.execute(ctx);

        assertThat(result.shouldContinue()).isTrue();
        assertThat(ctx.getRewrittenQuery()).isNull();
    }

    @Test
    @DisplayName("featureName retourne query-rewriting")
    void featureNameIsCorrect() {
        assertThat(rewriter.featureName()).isEqualTo("query-rewriting");
    }
}
