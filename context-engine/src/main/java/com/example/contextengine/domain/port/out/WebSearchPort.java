package com.example.contextengine.domain.port.out;

import com.example.contextengine.domain.model.SearchResult;

import java.util.List;

public interface WebSearchPort {

    List<SearchResult> searchAndExtract(String query);

    String buildContextPrompt(List<SearchResult> results);
}
