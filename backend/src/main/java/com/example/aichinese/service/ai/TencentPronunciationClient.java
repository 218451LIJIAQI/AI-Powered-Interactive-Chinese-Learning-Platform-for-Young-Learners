package com.example.aichinese.service.ai;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.entity.value.PronunciationFeedback;
import com.example.aichinese.entity.value.PronunciationFeedback.Issue;
import com.example.aichinese.entity.value.PronunciationFeedback.IssueKind;
import com.example.aichinese.entity.value.PronunciationFeedback.ToneComparison;
import com.example.aichinese.service.FileStorageService.PracticeUpload;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/** Assesses the learner's completed recording using Tencent Cloud's New Age Mandarin engine. */
@Component
@RequiredArgsConstructor
public class TencentPronunciationClient {

    static final String PROVIDER = "tencent-soe";
    private static final Logger log = LoggerFactory.getLogger(TencentPronunciationClient.class);
    private static final Pattern HANZI = Pattern.compile("\\p{IsHan}+");
    private static final Pattern OTHER_THAN_TEXT_AND_PAUSES = Pattern.compile("[^\\p{IsHan}，。！？；、,.!?;]");
    private static final int SENTENCE_MAX_CHARACTERS = 30;
    private static final int SINGLE_FRAME_MAX_SECONDS = 60;
    private static final int MAX_ISSUES = 3;
    private static final String SERVICE_PATH = "soe.cloud.tencent.com/soe/api/";

    private final AiProperties properties;
    private final PracticeAudioDecoder decoder;
    private final TencentSoeSocket socket;
    private final ObjectMapper mapper;

    public enum Failure {
        INVALID_TARGET("Enter the Mandarin target in Chinese characters.", "Invalid pronunciation target."),
        UNREADABLE_AUDIO("This audio file could not be read. Choose another file or record again.",
                "The audio upload could not be decoded."),
        AUDIO_TOO_LONG("Choose a recording of five minutes or less.", "The recording was longer than 300 seconds."),
        NO_SPEECH("SparkBot couldn't hear any speech in this recording. Record it again a little closer to the microphone.",
                "The pronunciation service detected no speech."),
        NO_MATCH("We could not match this recording to the target. Read the target aloud and try again.",
                "The pronunciation service could not match the audio to the reference."),
        DECODER_UNAVAILABLE(AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE,
                "The audio decoder could not start or did not finish in time. Check FFmpeg on the backend."),
        AUTHENTICATION(AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE,
                "Tencent pronunciation authentication failed. Check the account AppID and CAM key permissions."),
        SERVICE_DISABLED(AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE,
                "Tencent New Age pronunciation is not enabled for this account AppID."),
        ACCOUNT_LIMIT(AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE,
                "Tencent pronunciation quota or billing is preventing new assessments."),
        PROVIDER_FAILURE(AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE, "Tencent pronunciation assessment failed.");

        final String message;
        final String summary;

        Failure(String message, String summary) {
            this.message = message;
            this.summary = summary;
        }
    }

    public record Assessment(Integer score, PronunciationFeedback feedback, Failure failure) {

        public static Assessment failed(Failure failure) {
            return new Assessment(null, null, failure);
        }
    }

    /** A target the assessment takes: Chinese characters, and no letters or digits of another script. */
    public static boolean acceptsTarget(String target) {
        return target != null
                && HANZI.matcher(target).find()
                && target.codePoints().noneMatch(TencentPronunciationClient::nonChineseWord);
    }

