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

import java.util.ArrayList;
import java.util.List;

@Component
@Order(1)
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

            Ou si vague=true :

            {"vague":true,"confidence":0.9,"reason":"courte explication","suggestions":["Question complete 1 ?","Question complete 2 ?","Question complete 3 ?"]}

            REGLES CRITIQUES pour les suggestions :
            - Chaque suggestion DOIT etre une QUESTION COMPLETE et autonome (pas un mot-cle, pas un fragment)
            - Chaque suggestion DOIT se terminer par un point d'interrogation
            - Les suggestions DOIVENT etre des reformulations precises du message original
            - "confidence" = certitude dans le verdict (0.0 a 1.0)
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
            String rawResponse = context.getLlmPort().analyze(SYSTEM_PROMPT, prompt);
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
