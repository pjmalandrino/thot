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

@Component
@Order(25)
public class AutoWebSearchTrigger implements ContextStep {

    private static final Logger log = LoggerFactory.getLogger(AutoWebSearchTrigger.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    static final String SYSTEM_PROMPT = """
            Tu es un classificateur qui determine si une question necessite une recherche web \
            pour obtenir des informations a jour ou des faits verifiables.

            Reponds "needsWebSearch": true UNIQUEMENT si la question porte sur :
            - Des evenements recents ou actuels (actualites, dates apres 2024, tendances)
            - Des donnees factuelles verifiables en temps reel (prix, meteo, scores, statistiques)
            - Des technologies/versions/releases recentes
            - Des personnes ou organisations dont les informations changent frequemment
            - Des comparatifs necessitant des donnees a jour

            Reponds "needsWebSearch": false si :
            - La question porte sur des concepts stables (maths, physique, histoire ancienne)
            - C'est une demande de code, d'explication technique, ou de redaction creative
            - C'est une salutation ou conversation generale
            - L'utilisateur a deja fourni le contexte necessaire (documents attaches)
            - La question porte sur des connaissances generales intemporelles

            ## Format de sortie JSON strict (rien d'autre)

            {"needsWebSearch": true, "reason": "courte explication"}

            ou

            {"needsWebSearch": false, "reason": ""}
            """;

    @Override
    public String featureName() {
        return "auto-web-search";
    }

    @Override
    public StepResult execute(PipelineContext context) {
        if (context.isWebSearchRequested()) {
            log.debug("[AUTO-WEB-SEARCH] Web search already requested by user, skipping");
            return StepResult.continueProcessing();
        }

        try {
            String query = context.getEffectiveQuery();
            log.info("[AUTO-WEB-SEARCH] Analyzing: '{}'", query);

            String rawResponse = context.getLlmPort().analyze(SYSTEM_PROMPT, query);
            String json = VaguenessDetector.extractJson(rawResponse);
            JsonNode node = mapper.readTree(json);

            boolean needsWebSearch = node.path("needsWebSearch").asBoolean(false);

            if (needsWebSearch) {
                String reason = node.path("reason").asText("");
                log.info("[AUTO-WEB-SEARCH] Triggered: {}", reason);
                context.setWebSearchRequested(true);
                context.setAutoWebSearchTriggered(true);
            } else {
                log.info("[AUTO-WEB-SEARCH] Not needed");
            }

            return StepResult.continueProcessing();
        } catch (Exception e) {
            log.warn("[AUTO-WEB-SEARCH] Error, failing open: {}", e.getMessage());
            return StepResult.continueProcessing();
        }
    }
}
