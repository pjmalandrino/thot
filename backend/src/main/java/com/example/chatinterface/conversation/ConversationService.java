package com.example.chatinterface.conversation;

import com.example.chatinterface.llm.LlmGateway;
import com.example.chatinterface.llm.LlmGatewayFactory;
import com.example.chatinterface.llm.LlmModel;
import com.example.chatinterface.llm.LlmModelRepository;
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

    private static final String WEB_SEARCH_SYSTEM_PROMPT = """
            Tu es un assistant de recherche. Reponds a la question de l'utilisateur en te basant \
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

    private final ConversationRepository conversationRepository;
    private final LlmInteractionRepository interactionRepository;
    private final WebSearchService webSearchService;
    private final LlmGatewayFactory gatewayFactory;
    private final LlmModelRepository modelRepository;

    public ConversationService(
            ConversationRepository conversationRepository,
            LlmInteractionRepository interactionRepository,
            WebSearchService webSearchService,
            LlmGatewayFactory gatewayFactory,
            LlmModelRepository modelRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.interactionRepository = interactionRepository;
        this.webSearchService = webSearchService;
        this.gatewayFactory = gatewayFactory;
        this.modelRepository = modelRepository;
    }

    public List<Conversation> getConversations() {
        return conversationRepository.findAllByOrderByCreatedAtDesc();
    }

    public Conversation createConversation() {
        return conversationRepository.save(new Conversation("Nouvelle conversation"));
    }

    public Conversation renameConversation(Long conversationId, String title) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
        String trimmed = (title != null && !title.isBlank()) ? title.trim() : "Sans titre";
        conversation.setTitle(trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed);
        return conversationRepository.save(conversation);
    }

    public List<LlmInteraction> getCompletions(Long conversationId) {
        return interactionRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    public LlmInteraction complete(Long conversationId, String prompt, Long modelId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        ChatMemory memory = buildMemory(conversationId);
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
        String systemPrompt = String.format(WEB_SEARCH_SYSTEM_PROMPT, sourcesBlock);

        // 2. Build messages: system prompt (pinned) + history + question
        ChatMemory memory = buildMemory(conversationId);
        memory.add(SystemMessage.from(systemPrompt));
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
        interactionRepository.deleteByConversationId(conversationId);
        conversationRepository.deleteById(conversationId);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

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
