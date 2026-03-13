package com.example.chatinterface.context;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration du module Context Engineering.
 * Le modele d'analyse est decouple du modele de chat.
 */
@ConfigurationProperties(prefix = "context")
public class ContextProperties {

    /**
     * Nom du modele LLM dedie a l'analyse contextuelle (lookup par model_name en DB).
     * Recommandation : un SLM rapide comme Mistral Small pour la classification.
     */
    private String primaryModel = "mistral-small-latest";

    /**
     * Seuil de confiance minimum (0.0–1.0).
     * En dessous de ce seuil, le pipeline ne bloque pas l'utilisateur (fail-open).
     */
    private double minConfidence = 0.75;

    /**
     * Politique de fallback quand la confiance est sous le seuil.
     * CONTINUE = laisser passer (fail-open), BLOCK = bloquer quand meme.
     */
    private FallbackPolicy fallbackPolicy = FallbackPolicy.CONTINUE;

    public enum FallbackPolicy {
        CONTINUE, BLOCK
    }

    public String getPrimaryModel() { return primaryModel; }
    public void setPrimaryModel(String primaryModel) { this.primaryModel = primaryModel; }

    public double getMinConfidence() { return minConfidence; }
    public void setMinConfidence(double minConfidence) { this.minConfidence = minConfidence; }

    public FallbackPolicy getFallbackPolicy() { return fallbackPolicy; }
    public void setFallbackPolicy(FallbackPolicy fallbackPolicy) { this.fallbackPolicy = fallbackPolicy; }
}
