package com.example.chatinterface.context;

import java.util.Set;

/**
 * DTO requete pour l'endpoint POST /api/context/analyze.
 * Le modele LLM est configure cote backend (ContextProperties) — pas de modelId ici.
 */
public class ContextRequest {

    private String prompt;
    private Set<String> steps;

    public ContextRequest() {}

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public Set<String> getSteps() { return steps; }
    public void setSteps(Set<String> steps) { this.steps = steps; }
}
