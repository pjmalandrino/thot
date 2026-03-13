package com.example.chatinterface.context;

import com.example.chatinterface.llm.LlmGateway;
import com.example.chatinterface.llm.LlmGatewayFactory;
import com.example.chatinterface.llm.LlmModel;
import com.example.chatinterface.llm.LlmModelRepository;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * Endpoint autonome du module Context Engineering.
 * Le modele LLM est configure via ContextProperties (decouple du modele de chat).
 * Seul point de couplage avec l'infra : llm/ package (LlmGatewayFactory + LlmModelRepository).
 * ZERO dependance sur conversation/.
 */
@RestController
@RequestMapping("/api/context")
public class ContextController {

    private static final Logger log = LoggerFactory.getLogger(ContextController.class);

    private final ContextPipeline pipeline;
    private final ContextProperties properties;
    private final LlmGatewayFactory gatewayFactory;
    private final LlmModelRepository modelRepository;

    public ContextController(
            ContextPipeline pipeline,
            ContextProperties properties,
            LlmGatewayFactory gatewayFactory,
            LlmModelRepository modelRepository
    ) {
        this.pipeline = pipeline;
        this.properties = properties;
        this.gatewayFactory = gatewayFactory;
        this.modelRepository = modelRepository;
    }

    @PostMapping("/analyze")
    @Transactional(readOnly = true)
    public ContextResponse analyze(@RequestBody ContextRequest request) {
        log.info("[CONTEXT] Analyze request: prompt='{}', steps={}, model={}",
                request.getPrompt(), request.getSteps(), properties.getPrimaryModel());

        // Resolve le gateway depuis la config du module (pas le choix utilisateur)
        LlmGateway gateway = resolveContextGateway();

        // Adapte LlmGateway -> ContextLlm (lambda, pas de classe dediee)
        ContextLlm adapter = (systemPrompt, userMessage) ->
                gateway.generate(List.of(
                        SystemMessage.from(systemPrompt),
                        UserMessage.from(userMessage)));

        // Execute le pipeline
        Set<String> steps = request.getSteps() != null ? request.getSteps() : Set.of();
        ContextStepResult result = pipeline.run(request.getPrompt(), adapter, steps);

        log.info("[CONTEXT] Analysis result: {} (confidence={})",
                result.shouldContinue() ? "continue" : result.getType(),
                result.getConfidence());
        return ContextResponse.from(result);
    }

    /**
     * Resolve le gateway LLM dedie au context engineering.
     * Lookup par model_name en DB, fallback sur le premier modele enabled.
     */
    private LlmGateway resolveContextGateway() {
        LlmModel model = modelRepository.findByModelName(properties.getPrimaryModel())
                .orElseGet(this::defaultModel);
        log.debug("[CONTEXT] Using model: {} (provider={})", model.getModelName(), model.getProvider().getName());
        return gatewayFactory.getGateway(model.getProvider(), model.getModelName());
    }

    private LlmModel defaultModel() {
        log.warn("[CONTEXT] Model '{}' not found in DB, falling back to first enabled model",
                properties.getPrimaryModel());
        return modelRepository.findFirstByEnabledTrue()
                .orElseThrow(() -> new RuntimeException("No enabled LLM model available"));
    }
}
