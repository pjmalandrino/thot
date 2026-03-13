package com.example.chatinterface.admin;

import com.example.chatinterface.llm.LlmGatewayFactory;
import com.example.chatinterface.llm.LlmModel;
import com.example.chatinterface.llm.LlmModelRepository;
import com.example.chatinterface.llm.LlmProvider;
import com.example.chatinterface.llm.LlmProviderRepository;
import com.example.chatinterface.llm.ModelResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class LlmAdminController {

    private final LlmProviderRepository providerRepository;
    private final LlmModelRepository modelRepository;
    private final LlmGatewayFactory gatewayFactory;

    public LlmAdminController(
            LlmProviderRepository providerRepository,
            LlmModelRepository modelRepository,
            LlmGatewayFactory gatewayFactory
    ) {
        this.providerRepository = providerRepository;
        this.modelRepository = modelRepository;
        this.gatewayFactory = gatewayFactory;
    }

    // ── Providers ──────────────────────────────────────────────────────────────

    @GetMapping("/providers")
    public List<ProviderResponse> getProviders() {
        return providerRepository.findAll().stream()
                .map(ProviderResponse::from)
                .toList();
    }

    @PostMapping("/providers")
    @ResponseStatus(HttpStatus.CREATED)
    public ProviderResponse createProvider(@RequestBody ProviderRequest request) {
        LlmProvider p = new LlmProvider();
        p.setName(request.getName());
        p.setType(request.getType());
        p.setBaseUrl(request.getBaseUrl());
        p.setApiKey(request.getApiKey());
        if (request.getEnabled() != null) p.setEnabled(request.getEnabled());
        return ProviderResponse.from(providerRepository.save(p));
    }

    @PutMapping("/providers/{id}")
    public ProviderResponse updateProvider(@PathVariable Long id, @RequestBody ProviderRequest request) {
        LlmProvider p = providerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Provider not found: " + id));

        if (request.getName() != null) p.setName(request.getName());
        if (request.getType() != null) p.setType(request.getType());
        if (request.getBaseUrl() != null) p.setBaseUrl(request.getBaseUrl());
        if (request.getApiKey() != null) p.setApiKey(request.getApiKey());
        if (request.getEnabled() != null) p.setEnabled(request.getEnabled());

        ProviderResponse response = ProviderResponse.from(providerRepository.save(p));
        gatewayFactory.invalidate(id);   // vider le cache pour ce provider
        return response;
    }

    @DeleteMapping("/providers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProvider(@PathVariable Long id) {
        modelRepository.findByProviderId(id).forEach(m -> modelRepository.deleteById(m.getId()));
        providerRepository.deleteById(id);
        gatewayFactory.invalidate(id);
    }

    // ── Models ─────────────────────────────────────────────────────────────────

    @GetMapping("/providers/{id}/models")
    public List<ModelResponse> getModels(@PathVariable Long id) {
        return modelRepository.findByProviderId(id).stream()
                .map(ModelResponse::from)
                .toList();
    }

    @PostMapping("/providers/{id}/models")
    @ResponseStatus(HttpStatus.CREATED)
    public ModelResponse addModel(@PathVariable Long id, @RequestBody ModelRequest request) {
        LlmProvider provider = providerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Provider not found: " + id));

        LlmModel model = new LlmModel();
        model.setProvider(provider);
        model.setModelName(request.getModelName());
        model.setDisplayName(request.getDisplayName());
        if (request.getEnabled() != null) model.setEnabled(request.getEnabled());
        return ModelResponse.from(modelRepository.save(model));
    }

    @PutMapping("/models/{id}")
    public ModelResponse updateModel(@PathVariable Long id, @RequestBody ModelRequest request) {
        LlmModel model = modelRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Model not found: " + id));

        if (request.getModelName() != null) model.setModelName(request.getModelName());
        if (request.getDisplayName() != null) model.setDisplayName(request.getDisplayName());
        if (request.getEnabled() != null) model.setEnabled(request.getEnabled());

        return ModelResponse.from(modelRepository.save(model));
    }

    @DeleteMapping("/models/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteModel(@PathVariable Long id) {
        modelRepository.deleteById(id);
    }
}
