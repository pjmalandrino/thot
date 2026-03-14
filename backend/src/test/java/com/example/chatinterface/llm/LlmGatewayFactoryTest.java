package com.example.chatinterface.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.example.chatinterface.TestEntityHelper.setId;
import static org.assertj.core.api.Assertions.assertThat;

class LlmGatewayFactoryTest {

    private LlmGatewayFactory factory;
    private LlmProvider ollamaProvider;
    private LlmProvider mistralProvider;

    @BeforeEach
    void setUp() {
        factory = new LlmGatewayFactory();

        ollamaProvider = new LlmProvider();
        ollamaProvider.setName("ollama-local");
        ollamaProvider.setType(LlmProviderType.OLLAMA);
        ollamaProvider.setBaseUrl("http://localhost:11434");
        setId(ollamaProvider, 1L);

        mistralProvider = new LlmProvider();
        mistralProvider.setName("mistral-cloud");
        mistralProvider.setType(LlmProviderType.MISTRAL);
        mistralProvider.setApiKey("test-api-key");
        setId(mistralProvider, 2L);
    }

    @Test
    @DisplayName("cree un OllamaGateway pour un provider OLLAMA")
    void createOllamaGateway() {
        LlmGateway gateway = factory.getGateway(ollamaProvider, "llama3.2:3b");

        assertThat(gateway).isInstanceOf(OllamaGateway.class);
    }

    @Test
    @DisplayName("cree un MistralGateway pour un provider MISTRAL")
    void createMistralGateway() {
        LlmGateway gateway = factory.getGateway(mistralProvider, "mistral-small-latest");

        assertThat(gateway).isInstanceOf(MistralGateway.class);
    }

    @Test
    @DisplayName("retourne la meme instance en cache pour la meme cle")
    void cachesSameInstance() {
        LlmGateway first = factory.getGateway(ollamaProvider, "llama3.2:3b");
        LlmGateway second = factory.getGateway(ollamaProvider, "llama3.2:3b");

        assertThat(first).isSameAs(second);
    }

    @Test
    @DisplayName("cree des instances differentes pour des modeles differents")
    void differentModelsGetDifferentInstances() {
        LlmGateway first = factory.getGateway(ollamaProvider, "llama3.2:3b");
        LlmGateway second = factory.getGateway(ollamaProvider, "llama3.2:1b");

        assertThat(first).isNotSameAs(second);
    }

    @Test
    @DisplayName("cree des instances differentes pour des providers differents")
    void differentProvidersGetDifferentInstances() {
        LlmGateway first = factory.getGateway(ollamaProvider, "model-a");
        LlmGateway second = factory.getGateway(mistralProvider, "model-a");

        assertThat(first).isNotSameAs(second);
    }

    @Test
    @DisplayName("invalidate() supprime les entrees du cache pour un provider")
    void invalidateRemovesCachedEntries() {
        LlmGateway before = factory.getGateway(ollamaProvider, "llama3.2:3b");
        factory.invalidate(1L);
        LlmGateway after = factory.getGateway(ollamaProvider, "llama3.2:3b");

        assertThat(before).isNotSameAs(after);
    }

    @Test
    @DisplayName("invalidate() ne touche pas les autres providers")
    void invalidateDoesNotAffectOtherProviders() {
        LlmGateway mistral = factory.getGateway(mistralProvider, "mistral-small-latest");
        factory.invalidate(1L); // Invalide OLLAMA seulement
        LlmGateway mistralAfter = factory.getGateway(mistralProvider, "mistral-small-latest");

        assertThat(mistral).isSameAs(mistralAfter);
    }

}
