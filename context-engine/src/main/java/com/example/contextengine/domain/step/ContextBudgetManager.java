package com.example.contextengine.domain.step;

import com.example.contextengine.domain.model.StepResult;
import com.example.contextengine.domain.pipeline.ContextStep;
import com.example.contextengine.domain.pipeline.PipelineContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(100)
public class ContextBudgetManager implements ContextStep {

    private static final Logger log = LoggerFactory.getLogger(ContextBudgetManager.class);

    @Override
    public String featureName() {
        return "context-budget";
    }

    @Override
    public StepResult execute(PipelineContext context) {
        try {
            Map<String, Integer> budget = new LinkedHashMap<>();
            int total = 0;

            int promptTokens = estimateTokens(context.getPrompt());
            budget.put("prompt", promptTokens);
            total += promptTokens;

            int historyTokens = context.getConversationHistory().stream()
                    .mapToInt(m -> estimateTokens(m.content()))
                    .sum();
            budget.put("conversationHistory", historyTokens);
            total += historyTokens;

            if (context.getDocumentContext() != null && !context.getDocumentContext().isBlank()) {
                int docTokens = estimateTokens(context.getDocumentContext());
                budget.put("documentContext", docTokens);
                total += docTokens;
            }

            if (context.getWebSearchContext() != null && !context.getWebSearchContext().isBlank()) {
                int webTokens = estimateTokens(context.getWebSearchContext());
                budget.put("webSearchContext", webTokens);
                total += webTokens;
            }

            int maxTokens = context.getMaxContextTokens();
            budget.put("maxContextTokens", maxTokens);

            log.info("[BUDGET] Token estimation: {} total / {} max", total, maxTokens);

            if (total > maxTokens) {
                log.warn("[BUDGET] Over budget by {} tokens, trimming", total - maxTokens);
                total = trimContext(context, budget, total, maxTokens);
            }

            context.setTokenBudget(budget);
            context.setTotalEstimatedTokens(total);

            return StepResult.continueProcessing();
        } catch (Exception e) {
            log.warn("[BUDGET] Error, failing open: {}", e.getMessage());
            return StepResult.continueProcessing();
        }
    }

    private int trimContext(PipelineContext context, Map<String, Integer> budget,
                            int total, int maxTokens) {
        // Priority: trim web search first, then documents
        // Never trim the user prompt or conversation history

        if (total > maxTokens && context.getWebSearchContext() != null) {
            int webTokens = budget.getOrDefault("webSearchContext", 0);
            int allowedWeb = Math.max(0, webTokens - (total - maxTokens));
            if (allowedWeb < webTokens) {
                String trimmed = trimToTokens(context.getWebSearchContext(), allowedWeb);
                context.setWebSearchContext(trimmed);
                int newWebTokens = estimateTokens(trimmed);
                total = total - webTokens + newWebTokens;
                budget.put("webSearchContext", newWebTokens);
                log.info("[BUDGET] Trimmed webSearchContext: {} -> {} tokens", webTokens, newWebTokens);
            }
        }

        if (total > maxTokens && context.getDocumentContext() != null) {
            int docTokens = budget.getOrDefault("documentContext", 0);
            int allowedDoc = Math.max(0, docTokens - (total - maxTokens));
            String trimmed = trimToTokens(context.getDocumentContext(), allowedDoc);
            int newDocTokens = estimateTokens(trimmed);
            total = total - docTokens + newDocTokens;
            budget.put("documentContext", newDocTokens);
            log.info("[BUDGET] Trimmed documentContext: {} -> {} tokens", docTokens, newDocTokens);
        }

        return total;
    }

    public static int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        // Heuristic: ~4 characters per token for mixed French/English text
        return (int) Math.ceil(text.length() / 4.0);
    }

    private static final String TRIM_SUFFIX = "\n[...tronque]";

    private String trimToTokens(String text, int maxTokens) {
        if (maxTokens <= 0) return "";
        int maxChars = maxTokens * 4;
        if (text.length() <= maxChars) return text;
        int suffixLen = TRIM_SUFFIX.length();
        int cutAt = Math.max(0, maxChars - suffixLen);
        return text.substring(0, cutAt) + TRIM_SUFFIX;
    }
}
