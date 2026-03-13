package com.example.chatinterface.service;

import com.example.chatinterface.dto.SearchResultContext;
import com.example.chatinterface.model.Conversation;
import com.example.chatinterface.model.LlmInteraction;
import com.example.chatinterface.model.SourceInfo;
import com.example.chatinterface.repository.ConversationRepository;
import com.example.chatinterface.repository.LlmInteractionRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

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
    private final OllamaChatModel chatModel;

    public LlmService(
            ConversationRepository conversationRepository,
            LlmInteractionRepository interactionRepository,
            WebSearchService webSearchService,
            @Value("${ollama.base-url}") String baseUrl,
            @Value("${ollama.model}") String modelName
    ) {
        this.conversationRepository = conversationRepository;
        this.interactionRepository = interactionRepository;
        this.webSearchService = webSearchService;
        this.chatModel = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .build();
    }

    public List<Conversation> getConversations() {
        return conversationRepository.findAllByOrderByCreatedAtDesc();
    }

    public Conversation createConversation() {
        return conversationRepository.save(new Conversation("Nouvelle conversation"));
    }

    public List<LlmInteraction> getCompletions(Long conversationId) {
        List<LlmInteraction> results = interactionRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        log.info("[GET-COMPLETIONS] conversation {} → {} interactions trouvées en BDD", conversationId, results.size());
        if (!results.isEmpty()) {
            LlmInteraction last = results.get(results.size() - 1);
            log.info("[GET-COMPLETIONS] Dernière interaction: id={} | prompt='{}' | response length={}",
                    last.getId(), last.getPrompt(), last.getResponse() != null ? last.getResponse().length() : 0);
        }
        return results;
    }

    public LlmInteraction complete(Long conversationId, String prompt) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);

        List<LlmInteraction> history = interactionRepository
                .findByConversationIdOrderByCreatedAtAsc(conversationId);

        for (LlmInteraction prev : history) {
            memory.add(UserMessage.from(prev.getPrompt()));
            memory.add(AiMessage.from(prev.getResponse()));
        }
        memory.add(UserMessage.from(prompt));

        String response = chatModel.generate(memory.messages()).content().text();
        LlmInteraction interaction = interactionRepository.save(
                new LlmInteraction(conversation, prompt, response));
        log.info("[COMPLETE] Interaction saved: id={} | conversationId={}", interaction.getId(), conversationId);

        autoTitle(conversation, prompt);
        return interaction;
    }

    public LlmInteraction completeWithWebSearch(Long conversationId, String prompt) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        // 1. Search + Extract
        log.info("[WEB-SEARCH] Starting for conversation {} | query: {}", conversationId, prompt);
        List<SearchResultContext> contexts = webSearchService.searchAndExtract(prompt);
        log.info("[WEB-SEARCH] Got {} sources after search+extract", contexts.size());

        String sourcesBlock = webSearchService.buildContextPrompt(contexts);
        String systemPrompt = String.format(WEB_SEARCH_SYSTEM_PROMPT, sourcesBlock);
        log.debug("[WEB-SEARCH] System prompt length: {} chars", systemPrompt.length());

        // 2. Build messages: system prompt (pinned) + history + question
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
        memory.add(SystemMessage.from(systemPrompt));

        List<LlmInteraction> history = interactionRepository
                .findByConversationIdOrderByCreatedAtAsc(conversationId);

        for (LlmInteraction prev : history) {
            memory.add(UserMessage.from(prev.getPrompt()));
            memory.add(AiMessage.from(prev.getResponse()));
        }
        memory.add(UserMessage.from(prompt));

        log.info("[WEB-SEARCH] Sending {} messages to LLM (incl. system prompt)", memory.messages().size());

        // 3. Generate
        String response = chatModel.generate(memory.messages()).content().text();
        log.info("[WEB-SEARCH] LLM response received ({} chars)", response.length());

        // 4. Convert contexts to persistable SourceInfo
        List<SourceInfo> sourceInfos = contexts.stream()
                .map(c -> new SourceInfo(c.getCitationId(), c.getSourceUrl(), c.getSourceTitle()))
                .toList();

        LlmInteraction interaction = interactionRepository.save(
                new LlmInteraction(conversation, prompt, response, sourceInfos));
        log.info("[WEB-SEARCH] Interaction saved: id={} | {} sources persisted", interaction.getId(), sourceInfos.size());

        autoTitle(conversation, prompt);
        return interaction;
    }

    @Transactional
    public void deleteConversation(Long conversationId) {
        interactionRepository.deleteByConversationId(conversationId);
        conversationRepository.deleteById(conversationId);
    }

    private void autoTitle(Conversation conversation, String prompt) {
        if ("Nouvelle conversation".equals(conversation.getTitle())) {
            String title = prompt.length() > 30 ? prompt.substring(0, 30) + "..." : prompt;
            conversation.setTitle(title);
            conversationRepository.save(conversation);
        }
    }
}
