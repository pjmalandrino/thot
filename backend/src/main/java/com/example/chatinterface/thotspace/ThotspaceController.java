package com.example.chatinterface.thotspace;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.chatinterface.conversation.ConversationRepository;

import java.util.List;

@RestController
@RequestMapping("/api/thotspaces")
public class ThotspaceController {

    private final ThotspaceRepository thotspaceRepository;
    private final ConversationRepository conversationRepository;

    public ThotspaceController(ThotspaceRepository thotspaceRepository,
                               ConversationRepository conversationRepository) {
        this.thotspaceRepository = thotspaceRepository;
        this.conversationRepository = conversationRepository;
    }

    @GetMapping
    public List<ThotspaceResponse> getAll() {
        return thotspaceRepository.findAllByOrderByCreatedAtAsc().stream()
                .map(ThotspaceResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ThotspaceResponse create(@RequestBody ThotspaceRequest request) {
        Thotspace space = new Thotspace(request.getName());
        space.setDescription(request.getDescription());
        space.setSystemPrompt(request.getSystemPrompt());
        return ThotspaceResponse.from(thotspaceRepository.save(space));
    }

    @PutMapping("/{id}")
    public ThotspaceResponse update(@PathVariable Long id, @RequestBody ThotspaceRequest request) {
        Thotspace space = thotspaceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Thotspace not found"));
        if (request.getName() != null) space.setName(request.getName());
        if (request.getDescription() != null) space.setDescription(request.getDescription());
        if (request.getSystemPrompt() != null) space.setSystemPrompt(request.getSystemPrompt());
        return ThotspaceResponse.from(thotspaceRepository.save(space));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable Long id) {
        Thotspace space = thotspaceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Thotspace not found"));
        if (space.isDefault()) {
            throw new IllegalStateException("Cannot delete the default thotspace");
        }
        Thotspace defaultSpace = thotspaceRepository.findByIsDefaultTrue()
                .orElseThrow(() -> new RuntimeException("Default thotspace not found"));
        conversationRepository.reassignThotspace(id, defaultSpace.getId());
        thotspaceRepository.delete(space);
    }
}
