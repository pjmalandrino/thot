package com.example.chatinterface.config;

import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebSearchConfig {

    @Bean
    public TavilyWebSearchEngine tavilyWebSearchEngine(
            @Value("${tavily.api-key}") String apiKey,
            @Value("${tavily.search-depth:basic}") String searchDepth
    ) {
        return TavilyWebSearchEngine.builder()
                .apiKey(apiKey)
                .searchDepth(searchDepth)
                .includeAnswer(false)
                .includeRawContent(false)
                .build();
    }
}
