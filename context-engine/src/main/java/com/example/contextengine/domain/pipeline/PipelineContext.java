package com.example.contextengine.domain.pipeline;

import com.example.contextengine.domain.model.ConversationMessage;
import com.example.contextengine.domain.model.SearchResult;
import com.example.contextengine.domain.port.out.LlmPort;
import com.example.contextengine.domain.port.out.WebSearchPort;

import java.util.ArrayList;
import java.util.List;

public class PipelineContext {

    private final String prompt;
    private final List<ConversationMessage> conversationHistory;
    private final String documentContext;
    private final boolean webSearchRequested;
    private final LlmPort llmPort;
    private final WebSearchPort webSearchPort;

    private String rewrittenQuery;
    private List<SearchResult> webSearchResults = new ArrayList<>();
    private String webSearchContext;

    public PipelineContext(String prompt,
                           List<ConversationMessage> conversationHistory,
                           String documentContext,
                           boolean webSearchRequested,
                           LlmPort llmPort,
                           WebSearchPort webSearchPort) {
        this.prompt = prompt;
        this.conversationHistory = conversationHistory != null ? conversationHistory : List.of();
        this.documentContext = documentContext;
        this.webSearchRequested = webSearchRequested;
        this.llmPort = llmPort;
        this.webSearchPort = webSearchPort;
    }

    public String getEffectiveQuery() {
        return rewrittenQuery != null ? rewrittenQuery : prompt;
    }

    public String getPrompt() { return prompt; }
    public List<ConversationMessage> getConversationHistory() { return conversationHistory; }
    public String getDocumentContext() { return documentContext; }
    public boolean isWebSearchRequested() { return webSearchRequested; }
    public LlmPort getLlmPort() { return llmPort; }
    public WebSearchPort getWebSearchPort() { return webSearchPort; }

    public String getRewrittenQuery() { return rewrittenQuery; }
    public void setRewrittenQuery(String rewrittenQuery) { this.rewrittenQuery = rewrittenQuery; }

    public List<SearchResult> getWebSearchResults() { return webSearchResults; }
    public void setWebSearchResults(List<SearchResult> webSearchResults) { this.webSearchResults = webSearchResults; }

    public String getWebSearchContext() { return webSearchContext; }
    public void setWebSearchContext(String webSearchContext) { this.webSearchContext = webSearchContext; }
}
