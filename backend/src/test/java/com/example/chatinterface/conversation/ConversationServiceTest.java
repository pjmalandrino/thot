package com.example.chatinterface.conversation;

import com.example.chatinterface.contextengine.ContextEngineClient;
import com.example.chatinterface.contextengine.ContextEngineResponse;
import com.example.chatinterface.contextengine.ContextEngineResponse.WebSearchResultDto;
import com.example.chatinterface.document.DocumentRepository;
import com.example.chatinterface.document.DocumentService;
import com.example.chatinterface.llm.LlmGateway;
import com.example.chatinterface.llm.LlmGatewayFactory;
import com.example.chatinterface.llm.LlmModel;
import com.example.chatinterface.llm.LlmModelRepository;
import com.example.chatinterface.llm.LlmProvider;
import com.example.chatinterface.llm.LlmProviderType;
import com.example.chatinterface.thotspace.Thotspace;
import com.example.chatinterface.thotspace.ThotspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.example.chatinterface.TestEntityHelper.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock private ConversationRepository conversationRepository;
    @Mock private LlmInteractionRepository interactionRepository;
    @Mock private ThotspaceRepository thotspaceRepository;
    @Mock private ContextEngineClient contextEngineClient;
    @Mock private DocumentService documentService;
    @Mock private DocumentRepository documentRepository;
    @Mock private LlmGatewayFactory gatewayFactory;
    @Mock private LlmModelRepository modelRepository;
    @Mock private LlmGateway llmGateway;

    @InjectMocks
    private ConversationService conversationService;

    private Thotspace defaultSpace;
    private Conversation conversation;
    private LlmModel defaultModel;
    private LlmProvider defaultProvider;

    @BeforeEach
    void setUp() {
        defaultSpace = new Thotspace("General");
        setId(defaultSpace, 1L);

        conversation = new Conversation("Nouvelle conversation", defaultSpace);
        setId(conversation, 10L);

        defaultProvider = new LlmProvider();
        defaultProvider.setName("ollama-local");
        defaultProvider.setType(LlmProviderType.OLLAMA);
        defaultProvider.setBaseUrl("http://localhost:11434");
        setId(defaultProvider, 1L);

        defaultModel = new LlmModel();
        defaultModel.setProvider(defaultProvider);
        defaultModel.setModelName("llama3.2:3b");
        defaultModel.setDisplayName("Llama 3.2 3B");
        setId(defaultModel, 1L);
    }

    private ContextEngineResponse continueResponse() {
        return ContextEngineResponse.failOpen();
    }

    private ContextEngineResponse clarificationResponse() {
        ContextEngineResponse r = new ContextEngineResponse();
        r.setStatus("clarification_needed");
        r.setConfidence(0.9);
        r.setClarificationMessage("Votre question manque de precision.");
        r.setSuggestions(List.of("Question 1 ?", "Question 2 ?"));
        return r;
    }

    private ContextEngineResponse webSearchResponse() {
        ContextEngineResponse r = new ContextEngineResponse();
        r.setStatus("continue");
        r.setConfidence(1.0);
        r.setWebSearchResults(List.of(
                new WebSearchResultDto("[1]", "https://example.com", "Example", "Contenu extrait")));
        r.setWebSearchContext("[1] Example — https://example.com\nContenu extrait");
        return r;
    }

    // ── createConversation ───────────────────────────────────────────────

    @Nested
    @DisplayName("createConversation")
    class CreateConversation {

        @Test
        @DisplayName("cree une conversation dans un espace specifique")
        void withThotspaceId() {
            when(thotspaceRepository.findById(1L)).thenReturn(Optional.of(defaultSpace));
            when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> {
                Conversation c = inv.getArgument(0);
                setId(c, 100L);
                return c;
            });

            Conversation result = conversationService.createConversation(1L);

            assertThat(result.getTitle()).isEqualTo("Nouvelle conversation");
            assertThat(result.getThotspace()).isEqualTo(defaultSpace);
            verify(thotspaceRepository).findById(1L);
        }

        @Test
        @DisplayName("cree une conversation dans l'espace par defaut quand thotspaceId est null")
        void withNullThotspaceId_existingDefault() {
            when(thotspaceRepository.findByIsDefaultTrue()).thenReturn(Optional.of(defaultSpace));
            when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> {
                Conversation c = inv.getArgument(0);
                setId(c, 100L);
                return c;
            });

            Conversation result = conversationService.createConversation(null);

            assertThat(result.getThotspace()).isEqualTo(defaultSpace);
            verify(thotspaceRepository).findByIsDefaultTrue();
            verify(thotspaceRepository, never()).findById(anyLong());
        }

        @Test
        @DisplayName("cree l'espace par defaut s'il n'existe pas")
        void withNullThotspaceId_createsDefault() {
            when(thotspaceRepository.findByIsDefaultTrue()).thenReturn(Optional.empty());
            when(thotspaceRepository.save(any(Thotspace.class))).thenAnswer(inv -> {
                Thotspace t = inv.getArgument(0);
                setId(t, 99L);
                return t;
            });
            when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> {
                Conversation c = inv.getArgument(0);
                setId(c, 100L);
                return c;
            });

            Conversation result = conversationService.createConversation(null);

            ArgumentCaptor<Thotspace> captor = ArgumentCaptor.forClass(Thotspace.class);
            verify(thotspaceRepository).save(captor.capture());
            Thotspace created = captor.getValue();
            assertThat(created.getName()).isEqualTo("G\u00e9n\u00e9ral");
            assertThat(created.isDefault()).isTrue();
        }

        @Test
        @DisplayName("lance une exception si le thotspace n'existe pas")
        void withInvalidThotspaceId() {
            when(thotspaceRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> conversationService.createConversation(999L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Thotspace not found");
        }
    }

    // ── renameConversation ───────────────────────────────────────────────

    @Nested
    @DisplayName("renameConversation")
    class RenameConversation {

        @Test
        @DisplayName("renomme une conversation normalement")
        void renameNormal() {
            when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));
            when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Conversation result = conversationService.renameConversation(10L, "Mon nouveau titre");

            assertThat(result.getTitle()).isEqualTo("Mon nouveau titre");
        }

        @Test
        @DisplayName("tronque le titre a 100 caracteres")
        void truncatesLongTitle() {
            String longTitle = "a".repeat(150);
            when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));
            when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Conversation result = conversationService.renameConversation(10L, longTitle);

            assertThat(result.getTitle()).hasSize(100);
        }

        @Test
        @DisplayName("utilise 'Sans titre' si le titre est blank")
        void blankTitleFallback() {
            when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));
            when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Conversation result = conversationService.renameConversation(10L, "   ");

            assertThat(result.getTitle()).isEqualTo("Sans titre");
        }

        @Test
        @DisplayName("utilise 'Sans titre' si le titre est null")
        void nullTitleFallback() {
            when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));
            when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Conversation result = conversationService.renameConversation(10L, null);

            assertThat(result.getTitle()).isEqualTo("Sans titre");
        }
    }

    // ── complete ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("complete")
    class Complete {

        @Test
        @DisplayName("genere une completion via context-engine et sauvegarde l'interaction")
        void completeNormal() {
            when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));
            when(documentService.buildDocumentContext(10L)).thenReturn(null);
            when(interactionRepository.findByConversationIdOrderByCreatedAtAsc(10L))
                    .thenReturn(List.of());
            when(contextEngineClient.analyze(any())).thenReturn(continueResponse());
            when(modelRepository.findById(1L)).thenReturn(Optional.of(defaultModel));
            when(gatewayFactory.getGateway(defaultProvider, "llama3.2:3b")).thenReturn(llmGateway);
            when(llmGateway.generate(any())).thenReturn("Reponse du LLM");
            when(interactionRepository.save(any(LlmInteraction.class))).thenAnswer(inv -> {
                LlmInteraction i = inv.getArgument(0);
                setId(i, 1L);
                return i;
            });

            CompletionResponse result = conversationService.complete(10L, "Bonjour", 1L);

            assertThat(result.getPrompt()).isEqualTo("Bonjour");
            assertThat(result.getResponse()).isEqualTo("Reponse du LLM");
            assertThat(result.getStatus()).isEqualTo("continue");
            verify(contextEngineClient).analyze(any());
            verify(llmGateway).generate(any());
        }

        @Test
        @DisplayName("utilise le modele par defaut si modelId est null")
        void completeWithDefaultModel() {
            when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));
            when(documentService.buildDocumentContext(10L)).thenReturn(null);
            when(interactionRepository.findByConversationIdOrderByCreatedAtAsc(10L))
                    .thenReturn(List.of());
            when(contextEngineClient.analyze(any())).thenReturn(continueResponse());
            when(modelRepository.findFirstByEnabledTrue()).thenReturn(Optional.of(defaultModel));
            when(gatewayFactory.getGateway(defaultProvider, "llama3.2:3b")).thenReturn(llmGateway);
            when(llmGateway.generate(any())).thenReturn("Reponse");
            when(interactionRepository.save(any())).thenAnswer(inv -> {
                LlmInteraction i = inv.getArgument(0);
                setId(i, 1L);
                return i;
            });

            conversationService.complete(10L, "Question", null);

            verify(modelRepository).findFirstByEnabledTrue();
        }

        @Test
        @DisplayName("auto-titre la conversation avec le debut du prompt")
        void autoTitleOnFirstMessage() {
            conversation.setTitle("Nouvelle conversation");
            when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));
            when(documentService.buildDocumentContext(10L)).thenReturn(null);
            when(interactionRepository.findByConversationIdOrderByCreatedAtAsc(10L))
                    .thenReturn(List.of());
            when(contextEngineClient.analyze(any())).thenReturn(continueResponse());
            when(modelRepository.findFirstByEnabledTrue()).thenReturn(Optional.of(defaultModel));
            when(gatewayFactory.getGateway(any(), anyString())).thenReturn(llmGateway);
            when(llmGateway.generate(any())).thenReturn("OK");
            when(interactionRepository.save(any())).thenAnswer(inv -> {
                LlmInteraction i = inv.getArgument(0);
                setId(i, 1L);
                return i;
            });

            conversationService.complete(10L, "Comment fonctionne l'intelligence artificielle ?", null);

            assertThat(conversation.getTitle()).isEqualTo("Comment fonctionne l'intellige...");
            verify(conversationRepository).save(conversation);
        }

        @Test
        @DisplayName("ne modifie pas le titre si deja renomme")
        void doesNotAutoTitleIfAlreadyRenamed() {
            conversation.setTitle("Mon titre custom");
            when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));
            when(documentService.buildDocumentContext(10L)).thenReturn(null);
            when(interactionRepository.findByConversationIdOrderByCreatedAtAsc(10L))
                    .thenReturn(List.of());
            when(contextEngineClient.analyze(any())).thenReturn(continueResponse());
            when(modelRepository.findFirstByEnabledTrue()).thenReturn(Optional.of(defaultModel));
            when(gatewayFactory.getGateway(any(), anyString())).thenReturn(llmGateway);
            when(llmGateway.generate(any())).thenReturn("OK");
            when(interactionRepository.save(any())).thenAnswer(inv -> {
                LlmInteraction i = inv.getArgument(0);
                setId(i, 1L);
                return i;
            });

            conversationService.complete(10L, "Nouveau prompt", null);

            assertThat(conversation.getTitle()).isEqualTo("Mon titre custom");
        }
    }

    // ── complete with web search results ──────────────────────────────────

    @Nested
    @DisplayName("complete with web search")
    class CompleteWithWebSearch {

        @Test
        @DisplayName("injecte les sources web du context-engine dans la reponse")
        void searchAndInjectSources() {
            when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));
            when(documentService.buildDocumentContext(10L)).thenReturn(null);
            when(interactionRepository.findByConversationIdOrderByCreatedAtAsc(10L))
                    .thenReturn(List.of());
            when(contextEngineClient.analyze(any())).thenReturn(webSearchResponse());
            when(modelRepository.findFirstByEnabledTrue()).thenReturn(Optional.of(defaultModel));
            when(gatewayFactory.getGateway(any(), anyString())).thenReturn(llmGateway);
            when(llmGateway.generate(any())).thenReturn("Reponse avec sources [1]");
            when(interactionRepository.save(any(LlmInteraction.class))).thenAnswer(inv -> {
                LlmInteraction i = inv.getArgument(0);
                setId(i, 1L);
                return i;
            });

            CompletionResponse result = conversationService.complete(10L, "question", null);

            assertThat(result.getResponse()).contains("sources [1]");
            assertThat(result.getStatus()).isEqualTo("continue");
            verify(contextEngineClient).analyze(any());

            ArgumentCaptor<LlmInteraction> captor = ArgumentCaptor.forClass(LlmInteraction.class);
            verify(interactionRepository).save(captor.capture());
            LlmInteraction saved = captor.getValue();
            assertThat(saved.getSources()).hasSize(1);
            assertThat(saved.getSources().get(0).getSourceUrl()).isEqualTo("https://example.com");
            assertThat(saved.getSources().get(0).getExtractedText()).isEqualTo("Contenu extrait");
        }
    }

    // ── clarification (context-engine interrupts) ────────────────────────

    @Nested
    @DisplayName("clarification")
    class Clarification {

        @Test
        @DisplayName("retourne la clarification du context-engine sans appeler le LLM ni persister")
        void returnsContextEngineClarification() {
            when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));
            when(documentService.buildDocumentContext(10L)).thenReturn(null);
            when(interactionRepository.findByConversationIdOrderByCreatedAtAsc(10L))
                    .thenReturn(List.of());
            when(contextEngineClient.analyze(any())).thenReturn(clarificationResponse());

            CompletionResponse result = conversationService.complete(10L, "Aide-moi", null);

            assertThat(result.getStatus()).isEqualTo("clarification_needed");
            assertThat(result.getClarificationMessage()).isEqualTo("Votre question manque de precision.");
            assertThat(result.getSuggestions()).containsExactly("Question 1 ?", "Question 2 ?");
            verify(llmGateway, never()).generate(any());
            verify(interactionRepository, never()).save(any());
        }
    }

    // ── deleteConversation ───────────────────────────────────────────────

    @Nested
    @DisplayName("deleteConversation")
    class DeleteConversation {

        @Test
        @DisplayName("supprime documents, interactions et conversation en cascade")
        void deleteCascade() {
            conversationService.deleteConversation(10L);

            verify(documentRepository).deleteByConversationId(10L);
            verify(interactionRepository).deleteByConversationId(10L);
            verify(conversationRepository).deleteById(10L);
        }
    }

    // ── getConversations ─────────────────────────────────────────────────

    @Nested
    @DisplayName("getConversations")
    class GetConversations {

        @Test
        @DisplayName("retourne les conversations filtrees par thotspace")
        void withThotspaceFilter() {
            when(conversationRepository.findByThotspaceIdOrderByCreatedAtDesc(1L))
                    .thenReturn(List.of(conversation));

            List<Conversation> result = conversationService.getConversations(1L);

            assertThat(result).hasSize(1);
            verify(conversationRepository).findByThotspaceIdOrderByCreatedAtDesc(1L);
        }

        @Test
        @DisplayName("retourne toutes les conversations si pas de filtre")
        void withoutFilter() {
            when(conversationRepository.findAllByOrderByCreatedAtDesc())
                    .thenReturn(List.of(conversation));

            List<Conversation> result = conversationService.getConversations(null);

            assertThat(result).hasSize(1);
            verify(conversationRepository).findAllByOrderByCreatedAtDesc();
        }
    }

}
