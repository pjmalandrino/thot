package com.example.contextengine.domain.step;

import com.example.contextengine.domain.model.ConversationMessage;
import com.example.contextengine.domain.model.StepResult;
import com.example.contextengine.domain.pipeline.ContextStep;
import com.example.contextengine.domain.pipeline.PipelineContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class QueryRewriter implements ContextStep {

    private static final Logger log = LoggerFactory.getLogger(QueryRewriter.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    static final String SYSTEM_PROMPT = """
            Tu es un specialiste de la reformulation de requetes pour optimiser les reponses d'un assistant IA.

            Ton role :
            - Decomposer les requetes complexes en une formulation claire et precise
            - Ajouter du contexte implicite quand necessaire
            - Optimiser la requete pour une recherche RAG (mots-cles pertinents, formulation precise)
            - Conserver le sens original et l'intention de l'utilisateur

            Si la requete est deja claire et precise, retourne-la telle quelle.
            Si un historique de conversation est fourni, utilise-le pour contextualiser la requete \
            (ex: resoudre les pronoms, completer les references).

            ## Format de sortie JSON strict (rien d'autre)

            {"rewritten": "la requete reformulee", "changed": true, "reason": "courte explication"}

            Ou si pas de changement :

            {"rewritten": "la requete originale", "changed": false, "reason": ""}
            """;

    @Override
    public String featureName() {
        return "query-rewriting";
    }

    @Override
    public StepResult execute(PipelineContext context) {
        try {
            String userMessage = buildUserMessage(context);
            log.info("[QUERY-REWRITE] Analyzing prompt: '{}'", context.getPrompt());
            String rawResponse = context.getLlmPort().analyze(SYSTEM_PROMPT, userMessage);
            log.debug("[QUERY-REWRITE] LLM raw response: {}", rawResponse);

            String json = VaguenessDetector.extractJson(rawResponse);
            JsonNode node = mapper.readTree(json);

            boolean changed = node.path("changed").asBoolean(false);
            if (changed) {
                String rewritten = node.path("rewritten").asText(context.getPrompt());
                context.setRewrittenQuery(rewritten);
                log.info("[QUERY-REWRITE] Rewrote: '{}' -> '{}'", context.getPrompt(), rewritten);
            } else {
                log.info("[QUERY-REWRITE] No rewrite needed");
            }

            return StepResult.continueProcessing();
        } catch (Exception e) {
            log.warn("[QUERY-REWRITE] Error, failing open: {}", e.getMessage());
            return StepResult.continueProcessing();
        }
    }

    private String buildUserMessage(PipelineContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("Requete utilisateur : ").append(context.getPrompt());

        if (!context.getConversationHistory().isEmpty()) {
            sb.append("\n\nHistorique de conversation :\n");
            for (ConversationMessage msg : context.getConversationHistory()) {
                sb.append(msg.role()).append(": ").append(msg.content()).append("\n");
            }
        }

        return sb.toString();
    }
}
