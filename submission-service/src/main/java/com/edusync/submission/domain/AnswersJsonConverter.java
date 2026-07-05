package com.edusync.submission.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;
import java.util.Map;

/**
 * Serializes {@code Submission#answers} to/from a single JSON text column.
 *
 * WHY a JSON column instead of a normalized relational schema: a submission's
 * answers are an arbitrary, per-assessment-defined shape (short answer, MCQ
 * selection, nested rubric responses, etc. — see the original controller's
 * recursive {@code appendValue}, which already had to handle String, Number,
 * nested Map, and nested Collection values). assessment-service owns the
 * question schema; submission-service only needs to store what was submitted
 * and derive comparison text from it, not query into individual answer
 * fields. Modeling this as N answer/question/value tables would require
 * either a rigid schema this service doesn't own, or an EAV-style table that
 * is harder to reason about than a single documented JSON column — so a JSON
 * column is a deliberate, bounded modeling choice here, not the same defect
 * as the in-memory {@code ConcurrentHashMap<String, Submission>} it replaces
 * (this data is now durable, queryable by id/assessment, and indexed).
 *
 * WHY a static ObjectMapper instead of an injected Spring bean: JPA
 * AttributeConverters are instantiated by the persistence provider
 * (Hibernate) via reflection, not by the Spring container, so constructor
 * injection is not available here. Jackson's ObjectMapper is documented as
 * thread-safe once configured, so a single static instance is the idiomatic,
 * safe pattern for this class of converter.
 */
@Converter
public class AnswersJsonConverter implements AttributeConverter<List<Map<String, Object>>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> ANSWERS_TYPE = new TypeReference<>() {
    };
    private static final String EMPTY_JSON_ARRAY = "[]";

    @Override
    public String convertToDatabaseColumn(List<Map<String, Object>> attribute) {
        if (attribute == null) {
            return EMPTY_JSON_ARRAY;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            // WHY IllegalStateException and not silently storing an empty/partial
            // value: failing loudly here prevents silently persisting corrupted or
            // truncated academic submission data (fail securely / defensive
            // programming), which would be far worse than a request failing with
            // a 500 that a GlobalExceptionHandler still turns into a clean,
            // non-leaking error response.
            throw new IllegalStateException("Failed to serialize submission answers to JSON", e);
        }
    }

    @Override
    public List<Map<String, Object>> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(dbData, ANSWERS_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize submission answers from JSON", e);
        }
    }
}
