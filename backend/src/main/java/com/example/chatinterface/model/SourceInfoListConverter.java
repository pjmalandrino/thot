package com.example.chatinterface.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class SourceInfoListConverter implements AttributeConverter<List<SourceInfo>, String> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<SourceInfo> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        try {
            return mapper.writeValueAsString(sources);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Impossible de sérialiser les sources en JSON", e);
        }
    }

    @Override
    public List<SourceInfo> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Impossible de désérialiser les sources depuis JSON", e);
        }
    }
}
