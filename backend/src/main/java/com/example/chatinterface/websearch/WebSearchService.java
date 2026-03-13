package com.example.chatinterface.websearch;

import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WebSearchService {

    private static final Logger log = LoggerFactory.getLogger(WebSearchService.class);

    private final TavilyWebSearchEngine searchEngine;
    private final TavilyExtractClient extractClient;
    private final int maxResults;
    private final int maxExtractUrls;
    private final int maxContentLength;

    public WebSearchService(
            TavilyWebSearchEngine searchEngine,
            TavilyExtractClient extractClient,
            @Value("${tavily.max-results:5}") int maxResults,
            @Value("${tavily.max-extract-urls:3}") int maxExtractUrls,
            @Value("${tavily.max-content-length:4000}") int maxContentLength
    ) {
        this.searchEngine = searchEngine;
        this.extractClient = extractClient;
        this.maxResults = maxResults;
        this.maxExtractUrls = maxExtractUrls;
        this.maxContentLength = maxContentLength;
    }

    public List<SearchResult> searchAndExtract(String query) {
        // 1. Search
        log.info("[SEARCH] Web search for: {}", query);
        WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms(query)
                .maxResults(maxResults)
                .build();

        WebSearchResults searchResults = searchEngine.search(request);
        List<WebSearchOrganicResult> organicResults = searchResults.results();

        if (organicResults == null || organicResults.isEmpty()) {
            log.warn("[SEARCH] No results for query: {}", query);
            return List.of();
        }

        for (int i = 0; i < organicResults.size(); i++) {
            WebSearchOrganicResult r = organicResults.get(i);
            log.info("[SEARCH] Result #{}: {} | {}", i + 1, r.title(), r.url());
        }

        // 2. Deduplicate & select top N for extraction
        List<WebSearchOrganicResult> selected = deduplicateByUrl(organicResults).stream()
                .limit(maxExtractUrls)
                .toList();

        // 3. Extract full content
        List<String> urls = selected.stream()
                .map(r -> r.url().toString())
                .toList();

        log.info("[EXTRACT] Extracting content from {} URLs", urls.size());
        List<TavilyExtractClient.ExtractResult> extractResults = extractClient.extract(urls);
        log.info("[EXTRACT] Got {} successful extractions", extractResults.size());

        // 4. Build enriched results
        Map<String, TavilyExtractClient.ExtractResult> extractMap = extractResults.stream()
                .collect(Collectors.toMap(
                        TavilyExtractClient.ExtractResult::getUrl,
                        Function.identity(),
                        (a, b) -> a
                ));

        List<SearchResult> results = new ArrayList<>();
        for (WebSearchOrganicResult result : selected) {
            String url = result.url().toString();
            TavilyExtractClient.ExtractResult extract = extractMap.get(url);

            if (extract == null || extract.getRawContent() == null || extract.getRawContent().isBlank()) {
                log.warn("[EXTRACT] No content for URL: {}", url);
                continue;
            }

            String content = truncate(extract.getRawContent(), maxContentLength);
            results.add(new SearchResult(
                    "[" + (results.size() + 1) + "]",
                    url,
                    result.title(),
                    content
            ));
        }

        log.info("[SEARCH] Built {} enriched results", results.size());
        return results;
    }

    public String buildContextPrompt(List<SearchResult> results) {
        if (results.isEmpty()) {
            return "Aucune source web pertinente n'a pu etre extraite. "
                    + "Indique clairement a l'utilisateur qu'aucune source fiable n'a ete trouvee.";
        }

        StringBuilder sb = new StringBuilder();
        for (SearchResult r : results) {
            sb.append(r.getCitationId()).append(" ")
                    .append(r.getSourceTitle()).append(" — ")
                    .append(r.getSourceUrl()).append("\n")
                    .append(r.getExtractedText())
                    .append("\n\n");
        }
        return sb.toString();
    }

    private List<WebSearchOrganicResult> deduplicateByUrl(List<WebSearchOrganicResult> results) {
        Set<String> seen = new LinkedHashSet<>();
        return results.stream()
                .filter(r -> seen.add(r.url().toString()))
                .toList();
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "\n[...contenu tronque]";
    }
}
