package com.example.contextengine.domain.step;

import com.example.contextengine.domain.model.StepResult;
import com.example.contextengine.domain.pipeline.ContextStep;
import com.example.contextengine.domain.pipeline.PipelineContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.example.contextengine.domain.model.ConversationMessage;

import java.util.ArrayList;
import java.util.List;

@Component
@Order(10)
public class VaguenessDetector implements ContextStep {

    private static final Logger log = LoggerFactory.getLogger(VaguenessDetector.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    static final String SYSTEM_PROMPT = """
            Tu es un filtre de clarte. Tu decides si un message peut etre traite par un assistant IA ou s'il est \
            TOTALEMENT VIDE DE SENS sans plus de contexte.

            ## REGLE ABSOLUE : tu reponds vague=true UNIQUEMENT si le message ne contient AUCUN sujet identifiable.

            Un message qui mentionne un sujet, meme vaguement, est TOUJOURS vague=false.
            Un mot ambigu ("Java", "Python", "Apple") contient un sujet identifiable -> vague=false.
            L'assistant IA peut toujours demander des precisions lui-meme si besoin.

            ## vague=false (la grande majorite des messages) :

            Tout message qui contient AU MOINS UN sujet, meme :
            - Ambigu : "C'est quoi Java ?", "Parle-moi de Python", "C'est quoi Apple ?"
            - Large : "Explique-moi le machine learning", "Comment fonctionne internet ?"
            - Court mais clair : "Bonjour", "Merci", "Salut", "OK"
            - Factuel : "Capitale de la France ?", "2+2 ?"
            - Technique : "Comment trier une liste ?", "Difference entre GET et POST ?"
            - Avec action : "Ecris un poeme", "Traduis en anglais", "Resume ce texte"
            - Comparatif avec objets : "Java vs Python ?", "React ou Vue ?"

            ## vague=true (UNIQUEMENT ces cas extremes) :

            Le message ne contient AUCUN sujet, AUCUN nom, AUCUN theme :
            - "Aide-moi" (aide a quoi ?)
            - "J'ai un probleme" (quel probleme ?)
            - "Comment faire ?" (faire quoi ?)
            - "C'est quoi le mieux ?" (le mieux de quoi ?)
            - "Dis-moi quelque chose" (sur quoi ?)
            - "Je suis bloque" (sur quoi ?)

            ## Exemples de classification

            "C'est quoi Java ?"             -> vague=false (sujet: Java)
            "Aide-moi"                      -> vague=true  (aucun sujet)
            "Bonjour"                       -> vague=false (salutation)
            "C'est quoi le mieux ?"         -> vague=true  (aucun sujet de comparaison)
            "Comment fonctionne Git ?"      -> vague=false (sujet: Git)
            "J'ai un probleme"              -> vague=true  (aucun sujet)
            "Parle-moi de Kubernetes"       -> vague=false (sujet: Kubernetes)
            "Comment faire ?"               -> vague=true  (aucun sujet)
            "C'est quoi le cloud ?"         -> vague=false (sujet: cloud)
            "Compare React et Angular"      -> vague=false (sujets: React, Angular)

            ## Format de sortie JSON strict (rien d'autre)

            {"vague":false,"confidence":0.95,"reason":"","suggestions":[]}

            Ou si vague=true (la "reason" doit etre une phrase courte, bienveillante, qui explique POURQUOI c'est vague) :

            {"vague":true,"confidence":0.9,"reason":"courte explication","suggestions":["Je cherche des infos sur un outil technique","Je veux comprendre un concept","J'ai un probleme de configuration"]}

            REGLES CRITIQUES pour les suggestions :
            - Les suggestions sont des REPONSES que l'utilisateur peut CLIQUER pour preciser son intention
            - Chaque suggestion est formulee A LA PREMIERE PERSONNE, comme si l'utilisateur parlait
            - Chaque suggestion est une AFFIRMATION CONCRETE (PAS une question, PAS un point d'interrogation)
            - Chaque suggestion propose un DOMAINE ou SUJET SPECIFIQUE plausible
            - INTERDIT : les questions ("Cherchez-vous... ?", "Parlez-vous de... ?") — l'utilisateur ne repond pas a une question, il CHOISIT une piste
            - INTERDIT : reformuler la question originale
            - Bons exemples pour "Aide-moi" : "J'ai besoin d'aide sur un probleme technique", "Je cherche un tutoriel ou guide", "Je veux comprendre un concept"
            - Bons exemples pour "C'est quoi le mieux ?" : "Je compare des langages de programmation", "Je cherche le meilleur outil pour un projet web", "Je compare des solutions cloud"
            - Bons exemples pour "C'est quoi sont truc ?" : "Je veux comprendre un terme technique", "Je cherche la definition d'un outil ou logiciel", "Je parle d'un concept que j'ai lu quelque part"
            - "confidence" = certitude dans le verdict (0.0 a 1.0)

            ## Contexte conversationnel

            Un historique de conversation et/ou des documents attaches peuvent etre fournis avec le message.
            Si disponible, utilise ce contexte pour generer des suggestions PLUS PERTINENTES et SPECIFIQUES.
            Exemple : si l'utilisateur parlait de Docker et dit "aide moi", propose des suggestions liees a Docker.
            IMPORTANT : le contexte ne change PAS le verdict vague/non-vague. Il ameliore UNIQUEMENT la qualite des suggestions quand vague=true.
            """;

    @Override
    public String featureName() {
        return "vagueness-detection";
    }

    @Override
    public StepResult execute(PipelineContext context) {
        try {
            String prompt = context.getEffectiveQuery();
            log.info("[VAGUENESS] Analyzing prompt: '{}'", prompt);
            String enrichedPrompt = buildContextualPrompt(prompt, context);
            String rawResponse = context.getLlmPort().analyze(SYSTEM_PROMPT, enrichedPrompt);
            log.debug("[VAGUENESS] LLM raw response: {}", rawResponse);

            String json = extractJson(rawResponse);
            JsonNode node = mapper.readTree(json);

            boolean vague = node.path("vague").asBoolean(false);
            double confidence = node.path("confidence").asDouble(0.5);

            if (!vague) {
                log.info("[VAGUENESS] Prompt is clear (confidence={})", confidence);
                return StepResult.continueProcessing();
            }

            String reason = node.path("reason").asText("Votre question manque de precision.");
            List<String> suggestions = new ArrayList<>();
            JsonNode suggestionsNode = node.path("suggestions");
            if (suggestionsNode.isArray()) {
                for (JsonNode s : suggestionsNode) {
                    suggestions.add(s.asText());
                }
            }

            log.info("[VAGUENESS] Prompt is vague (confidence={}): {} | {} suggestion(s)",
                    confidence, reason, suggestions.size());
            return StepResult.interrupt("clarification_needed", reason, suggestions, confidence);

        } catch (Exception e) {
            log.warn("[VAGUENESS] Error during analysis, failing open: {}", e.getMessage());
            return StepResult.continueProcessing();
        }
    }

    /**
     * Builds a contextual prompt by prepending conversation history and document hints.
     * This helps the LLM generate more relevant suggestions when the message is vague.
     */
    String buildContextualPrompt(String prompt, PipelineContext context) {
        List<ConversationMessage> history = context.getConversationHistory();
        String docContext = context.getDocumentContext();
        String driveContext = context.getDriveDocumentContext();

        boolean hasHistory = history != null && !history.isEmpty();
        boolean hasDocs = docContext != null && !docContext.isBlank();
        boolean hasDriveDocs = driveContext != null && !driveContext.isBlank();

        // No context available — return raw prompt
        if (!hasHistory && !hasDocs && !hasDriveDocs) {
            return prompt;
        }

        StringBuilder sb = new StringBuilder();

        // Add recent conversation history (last 6 messages max, truncated)
        if (hasHistory) {
            sb.append("## Historique recent de la conversation\n");
            int start = Math.max(0, history.size() - 6);
            for (int i = start; i < history.size(); i++) {
                ConversationMessage msg = history.get(i);
                String role = "user".equals(msg.role()) ? "Utilisateur" : "Assistant";
                String content = msg.content();
                if (content.length() > 200) {
                    content = content.substring(0, 200) + "...";
                }
                sb.append(role).append(": ").append(content).append("\n");
            }
            sb.append("\n");
        }

        // Add document context hint (truncated)
        if (hasDocs) {
            sb.append("## Documents attaches a la conversation\n");
            String truncated = docContext.length() > 300
                    ? docContext.substring(0, 300) + "..."
                    : docContext;
            sb.append(truncated).append("\n\n");
        }

        // Add Drive document context hint (truncated)
        if (hasDriveDocs) {
            sb.append("## Documents Google Drive de l'utilisateur\n");
            String truncated = driveContext.length() > 300
                    ? driveContext.substring(0, 300) + "..."
                    : driveContext;
            sb.append(truncated).append("\n\n");
        }

        // The actual message to analyze
        sb.append("## Message a analyser\n");
        sb.append(prompt);

        log.debug("[VAGUENESS] Enriched prompt with context ({} history msgs, docs={}, driveDocs={})",
                hasHistory ? Math.min(history.size(), 6) : 0, hasDocs, hasDriveDocs);

        return sb.toString();
    }

    static String extractJson(String raw) {
        if (raw == null) return "{}";
        String trimmed = raw.trim();

        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline != -1) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
            trimmed = trimmed.trim();
        }

        return trimmed;
    }
}
