package com.example.chatinterface.googledrive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/drive")
public class GoogleDriveController {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveController.class);

    private final GoogleDriveTokenService tokenService;
    private final GoogleDriveProperties properties;

    public GoogleDriveController(GoogleDriveTokenService tokenService,
                                 GoogleDriveProperties properties) {
        this.tokenService = tokenService;
        this.properties = properties;
    }

    /**
     * Returns the Google OAuth2 authorization URL for the frontend to redirect to.
     */
    @GetMapping("/connect")
    public ResponseEntity<?> connect(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        log.info("[DRIVE] Connect request from user '{}'", userId);

        if (!properties.isConfigured()) {
            log.warn("[DRIVE] Google Drive not configured (missing client ID/secret)");
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Google Drive is not configured on this server"));
        }

        String authUrl = tokenService.buildAuthorizationUrl(userId);
        return ResponseEntity.ok(Map.of("redirectUrl", authUrl));
    }

    /**
     * OAuth2 callback from Google. Receives authorization code, exchanges for tokens,
     * stores them, and redirects to frontend.
     * This endpoint is PUBLIC (no JWT required) — Google redirects the browser here.
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam String code, @RequestParam String state) {
        String userId = state;
        log.info("[DRIVE] OAuth callback for user '{}'", userId);

        try {
            tokenService.exchangeCodeAndStore(userId, code);
            String redirectUrl = properties.getFrontendRedirectUri() + "?driveConnected=true";
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(redirectUrl))
                    .build();
        } catch (Exception e) {
            log.error("[DRIVE] OAuth callback failed: {}", e.getMessage());
            String errorUrl = properties.getFrontendRedirectUri() + "?driveError=true";
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(errorUrl))
                    .build();
        }
    }

    /**
     * Disconnect Google Drive — revoke tokens and delete from DB.
     */
    @PostMapping("/disconnect")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        log.info("[DRIVE] Disconnect request from user '{}'", userId);
        tokenService.revokeTokens(userId);
    }

    /**
     * Check Drive connection status for the current user.
     */
    @GetMapping("/status")
    public Map<String, Object> status(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        boolean configured = properties.isConfigured();
        boolean connected = configured && tokenService.isConnected(userId);
        String email = connected ? tokenService.getConnectedEmail(userId) : "";
        return Map.of(
                "configured", configured,
                "connected", connected,
                "email", email
        );
    }
}
