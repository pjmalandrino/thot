package com.example.chatinterface.conversation;

import com.example.chatinterface.contextengine.ContextEngineClient;
import com.example.chatinterface.contextengine.ContextEngineRequest;
import com.example.chatinterface.contextengine.ContextEngineRequest.ConversationMessageDto;
import com.example.chatinterface.contextengine.ContextEngineResponse;
import com.example.chatinterface.document.DocumentRepository;
import com.example.chatinterface.document.DocumentService;
import com.example.chatinterface.llm.LlmGateway;
import com.example.chatinterface.llm.LlmGatewayFactory;
import com.example.chatinterface.llm.LlmModel;
import com.example.chatinterface.llm.LlmModelRepository;
import com.example.chatinterface.thotspace.Thotspace;
import com.example.chatinterface.thotspace.ThotspaceRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    // ── Prompt en couches ─────────────────────────────────────────────────────

    private static final String BASE_SYSTEM_PROMPT = """
            Tu es THOT, un assistant IA avance. Tu es precis, concis et utile.

            Regles de comportement :
            - Reponds dans la langue de l'utilisateur.
            - Sois factuel et structure tes reponses avec du Markdown quand c'est pertinent.
            - Si tu n'es pas sur d'une information, dis-le clairement plutot que d'inventer.
            - Adapte le niveau de detail a la complexite de la question.

            ## Outils de visualisation

            Tu disposes de deux outils de visualisation. Utilise-les via des blocs de code speciaux.
            IMPORTANT : N'utilise JAMAIS de tableau Markdown classique (avec | et ---). Utilise TOUJOURS l'outil table.

            ### Outil "table" — Donnees structurees
            Pour presenter des donnees comparatives, des classements, ou des listes structurees :
            ```table
            {"columns":["Ville","Population","Pays"],"rows":[["Paris","2161000","France"],["Lyon","522969","France"]]}
            ```
            - Le JSON contient "columns" (liste de noms de colonnes) et "rows" (liste de lignes, chaque ligne est une liste de valeurs).
            - UNE SEULE LIGNE de JSON valide.
            - Pas de Markdown a l'interieur, uniquement des valeurs texte.

            ### Outil "chart" — Graphiques interactifs
            OBLIGATOIRE quand l'utilisateur demande un graphique, graph, chart, diagramme, ou visualisation :
            ```chart
            {"data":[{"type":"bar","x":["Paris","Lyon","Marseille"],"y":[2161000,522969,873076]}],"layout":{"title":"Population des villes"}}
            ```
            - JSON Plotly valide avec "data" (liste de traces) et "layout" (avec au minimum un "title").
            - Types : bar, scatter, line, pie, histogram, heatmap.
            - Pour un pie : {"data":[{"type":"pie","labels":["A","B"],"values":[60,40]}],"layout":{"title":"Titre"}}
            - UNE SEULE LIGNE de JSON valide.
            - NE genere PAS de Python, matplotlib ou code executable.

            ### Regles communes
            - Tu peux combiner les deux outils dans une meme reponse (ex: un graphique + un tableau des donnees).
            - Genere le JSON sur UNE SEULE LIGNE, pas de retours a la ligne dans le bloc.
            - NE genere JAMAIS de tableau Markdown classique. Utilise TOUJOURS ```table.
            """;

    private static final String SPACE_INSTRUCTIONS_TEMPLATE = """

            ## Instructions specifiques de l'espace
            %s
            """;

    private static final String WEB_SEARCH_CONTEXT_TEMPLATE = """

            ## Mode recherche web
            Tu es en mode recherche web. Reponds a la question de l'utilisateur en te basant \
            UNIQUEMENT sur les sources fournies ci-dessous.

            Regles strictes :
            - Base ta reponse exclusivement sur le contenu des sources.
            - Cite tes sources en utilisant le format [1], [2], etc. dans le corps de ta reponse.
            - Si les sources ne contiennent pas d'information pertinente, dis-le clairement.
            - N'invente aucune information au-dela de ce qui est dans les sources.
            - A la fin de ta reponse, ajoute une section "Sources :" listant les URLs reellement citees.

            Sources :
            %s
            """;

    private static final String CLARIFICATION_CONTEXT_TEMPLATE = """

            ## Precision de l'utilisateur
            La question de l'utilisateur etait initialement vague. Il a precise son intention :
            "%s"
            Utilise cette precision pour orienter ta reponse de maniere pertinente.
            """;

    private static final String DOCUMENT_CONTEXT_TEMPLATE = """

            ## Documents attaches
            L'utilisateur a attache les documents suivants a cette conversation. \
            Utilise leur contenu pour repondre a ses questions.

            Regles :
            - Base ta reponse sur le contenu des documents quand c'est pertinent.
            - Si l'utilisateur pose une question sur un document specifique, concentre-toi sur celui-ci.
            - Cite le nom du document quand tu fais reference a son contenu.
            - Si les documents ne contiennent pas l'information demandee, dis-le clairement.

            %s
            """;

    // ── Dependencies ──────────────────────────────────────────────────────────

    private final ConversationRepository conversationRepository;
    private final LlmInteractionRepository interactionRepository;
    private final ThotspaceRepository thotspaceRepository;
    private final ContextEngineClient contextEngineClient;
    private final DocumentService documentService;
    private final DocumentRepository documentRepository;
    private final LlmGatewayFactory gatewayFactory;
    private final LlmModelRepository modelRepository;

    public ConversationService(
            ConversationRepository conversationRepository,
            LlmInteractionRepository interactionRepository,
            ThotspaceRepository thotspaceRepository,
            ContextEngineClient contextEngineClient,
            DocumentService documentService,
            DocumentRepository documentRepository,
            LlmGatewayFactory gatewayFactory,
            LlmModelRepository modelRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.interactionRepository = interactionRepository;
        this.thotspaceRepository = thotspaceRepository;
        this.contextEngineClient = contextEngineClient;
        this.documentService = documentService;
        this.documentRepository = documentRepository;
        this.gatewayFactory = gatewayFactory;
        this.modelRepository = modelRepository;
    }

    // ── Conversations CRUD ────────────────────────────────────────────────────

    public List<Conversation> getConversations(Long thotspaceId) {
        if (thotspaceId != null) {
            return conversationRepository.findByThotspaceIdOrderByCreatedAtDesc(thotspaceId);
        }
        return conversationRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public Conversation createConversation(Long thotspaceId) {
        Thotspace space;
        if (thotspaceId != null) {
            space = thotspaceRepository.findById(thotspaceId)
                    .orElseThrow(() -> new RuntimeException("Thotspace not found: " + thotspaceId));
        } else {
            space = thotspaceRepository.findByIsDefaultTrue()
                    .orElseGet(() -> {
                        Thotspace defaultSpace = new Thotspace("Général");
                        defaultSpace.setDefault(true);
                        return thotspaceRepository.save(defaultSpace);
                    });
        }
        return conversationRepository.save(new Conversation("Nouvelle conversation", space));
    }

    public Conversation renameConversation(Long conversationId, String title) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
        String trimmed = (title != null && !title.isBlank()) ? title.trim() : "Sans titre";
        conversation.setTitle(trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed);
        return conversationRepository.save(conversation);
    }

    // ── Completions ───────────────────────────────────────────────────────────

    public List<LlmInteraction> getCompletions(Long conversationId) {
        return interactionRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    /**
     * Unified completion: context-engine decides about web search (auto-trigger).
     * Returns CompletionResponse with full pipeline metadata.
     */
    public CompletionResponse complete(Long conversationId, String prompt, Long modelId) {
        return complete(conversationId, prompt, modelId, null);
    }

    /**
     * Completion with optional clarification context.
     * When clarificationContext is present, the user has already answered a clarification question —
     * we skip the context-engine (no re-vagueness check) and inject the clarification transparently.
     */
    public CompletionResponse complete(Long conversationId, String prompt, Long modelId, String clarificationContext) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        String documentContext = documentService.buildDocumentContext(conversationId);

        // Fast path: clarification response → skip context-engine, go straight to LLM
        if (clarificationContext != null && !clarificationContext.isBlank()) {
            log.info("[COMPLETION] Clarification context present, skipping context-engine");
            return completeWithClarification(conversation, prompt, modelId, clarificationContext, documentContext);
        }

        // Normal path: full context-engine pipeline
        return completeWithPipeline(conversation, prompt, modelId, documentContext);
    }

    private CompletionResponse completeWithClarification(
            Conversation conversation, String prompt, Long modelId,
            String clarificationContext, String documentContext) {

        // Build system prompt with clarification hint
        String systemPromptText = buildSystemPrompt(
                conversation.getThotspace(), documentContext, null);
        systemPromptText += String.format(CLARIFICATION_CONTEXT_TEMPLATE, clarificationContext);

        // Build memory and generate
        ChatMemory memory = buildMemory(conversation.getId());
        memory.add(SystemMessage.from(systemPromptText));
        memory.add(UserMessage.from(prompt));

        LlmGateway gateway = resolveGateway(modelId);
        String response = gateway.generate(memory.messages());

        // Persist with original prompt only (clarification stays invisible)
        LlmInteraction interaction = interactionRepository.save(
                new LlmInteraction(conversation, prompt, response));

        autoTitle(conversation, prompt);

        return CompletionResponse.from(interaction);
    }

    private CompletionResponse completeWithPipeline(
            Conversation conversation, String prompt, Long modelId, String documentContext) {

        // 1. Call context-engine — auto-search decides about web
        ContextEngineRequest ceRequest = new ContextEngineRequest();
        ceRequest.setPrompt(prompt);
        ceRequest.setWebSearchRequested(false);
        ceRequest.setDocumentContext(documentContext);
        ceRequest.setConversationHistory(buildRecentHistory(conversation.getId()));

        ContextEngineResponse ceResponse = contextEngineClient.analyze(ceRequest);

        // 2. If clarification needed, return without persisting
        if (!ceResponse.isContinue()) {
            return CompletionResponse.clarification(
                    prompt,
                    ceResponse.getClarificationMessage(),
                    ceResponse.getSuggestions(),
                    ceResponse.getConfidence());
        }

        // 3. Use the rewritten query if available
        String effectivePrompt = ceResponse.getRewrittenQuery() != null
                ? ceResponse.getRewrittenQuery()
                : prompt;

        // 4. Build system prompt with web search context if present
        String systemPromptText = buildSystemPrompt(
                conversation.getThotspace(), documentContext, ceResponse.getWebSearchContext());

        // 5. Build memory and generate
        ChatMemory memory = buildMemory(conversation.getId());
        memory.add(SystemMessage.from(systemPromptText));
        memory.add(UserMessage.from(effectivePrompt));

        LlmGateway gateway = resolveGateway(modelId);
        String response = gateway.generate(memory.messages());

        // 6. Persist with sources (including extractedText)
        List<SourceInfo> sourceInfos = ceResponse.getWebSearchResults() != null
                ? ceResponse.getWebSearchResults().stream()
                    .map(r -> new SourceInfo(r.citationId(), r.sourceUrl(), r.sourceTitle(), r.extractedText()))
                    .toList()
                : List.of();

        LlmInteraction interaction = sourceInfos.isEmpty()
                ? interactionRepository.save(new LlmInteraction(conversation, prompt, response))
                : interactionRepository.save(new LlmInteraction(conversation, prompt, response, sourceInfos));

        autoTitle(conversation, prompt);

        // 7. Return enriched response with pipeline metadata
        return CompletionResponse.from(interaction)
                .withPipelineMetadata(
                        ceResponse.getRewrittenQuery(),
                        ceResponse.isAutoWebSearchTriggered(),
                        ceResponse.getTokenUsage());
    }

    @Transactional
    public void deleteConversation(Long conversationId) {
        documentRepository.deleteByConversationId(conversationId);
        interactionRepository.deleteByConversationId(conversationId);
        conversationRepository.deleteById(conversationId);
    }

    // ── Streaming persistence ─────────────────────────────────────────────────

    /**
     * Persists the result of a streaming completion (Think or Research mode).
     * Called after the SSE stream completes with the accumulated data.
     */
    @Transactional
    public CompletionResponse persistStreamResult(Long conversationId, String prompt,
                                                   String mode, String response,
                                                   String thinking, List<SourceInfo> sources) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        LlmInteraction interaction = interactionRepository.save(
                new LlmInteraction(conversation, prompt, response, mode, thinking,
                        sources != null ? sources : List.of()));

        autoTitle(conversation, prompt);

        return CompletionResponse.from(interaction);
    }

    /**
     * Builds document context for a conversation (exposed for streaming controller).
     */
    public String getDocumentContext(Long conversationId) {
        return documentService.buildDocumentContext(conversationId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String buildSystemPrompt(Thotspace space, String documentContext, String webSearchContext) {
        StringBuilder sb = new StringBuilder(BASE_SYSTEM_PROMPT);
        if (space != null && space.getSystemPrompt() != null && !space.getSystemPrompt().isBlank()) {
            sb.append(String.format(SPACE_INSTRUCTIONS_TEMPLATE, space.getSystemPrompt()));
        }
        if (documentContext != null) {
            sb.append(String.format(DOCUMENT_CONTEXT_TEMPLATE, documentContext));
        }
        if (webSearchContext != null) {
            sb.append(String.format(WEB_SEARCH_CONTEXT_TEMPLATE, webSearchContext));
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    protected LlmGateway resolveGateway(Long modelId) {
        LlmModel model = (modelId != null)
                ? modelRepository.findById(modelId).orElseGet(this::defaultModel)
                : defaultModel();
        return gatewayFactory.getGateway(model.getProvider(), model.getModelName());
    }

    private LlmModel defaultModel() {
        return modelRepository.findFirstByEnabledTrue()
                .orElseThrow(() -> new RuntimeException("No enabled LLM model available"));
    }

    private List<ConversationMessageDto> buildRecentHistory(Long conversationId) {
        List<LlmInteraction> history = interactionRepository
                .findByConversationIdOrderByCreatedAtAsc(conversationId);
        int start = Math.max(0, history.size() - 5);
        return history.subList(start, history.size()).stream()
                .flatMap(i -> java.util.stream.Stream.of(
                        new ConversationMessageDto("user", i.getPrompt()),
                        new ConversationMessageDto("assistant", i.getResponse())))
                .toList();
    }

    private ChatMemory buildMemory(Long conversationId) {
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
        List<LlmInteraction> history = interactionRepository
                .findByConversationIdOrderByCreatedAtAsc(conversationId);
        for (LlmInteraction prev : history) {
            memory.add(UserMessage.from(prev.getPrompt()));
            memory.add(AiMessage.from(prev.getResponse()));
        }
        return memory;
    }

    private void autoTitle(Conversation conversation, String prompt) {
        if ("Nouvelle conversation".equals(conversation.getTitle())) {
            String title = prompt.length() > 30 ? prompt.substring(0, 30) + "..." : prompt;
            conversation.setTitle(title);
            conversationRepository.save(conversation);
        }
    }
}