    public Assessment assess(PracticeUpload upload, String target) {
        if (!acceptsTarget(target)) {
            return Assessment.failed(Failure.INVALID_TARGET);
        }
        String reference = OTHER_THAN_TEXT_AND_PAUSES.matcher(target).replaceAll("");
        if (!properties.isEnableRealProvider() || !properties.getPronunciation().configured()) {
            return Assessment.failed(Failure.PROVIDER_FAILURE);
        }
        byte[] pcm;
        try {
            pcm = decoder.pcm(upload);
        } catch (PracticeAudioDecoder.DecodeException exception) {
            return Assessment.failed(exception.failure());
        }
        boolean recordingMode = pcm.length <= SINGLE_FRAME_MAX_SECONDS * PracticeAudioDecoder.BYTES_PER_SECOND;
        boolean paragraph = !recordingMode || reference.codePointCount(0, reference.length()) > SENTENCE_MAX_CHARACTERS;
        try {
            URI uri = signedUri(reference, paragraph, recordingMode, Instant.now().getEpochSecond(),
                    ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE), UUID.randomUUID().toString());
            var json = socket.assess(uri, pcm, recordingMode, properties.getTimeoutSeconds());
            var reply = mapper.treeToValue(json, ProviderReply.class);
            return readAssessment(reply, reference);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Assessment.failed(Failure.PROVIDER_FAILURE);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof Error error) {
                throw error;
            }
            if (exception.getCause() instanceof TencentSoeSocket.ServiceFailure service) {
                log.warn("Tencent pronunciation assessment returned code {}.", service.code());
                return Assessment.failed(serviceFailure(service.code()));
            }
            if (exception.getCause() instanceof TencentSoeSocket.ProtocolFailure protocol) {
                log.warn("Tencent pronunciation assessment protocol failure: {}.", protocol.reason());
                return Assessment.failed(Failure.PROVIDER_FAILURE);
            }
            return providerFailure(exception.getCause());
        } catch (GeneralSecurityException | JsonProcessingException | TimeoutException exception) {
            return providerFailure(exception);
        }
    }

    /** Logs the failure's type only: its text can include the signed URL or the learner's reference text. */
    private static Assessment providerFailure(Throwable failure) {
        log.warn("Tencent pronunciation assessment failed with exception type {}.",
                failure == null ? "Unknown" : failure.getClass().getSimpleName());
        return Assessment.failed(Failure.PROVIDER_FAILURE);
    }

    private static Failure serviceFailure(int code) {
        return switch (code) {
            case 4002 -> Failure.AUTHENTICATION;
            case 4003 -> Failure.SERVICE_DISABLED;
            case 4004, 4005 -> Failure.ACCOUNT_LIMIT;
            case 4007, 4107 -> Failure.UNREADABLE_AUDIO;
            case 4014, 4106 -> Failure.AUDIO_TOO_LONG;
            case 4105, 4108 -> Failure.NO_SPEECH;
            default -> Failure.PROVIDER_FAILURE;
        };
    }

    URI signedUri(String reference, boolean paragraph, boolean recordingMode, long issuedAt, int nonce, String voiceId)
            throws GeneralSecurityException {
        AiProperties.Pronunciation settings = properties.getPronunciation();
        var query = new TreeMap<>(Map.of(
                "eval_mode", paragraph ? "2" : "1",
                "expired", Long.toString(issuedAt + 300),
                "nonce", Integer.toString(nonce),
                "rec_mode", recordingMode ? "1" : "0",
                "ref_text", paragraph ? reference : "{::cmd{F_TDET=true}}" + reference,
                "score_coeff", "2.0",
                "secretid", settings.getSecretId(),
                "server_engine_type", "16k_zh",
                "timestamp", Long.toString(issuedAt),
                "voice_format", "0"));
        query.put("voice_id", voiceId);
        String path = SERVICE_PATH + settings.getSoeAppId();
        String plainQuery = query.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
        Mac mac = Mac.getInstance("HmacSHA1"); // The Tencent WebSocket signing protocol specifies HMAC-SHA1.
        mac.init(new SecretKeySpec(settings.getSecretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        String signature = Base64.getEncoder().encodeToString(mac.doFinal((path + "?" + plainQuery).getBytes(StandardCharsets.UTF_8)));
        String encodedQuery = query.entrySet().stream().map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        return URI.create("wss://" + path + "?" + encodedQuery + "&signature=" + encode(signature));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static Assessment readAssessment(ProviderReply reply, String reference) {
        if (Float.valueOf(-1).equals(reply.accuracy())) {
            return Assessment.failed(Failure.NO_MATCH);
        }
        Integer score = percent(reply.score(), 100);
        if (score == null) {
            return Assessment.failed(Failure.PROVIDER_FAILURE);
        }
        return new Assessment(score, feedback(reply, reference), null);
    }

    private static PronunciationFeedback feedback(ProviderReply reply, String reference) {
        List<Issue> issues = new ArrayList<>();
        List<ToneComparison> toneComparisons = new ArrayList<>();
        Word[] words = reply.words();
        for (int index = 0; words != null && index < words.length; index++) {
            Word word = words[index];
            if (word == null) {
                continue;
            }
            String text = targetWord(word, reference);
            if (text == null) {
                continue;
            }
            ToneComparison comparison = toneComparison(word, text);
            if (comparison != null) {
                Word next = index + 1 < words.length ? words[index + 1] : null;
                if (comparison.expectedTone() != comparison.detectedTone()
                        && possibleSandhi(text, word.tone(), next, reference)) {
                    continue;
                }
                toneComparisons.add(comparison);
            }
            if (issues.size() < MAX_ISSUES) {
                Issue issue = issue(word.matchTag(), text, comparison);
                if (issue != null) {
                    issues.add(issue);
                }
            }
        }
        return new PronunciationFeedback(percent(reply.accuracy(), 100), percent(reply.fluency(), 1),
                percent(reply.completion(), 1), issues, toneComparisons);
    }

    private static ToneComparison toneComparison(Word word, String text) {
        Long tag = word.matchTag();
        Tone tone = word.tone();
        if ((Long.valueOf(0).equals(tag) || Long.valueOf(3).equals(tag)) && tone != null
                && Boolean.TRUE.equals(tone.valid()) && recognizedTone(tone.reference()) && recognizedTone(tone.detected())) {
            return new ToneComparison(text, tone.reference().intValue(), tone.detected().intValue());
        }
        return null;
    }

    private static Issue issue(Long tag, String text, ToneComparison comparison) {
        if (Long.valueOf(2).equals(tag)) {
            return new Issue(text, IssueKind.OMITTED, null, null);
        }
        if (comparison != null && comparison.expectedTone() != comparison.detectedTone()) {
            return new Issue(text, IssueKind.TONE_MISMATCH, comparison.expectedTone(), comparison.detectedTone());
        }
        return Long.valueOf(3).equals(tag) ? new Issue(text, IssueKind.MISPRONOUNCED, null, null) : null;
    }

    private static String targetWord(Word word, String reference) {
        String text = word.reference();
        if (text == null || text.isBlank()) {
            text = word.text();
        }
        return text != null && HANZI.matcher(text).matches() && reference.contains(text) ? text : null;
    }

    /** A lexical reference tone can differ from the expected sound in a phrase such as 你好, 不是, or 一起. */
    private static boolean possibleSandhi(String text, Tone tone, Word next, String reference) {
        String nextText = next == null ? null : targetWord(next, reference);
        if (nextText == null || !reference.contains(text + nextText)) {
            return false;
        }
        Long nextRefTone = next.tone() == null ? null : next.tone().reference();
        if (text.equals("不")) {
            return Long.valueOf(4).equals(tone.reference())
                    && Long.valueOf(2).equals(tone.detected())
                    && (nextRefTone == null || Long.valueOf(4).equals(nextRefTone));
        }
        if (text.equals("一")) {
            if (!Long.valueOf(1).equals(tone.reference())) {
                return false;
            }
            if (nextRefTone == null) {
                return Long.valueOf(2).equals(tone.detected()) || Long.valueOf(4).equals(tone.detected());
            }
            return Long.valueOf(4).equals(nextRefTone)
                    ? Long.valueOf(2).equals(tone.detected())
                    : recognizedTone(nextRefTone) && Long.valueOf(4).equals(tone.detected());
        }
        return Long.valueOf(3).equals(tone.reference()) && Long.valueOf(2).equals(tone.detected())
                && Long.valueOf(3).equals(nextRefTone);
    }

    private static boolean nonChineseWord(int codePoint) {
        return Character.isLetterOrDigit(codePoint) && Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.HAN;
    }

    private static boolean recognizedTone(Long tone) {
        return tone != null && tone >= 1 && tone <= 4;
    }

    private static Integer percent(Float value, int scale) {
        return value != null && Float.isFinite(value) && value >= 0 && value <= scale
                ? Math.round(value * 100 / scale)
                : null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProviderReply(
            @JsonProperty("SuggestedScore") Float score,
            @JsonProperty("PronAccuracy") Float accuracy,
            @JsonProperty("PronFluency") Float fluency,
            @JsonProperty("PronCompletion") Float completion,
            @JsonProperty("Words") Word[] words) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Word(
            @JsonProperty("Word") String text,
            @JsonProperty("ReferenceWord") String reference,
            @JsonProperty("MatchTag") @JsonAlias("Tag") Long matchTag,
            @JsonProperty("Tone") Tone tone) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Tone(
            @JsonProperty("Valid") Boolean valid,
            @JsonProperty("RefTone") Long reference,
            @JsonProperty("HypothesisTone") @JsonAlias("HypTone") Long detected) {
    }
}
