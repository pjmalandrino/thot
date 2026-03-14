package com.example.contextengine.infrastructure.config;

import com.example.contextengine.domain.port.out.WebSearchPort;
import com.example.contextengine.infrastructure.adapter.out.websearch.TavilyExtractClient;
import com.example.contextengine.infrastructure.adapter.out.websearch.TavilyWebSearchAdapter;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebSearchConfig {

    @Bean
    public WebSearchPort webSearchPort(
            @Value("${tavily.api-key}") String apiKey,
            @Value("${tavily.search-depth:basic}") String searchDepth,
            @Value("${tavily.max-results:5}") int maxResults,
            @Value("${tavily.max-extract-urls:3}") int maxExtractUrls,
            @Value("${tavily.max-content-length:4000}") int maxContentLength
    ) {
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
