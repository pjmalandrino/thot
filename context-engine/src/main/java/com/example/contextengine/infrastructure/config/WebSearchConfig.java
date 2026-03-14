package com.example.contextengine.infrastructure.config;

import com.example.contextengine.domain.model.SearchResult;
import com.example.contextengine.domain.port.out.WebSearchPort;
import com.example.contextengine.infrastructure.adapter.out.websearch.TavilyExtractClient;
import com.example.contextengine.infrastructure.adapter.out.websearch.TavilyWebSearchAdapter;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class WebSearchConfig {

    private static final Logger log = LoggerFactory.getLogger(WebSearchConfig.class);

    @Bean
    public WebSearchPort webSearchPort(
            @Value("${tavily.api-key:}") String apiKey,
            @Value("${tavily.search-depth:basic}") String searchDepth,
            @Value("${tavily.max-results:5}") int maxResults,
            @Value("${tavily.max-extract-urls:3}") int maxExtractUrls,
            @Value("${tavily.max-content-length:4000}") int maxContentLength
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[CONFIG] No Tavily API key configured, web search will return empty results");
            return new WebSearchPort() {
                @Override
                public List<SearchResult> searchAndExtract(String query) {
                    log.info("[SEARCH] Web search disabled (no API key), returning empty for: {}", query);
                    return List.of();
                }

                @Override
                public String buildContextPrompt(List<SearchResult> results) {
                    return "";
                }
            };
        }

        TavilyWebSearchEngine searchEngine = TavilyWebSearchEngine.builder()
                .apiKey(apiKey)
                .searchDepth(searchDepth)
                .includeAnswer(false)
                .includeRawContent(false)
                .build();

        TavilyExtractClient extractClient = new TavilyExtractClient(apiKey);

        return new TavilyWebSearchAdapter(searchEngine, extractClient,
                maxResults, maxExtractUrls, maxContentLength);
    }
}
