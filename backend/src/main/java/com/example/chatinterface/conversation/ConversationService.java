package com.example.chatinterface.conversation;

import com.example.chatinterface.document.DocumentRepository;
import com.example.chatinterface.document.DocumentService;
import com.example.chatinterface.llm.LlmGateway;
import com.example.chatinterface.llm.LlmGatewayFactory;
import com.example.chatinterface.llm.LlmModel;
import com.example.chatinterface.llm.LlmModelRepository;
import com.example.chatinterface.thotspace.Thotspace;
import com.example.chatinterface.thotspace.ThotspaceRepository;
import com.example.chatinterface.websearch.SearchResult;
import com.example.chatinterface.websearch.WebSearchService;
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
    private final WebSearchService webSearchService;
    private final DocumentService documentService;
    private final DocumentRepository documentRepository;
    private final LlmGatewayFactory gatewayFactory;
    private final LlmModelRepository modelRepository;

    public ConversationService(
            ConversationRepository conversationRepository,
            LlmInteractionRepository interactionRepository,
            ThotspaceRepository thotspaceRepository,
            WebSearchService webSearchService,
            DocumentService documentService,
            DocumentRepository documentRepository,
            LlmGatewayFactory gatewayFactory,
            LlmModelRepository modelRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.interactionRepository = interactionRepository;
        this.thotspaceRepository = thotspaceRepository;
        this.webSearchService = webSearchService;
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

    public Conversation createConversation(Long thotspaceId) {
        Thotspace space = thotspaceRepository.findById(thotspaceId)
                .orElseThrow(() -> new RuntimeException("Thotspace not found"));
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

    public LlmInteraction complete(Long conversationId, String prompt, Long modelId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        String documentContext = documentService.buildDocumentContext(conversationId);
        String systemPromptText = buildSystemPrompt(conversation.getThotspace(), documentContext, null);

        ChatMemory memory = buildMemory(conversationId);
        memory.add(SystemMessage.from(systemPromptText));
        memory.add(UserMessage.from(prompt));

        LlmGateway gateway = resolveGateway(modelId);
        String response = gateway.generate(memory.messages());
        LlmInteraction interaction = interactionRepository.save(
                new LlmInteraction(conversation, prompt, response));

        autoTitle(conversation, prompt);
        return interaction;
    }

    public LlmInteraction completeWithWebSearch(Long conversationId, String prompt, Long modelId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        // 1. Search + Extract
        log.info("[WEB-SEARCH] Starting for conversation {} | query: {}", conversationId, prompt);
        List<SearchResult> results = webSearchService.searchAndExtract(prompt);
        log.info("[WEB-SEARCH] Got {} sources", results.size());

        String sourcesBlock = webSearchService.buildContextPrompt(results);
        String documentContext = documentService.buildDocumentContext(conversationId);
        String systemPromptText = buildSystemPrompt(conversation.getThotspace(), documentContext, sourcesBlock);

        // 2. Build messages: system prompt + history + question
        ChatMemory memory = buildMemory(conversationId);
        memory.add(SystemMessage.from(systemPromptText));
        memory.add(UserMessage.from(prompt));

        log.info("[WEB-SEARCH] Sending {} messages to LLM", memory.messages().size());

        // 3. Generate
        LlmGateway gateway = resolveGateway(modelId);
        String response = gateway.generate(memory.messages());
        log.info("[WEB-SEARCH] LLM response received ({} chars)", response.length());

        // 4. Persist with sources
        List<SourceInfo> sourceInfos = results.stream()
                .map(r -> new SourceInfo(r.getCitationId(), r.getSourceUrl(), r.getSourceTitle()))
                .toList();

        LlmInteraction interaction = interactionRepository.save(
                new LlmInteraction(conversation, prompt, response, sourceInfos));

        autoTitle(conversation, prompt);
        return interaction;
    }

    @Transactional
    public void deleteConversation(Long conversationId) {
        documentRepository.deleteByConversationId(conversationId);
        interactionRepository.deleteByConversationId(conversationId);
        conversationRepository.deleteById(conversationId);
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
