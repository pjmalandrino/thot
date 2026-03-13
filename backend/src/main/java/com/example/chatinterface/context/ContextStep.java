package com.example.chatinterface.context;

/**
 * Interface pour un step du pipeline de context engineering.
 * Chaque step est un @Component avec un @Order pour definir la priorite.
 */
public interface ContextStep {

    /**
     * Identifiant technique du step (ex: "vagueness", "intent").
     * Utilise par le frontend pour activer/desactiver individuellement.
     */
    String name();

    /**
     * Execute l'analyse contextuelle sur le prompt utilisateur.
     *
     * @param prompt   le message de l'utilisateur
     * @param llm      le port LLM pour generer des analyses
     * @return CONTINUE si le prompt est OK, INTERRUPT si clarification necessaire
     */
    ContextStepResult execute(String prompt, ContextLlm llm);
}
