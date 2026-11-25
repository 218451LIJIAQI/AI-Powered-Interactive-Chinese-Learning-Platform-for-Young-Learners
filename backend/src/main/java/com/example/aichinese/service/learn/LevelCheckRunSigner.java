package com.example.aichinese.service.learn;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.SecurityProperties;
import com.example.aichinese.dto.learn.LevelCheckNextRequest.Answer;
import com.example.aichinese.entity.enums.MandarinLevel;

/**
 * Signs the stateless adaptive level-check run so each step and the final plan placement can verify that the answers
 * belong to this learner, started from this level, followed a sequence of questions issued by the server for this run,
 * and stay valid within {@link #RUN_TTL}. Because runs are stateless (no server-side per-run table), each token binds
 * the accumulated answer transcript and next issued question via HMAC.
 */
@Component
class LevelCheckRunSigner {

    private static final String RESTART_MESSAGE = "Start the level check again.";
    private static final String KEY_PURPOSE = "MandarinSpark level check run v1";
    private static final String VERSION = "v1";
    private static final Duration RUN_TTL = Duration.ofHours(24);
    private static final int MAX_TOKEN_LENGTH = 512;

    private final HmacSigner signer;

    LevelCheckRunSigner(SecurityProperties securityProperties) {
        this.signer = HmacSigner.forPurpose(securityProperties, KEY_PURPOSE, "level check runs");
    }

    /** Verified state of an in-progress or completed level check run. */
    record RunState(
            String runId,
            List<Answer> answered,
            Long nextQuestionId,
            boolean done) {

        Set<Long> issuedQuestionIds() {
            Set<Long> ids = HashSet.newHashSet(answered.size() + (nextQuestionId == null ? 0 : 1));
            for (Answer answer : answered) {
                ids.add(answer.questionId());
            }
            if (nextQuestionId != null) {
                ids.add(nextQuestionId);
            }
            return Set.copyOf(ids);
        }
    }

    String newRunId() {
        return UUID.randomUUID().toString();
    }

    String sign(
            Long studentId,
            MandarinLevel startLevel,
            String runId,
            List<Answer> answered,
            Long nextQuestionId,
            boolean done) {
        long issuedAt = Instant.now().getEpochSecond();
        String encodedAnswers = answered.stream()
                .map(answer -> answer.questionId() + ":" + (answer.optionId() == null ? "-" : answer.optionId()))
                .collect(Collectors.joining(","));
        String rawPayload = studentId
                + "|" + startLevel.name()
                + "|" + runId
                + "|" + encodedAnswers
                + "|" + (nextQuestionId == null ? "-" : nextQuestionId)
                + "|" + (done ? "1" : "0")
                + "|" + issuedAt;
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(rawPayload.getBytes(StandardCharsets.UTF_8));
        String signed = VERSION + "." + payload;
        return signed + "." + signer.sign(signed);
    }

    RunState verify(Long studentId, MandarinLevel expectedStartLevel, String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            throw restart();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || !VERSION.equals(parts[0]) || parts[1].isEmpty() || parts[2].isEmpty()) {
            throw restart();
        }
        String signed = parts[0] + "." + parts[1];
        if (!signer.matches(signed, parts[2])) {
            throw restart();
        }
        try {
            String rawPayload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            String[] fields = rawPayload.split("\\|", -1);
            if (fields.length != 7) {
                throw restart();
            }
            Long tokenStudentId = Long.valueOf(fields[0]);
            MandarinLevel tokenStartLevel = MandarinLevel.valueOf(fields[1]);
            String runId = fields[2];
            if (!studentId.equals(tokenStudentId) || expectedStartLevel != tokenStartLevel || runId.isBlank()) {
                throw restart();
            }
            long issuedAt = Long.parseLong(fields[6]);
            long now = Instant.now().getEpochSecond();
            if (issuedAt > now + 60 || now - issuedAt > RUN_TTL.toSeconds()) {
                throw restart();
            }
            List<Answer> answered = parseAnswers(fields[3]);
            Long nextQuestionId = "-".equals(fields[4]) ? null : Long.valueOf(fields[4]);
            boolean done;
            if ("1".equals(fields[5])) {
                done = true;
            } else if ("0".equals(fields[5])) {
                done = false;
            } else {
                throw restart();
            }
            return new RunState(runId, answered, nextQuestionId, done);
        } catch (IllegalArgumentException exception) {
            throw restart();
        }
    }

    private static List<Answer> parseAnswers(String encoded) {
        if (encoded.isEmpty()) {
            return List.of();
        }
        String[] entries = encoded.split(",", -1);
        List<Answer> result = new ArrayList<>(entries.length);
        for (String entry : entries) {
            String[] pair = entry.split(":", -1);
            if (pair.length != 2) {
                throw new IllegalArgumentException("Malformed answer entry");
            }
            Long questionId = Long.valueOf(pair[0]);
            Long optionId = "-".equals(pair[1]) ? null : Long.valueOf(pair[1]);
            result.add(new Answer(questionId, optionId));
        }
        return List.copyOf(result);
    }

    private static ResponseStatusException restart() {
        return new ResponseStatusException(BAD_REQUEST, RESTART_MESSAGE);
    }
}
