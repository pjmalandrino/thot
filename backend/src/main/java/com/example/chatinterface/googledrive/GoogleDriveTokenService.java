package com.example.chatinterface.googledrive;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class GoogleDriveTokenService {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveTokenService.class);
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_REVOKE_URL = "https://oauth2.googleapis.com/revoke";

    private final GoogleDriveTokenRepository tokenRepository;
    private final GoogleDriveProperties properties;
    private final RestClient restClient;

    public GoogleDriveTokenService(GoogleDriveTokenRepository tokenRepository,
                                   GoogleDriveProperties properties) {
        this.tokenRepository = tokenRepository;
        this.properties = properties;
        this.restClient = RestClient.builder().build();
    }

    /**
     * Exchange authorization code for tokens and persist them.
     */
    @Transactional
    public void exchangeCodeAndStore(String userId, String code) {
        log.info("[DRIVE-TOKEN] Exchanging auth code for user '{}'", userId);

        String body = "code=" + code
                + "&client_id=" + properties.getClientId()
                + "&client_secret=" + properties.getClientSecret()
                + "&redirect_uri=" + properties.getRedirectUri()
                + "&grant_type=authorization_code";

        GoogleTokenResponse response = restClient.post()
                .uri(GOOGLE_TOKEN_URL)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(body)
                .retrieve()
                .body(GoogleTokenResponse.class);

        if (response == null || response.accessToken == null) {
            throw new RuntimeException("Failed to exchange authorization code");
        }

        storeTokens(userId, response.accessToken, response.refreshToken,
                response.expiresIn, response.scope);

        log.info("[DRIVE-TOKEN] Tokens stored for user '{}'", userId);
    }

    /**
     * Get a valid access token, refreshing if needed.
     */
    public String getValidAccessToken(String userId) {
        GoogleDriveToken token = tokenRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("No Google Drive token for user: " + userId));

        // Refresh if expiring in the next 60 seconds
        if (token.getExpiresAt().isBefore(LocalDateTime.now().plusSeconds(60))) {
            log.info("[DRIVE-TOKEN] Token expired for user '{}', refreshing", userId);
            refreshToken(token);
        }

        return token.getAccessToken();
    }

    /**
     * Revoke tokens with Google and delete from DB.
     */
    @Transactional
    public void revokeTokens(String userId) {
        tokenRepository.findByUserId(userId).ifPresent(token -> {
            try {
                restClient.post()
                        .uri(GOOGLE_REVOKE_URL + "?token=" + token.getAccessToken())
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .retrieve()
                        .toBodilessEntity();
                log.info("[DRIVE-TOKEN] Revoked tokens for user '{}'", userId);
            } catch (Exception e) {
                log.warn("[DRIVE-TOKEN] Revocation failed (token may already be invalid): {}", e.getMessage());
            }
            tokenRepository.deleteByUserId(userId);
        });
    }

    public boolean isConnected(String userId) {
        return tokenRepository.existsByUserId(userId);
    }

    /**
     * Extract email from the Google access token (JWT payload).
     * Falls back to empty string if token is opaque or missing email claim.
     */
    public String getConnectedEmail(String userId) {
        return tokenRepository.findByUserId(userId)
                .map(token -> extractEmailFromToken(token.getAccessToken()))
                .orElse("");
    }

    /**
     * Build the Google OAuth2 authorization URL.
     */
    public String buildAuthorizationUrl(String userId) {
        return "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + properties.getClientId()
                + "&redirect_uri=" + properties.getRedirectUri()
                + "&response_type=code"
                + "&scope=" + properties.getScope()
                + "&access_type=offline"
                + "&prompt=consent"
                + "&state=" + userId;
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    @Transactional
    void storeTokens(String userId, String accessToken, String refreshToken,
                     long expiresIn, String scope) {
        GoogleDriveToken token = tokenRepository.findByUserId(userId)
                .orElse(new GoogleDriveToken(userId, accessToken,
                        refreshToken != null ? refreshToken : "",
                        LocalDateTime.now().plusSeconds(expiresIn),
                        scope));

        token.setAccessToken(accessToken);
        if (refreshToken != null && !refreshToken.isBlank()) {
            token.setRefreshToken(refreshToken);
        }
        token.setExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
        token.setUpdatedAt(LocalDateTime.now());

        tokenRepository.save(token);
    }

    private void refreshToken(GoogleDriveToken token) {
        String body = "refresh_token=" + token.getRefreshToken()
                + "&client_id=" + properties.getClientId()
                + "&client_secret=" + properties.getClientSecret()
                + "&grant_type=refresh_token";

        try {
            GoogleTokenResponse response = restClient.post()
                    .uri(GOOGLE_TOKEN_URL)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body(body)
                    .retrieve()
                    .body(GoogleTokenResponse.class);

            if (response != null && response.accessToken != null) {
                token.setAccessToken(response.accessToken);
                token.setExpiresAt(LocalDateTime.now().plusSeconds(response.expiresIn));
                token.setUpdatedAt(LocalDateTime.now());
                tokenRepository.save(token);
                log.info("[DRIVE-TOKEN] Token refreshed for user '{}'", token.getUserId());
            }
        } catch (Exception e) {
            log.error("[DRIVE-TOKEN] Failed to refresh token for user '{}': {}",
                    token.getUserId(), e.getMessage());
            throw new RuntimeException("Failed to refresh Google Drive token", e);
        }
    }

    private String extractEmailFromToken(String accessToken) {
        try {
            // Google access tokens are opaque (not JWT), so we call the userinfo endpoint
            var response = restClient.get()
                    .uri("https://www.googleapis.com/oauth2/v3/userinfo")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(GoogleUserInfo.class);
            return response != null && response.email != null ? response.email : "";
        } catch (Exception e) {
            log.debug("[DRIVE-TOKEN] Could not extract email: {}", e.getMessage());
            return "";
        }
    }

    // ── DTOs ────────────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class GoogleTokenResponse {
        @JsonProperty("access_token")
        public String accessToken;
        @JsonProperty("refresh_token")
        public String refreshToken;
        @JsonProperty("expires_in")
        public long expiresIn;
        @JsonProperty("scope")
        public String scope;
        @JsonProperty("token_type")
        public String tokenType;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class GoogleUserInfo {
        @JsonProperty("email")
        public String email;
        @JsonProperty("name")
        public String name;
    }
}
