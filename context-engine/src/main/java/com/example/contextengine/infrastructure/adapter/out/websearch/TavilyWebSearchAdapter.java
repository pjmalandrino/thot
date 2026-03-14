package com.example.contextengine.infrastructure.adapter.out.websearch;

import com.example.contextengine.domain.model.SearchResult;
import com.example.contextengine.domain.port.out.WebSearchPort;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TavilyWebSearchAdapter implements WebSearchPort {

    private static final Logger log = LoggerFactory.getLogger(TavilyWebSearchAdapter.class);

    private final TavilyWebSearchEngine searchEngine;
    private final TavilyExtractClient extractClient;
    private final int maxResults;
    private final int maxExtractUrls;
    private final int maxContentLength;

    public TavilyWebSearchAdapter(TavilyWebSearchEngine searchEngine,
                                  TavilyExtractClient extractClient,
                                  int maxResults,
                                  int maxExtractUrls,
                                  int maxContentLength) {
        this.searchEngine = searchEngine;
        this.extractClient = extractClient;
        this.maxResults = maxResults;
        this.maxExtractUrls = maxExtractUrls;
        this.maxContentLength = maxContentLength;
    }

    @Override
    public List<SearchResult> searchAndExtract(String query) {
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

        List<WebSearchOrganicResult> selected = deduplicateByUrl(organicResults).stream()
                .limit(maxExtractUrls)
                .toList();

        List<String> urls = selected.stream()
                .map(r -> r.url().toString())
                .toList();

        log.info("[EXTRACT] Extracting content from {} URLs", urls.size());
        List<TavilyExtractClient.ExtractResult> extractResults = extractClient.extract(urls);
        log.info("[EXTRACT] Got {} successful extractions", extractResults.size());

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

    @Override
    public String buildContextPrompt(List<SearchResult> results) {
        if (results.isEmpty()) {
            return "Aucune source web pertinente n'a pu etre extraite. "
                    + "Indique clairement a l'utilisateur qu'aucune source fiable n'a ete trouvee.";
        }

        StringBuilder sb = new StringBuilder();
        for (SearchResult r : results) {
            sb.append(r.citationId()).append(" ")
                    .append(r.sourceTitle()).append(" — ")
                    .append(r.sourceUrl()).append("\n")
                    .append(r.extractedText())
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
