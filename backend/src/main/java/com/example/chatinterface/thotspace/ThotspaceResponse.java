package com.example.chatinterface.thotspace;

import java.time.LocalDateTime;

public class ThotspaceResponse {

    private Long id;
    private String name;
    private String description;
    private String systemPrompt;
    private boolean isDefault;
    private LocalDateTime createdAt;

    public static ThotspaceResponse from(Thotspace space) {
        ThotspaceResponse r = new ThotspaceResponse();
        r.id = space.getId();
        r.name = space.getName();
        r.description = space.getDescription();
        r.systemPrompt = space.getSystemPrompt();
        r.isDefault = space.isDefault();
        r.createdAt = space.getCreatedAt();
        return r;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getSystemPrompt() { return systemPrompt; }
    public boolean isDefault() { return isDefault; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
