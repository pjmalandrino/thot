package com.example.contextengine.domain.pipeline;

import com.example.contextengine.domain.model.ConversationMessage;
import com.example.contextengine.domain.model.SearchResult;
import com.example.contextengine.domain.port.out.LlmPort;
import com.example.contextengine.domain.port.out.WebSearchPort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PipelineContext {

    private final String prompt;
    private final List<ConversationMessage> conversationHistory;
    private final String documentContext;
    private final LlmPort llmPort;
    private final WebSearchPort webSearchPort;
    private final int maxContextTokens;

    // mutable — can be set by AutoWebSearchTrigger
    private boolean webSearchRequested;
    private boolean autoWebSearchTriggered;

    // enrichments accumulated by steps
    private String rewrittenQuery;
    private List<SearchResult> webSearchResults = new ArrayList<>();
    private String webSearchContext;

    // token budget (populated by ContextBudgetManager)
    private Map<String, Integer> tokenBudget = new LinkedHashMap<>();
    private int totalEstimatedTokens;

    public PipelineContext(String prompt,
                           List<ConversationMessage> conversationHistory,
                           String documentContext,
                           boolean webSearchRequested,
                           LlmPort llmPort,
                           WebSearchPort webSearchPort) {
        this(prompt, conversationHistory, documentContext, webSearchRequested, llmPort, webSearchPort, 8192);
    }

    public PipelineContext(String prompt,
                           List<ConversationMessage> conversationHistory,
                           String documentContext,
                           boolean webSearchRequested,
                           LlmPort llmPort,
                           WebSearchPort webSearchPort,
                           int maxContextTokens) {
        this.prompt = prompt;
        this.conversationHistory = conversationHistory != null ? conversationHistory : List.of();
        this.documentContext = documentContext;
        this.webSearchRequested = webSearchRequested;
        this.llmPort = llmPort;
        this.webSearchPort = webSearchPort;
        this.maxContextTokens = maxContextTokens;
    }

    public String getEffectiveQuery() {
        return rewrittenQuery != null ? rewrittenQuery : prompt;
    }

    public String getPrompt() { return prompt; }
    public List<ConversationMessage> getConversationHistory() { return conversationHistory; }
    public String getDocumentContext() { return documentContext; }
    public LlmPort getLlmPort() { return llmPort; }
    public WebSearchPort getWebSearchPort() { return webSearchPort; }
    public int getMaxContextTokens() { return maxContextTokens; }

    public boolean isWebSearchRequested() { return webSearchRequested; }
    public void setWebSearchRequested(boolean webSearchRequested) { this.webSearchRequested = webSearchRequested; }

    public boolean isAutoWebSearchTriggered() { return autoWebSearchTriggered; }
    public void setAutoWebSearchTriggered(boolean autoWebSearchTriggered) { this.autoWebSearchTriggered = autoWebSearchTriggered; }

    public String getRewrittenQuery() { return rewrittenQuery; }
    public void setRewrittenQuery(String rewrittenQuery) { this.rewrittenQuery = rewrittenQuery; }

    public List<SearchResult> getWebSearchResults() { return webSearchResults; }
    public void setWebSearchResults(List<SearchResult> webSearchResults) { this.webSearchResults = webSearchResults; }

    public String getWebSearchContext() { return webSearchContext; }
    public void setWebSearchContext(String webSearchContext) { this.webSearchContext = webSearchContext; }

    public Map<String, Integer> getTokenBudget() { return tokenBudget; }
    public void setTokenBudget(Map<String, Integer> tokenBudget) { this.tokenBudget = tokenBudget; }

    public int getTotalEstimatedTokens() { return totalEstimatedTokens; }
    public void setTotalEstimatedTokens(int totalEstimatedTokens) { this.totalEstimatedTokens = totalEstimatedTokens; }
}
