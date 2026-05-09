package com.example.aichinese.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.learning.ListeningExercisePayload;
import com.example.aichinese.dto.learning.ListeningOptionPayload;
import com.example.aichinese.dto.learning.ListeningOptionRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@Service
public class LearningPayloadCodec {

    private final ObjectMapper objectMapper;

    public LearningPayloadCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String serializeListeningPayload(
            String chineseText,
            String pinyin,
            String explanation,
            List<ListeningOptionRequest> options) {
        ListeningExercisePayload payload = new ListeningExercisePayload(
                chineseText,
                pinyin,
                explanation,
                options == null ? List.of() : options.stream()
                        .map(option -> new ListeningOptionPayload(
                                option.optionText(),
                                option.correctOption(),
                                option.displayOrder() == null ? 0 : option.displayOrder()))
                        .toList());

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Unable to store listening exercise payload.");
        }
    }

    public ListeningExercisePayload deserializeListeningPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return new ListeningExercisePayload(null, null, null, List.of());
        }

        try {
            ListeningExercisePayload decoded = objectMapper.readValue(payload, ListeningExercisePayload.class);
            return decoded == null
                    ? new ListeningExercisePayload(null, null, null, List.of())
                    : new ListeningExercisePayload(
                            decoded.chineseText(),
                            decoded.pinyin(),
                            decoded.explanation(),
                            decoded.options() == null ? List.of() : decoded.options());
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Stored listening exercise payload is invalid.");
        }
    }
}
