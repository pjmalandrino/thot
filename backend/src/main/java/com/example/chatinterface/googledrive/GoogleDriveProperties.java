package com.example.chatinterface.googledrive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "google.drive")
public class GoogleDriveProperties {

    private String clientId = "";
    private String clientSecret = "";
    private String redirectUri = "http://localhost:8081/api/drive/callback";
    private String scope = "https://www.googleapis.com/auth/drive.readonly";
    private String frontendRedirectUri = "http://localhost:3000";
    private int maxSearchResults = 10;
    private int maxExtractFiles = 3;
    private int maxContentLength = 4000;

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getFrontendRedirectUri() { return frontendRedirectUri; }
    public void setFrontendRedirectUri(String frontendRedirectUri) { this.frontendRedirectUri = frontendRedirectUri; }

    public int getMaxSearchResults() { return maxSearchResults; }
    public void setMaxSearchResults(int maxSearchResults) { this.maxSearchResults = maxSearchResults; }

    public int getMaxExtractFiles() { return maxExtractFiles; }
    public void setMaxExtractFiles(int maxExtractFiles) { this.maxExtractFiles = maxExtractFiles; }

    public int getMaxContentLength() { return maxContentLength; }
    public void setMaxContentLength(int maxContentLength) { this.maxContentLength = maxContentLength; }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }
}
