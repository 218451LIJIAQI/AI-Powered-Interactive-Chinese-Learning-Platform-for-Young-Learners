package com.example.aichinese.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import com.example.aichinese.util.NetworkAddresses;
import com.example.aichinese.util.TextUtils;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private static final Logger log = LoggerFactory.getLogger(AiProperties.class);
    private static final int MAX_API_KEY_FILE_BYTES = 4096;

    /** OpenAI Responses API (GPT-5.6 Luna), the provider named in the project proposal. */
    public static final String OPENAI = "openai";

    /** {@link #OPENAI} and the alternatives that speak Chat Completions, which are text only. */
    public static final Set<String> SUPPORTED_PROVIDERS = Set.of(OPENAI, "deepseek", "openai-compatible");

    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "::1");
    private static final int MIN_TIMEOUT_SECONDS = 1;
    /**
     * The longest provider deadline: safely below the shortest wait downstream, the 45 s of the Ask SparkBot stream
     * ({@code AiChatStreamRunner}) and of the browser's AI requests ({@code AI_REQUEST_CONFIG} in
     * {@code frontend/src/api/ai.ts}), so the server has always given up, logged and answered before anyone
     * stops listening for the answer.
     */
    public static final int MAX_TIMEOUT_SECONDS = 40;
    private static final int MIN_OUTPUT_TOKENS = 1;
    private static final int MAX_OUTPUT_TOKENS = 4000;

    private String provider = OPENAI;
    private String apiBaseUrl = "https://api.openai.com/v1";
    private String apiKey;
    @Getter(AccessLevel.NONE)
    private boolean explicitApiKeyInvalid;
    @Setter
    private Path apiKeyFile;
    private String modelName = "gpt-5.6-luna";
    private final Pronunciation pronunciation = new Pronunciation();
    private final RateLimit rateLimit = new RateLimit();
    private int timeoutSeconds = 30;
    private int maxOutputTokens = 1200;
    @Setter
    private boolean enableRealProvider;

    public void setProvider(String provider) {
        this.provider = TextUtils.trimToNull(provider);
    }

    /** The provider in lower case, {@link #OPENAI} when unset. */
    public String normalizedProvider() {
        return provider == null ? OPENAI : provider.toLowerCase(Locale.ROOT);
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = TextUtils.trimToNull(apiBaseUrl);
    }

    /**
     * The base URL without trailing slashes, or null when the API key must not be sent to it: only HTTPS (plain HTTP
     * on this machine alone), with a host and no credentials, query or fragment.
     */
    public String normalizedApiBaseUrl() {
        if (apiBaseUrl == null) {
            return null;
        }
        String url = apiBaseUrl.replaceAll("/+$", "");
        URI uri;
        try {
            uri = HttpUrlValidation.requireAbsoluteHttpUrl(url, "AI API base URL");
        } catch (IllegalArgumentException invalid) {
            return null;
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            return null;
        }
        String host = NetworkAddresses.normalizeHost(uri.getHost());
        boolean allowedScheme = "https".equalsIgnoreCase(uri.getScheme())
                || LOOPBACK_HOSTS.contains(host);
        return allowedScheme ? url : null;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = parseApiKey(apiKey);
        this.explicitApiKeyInvalid = !TextUtils.isBlankOrSpaces(apiKey) && this.apiKey == null;
    }

    /** An explicit key takes precedence. Read the optional private file at use time so saving a key takes effect immediately. */
    public String getApiKey() {
        if (explicitApiKeyInvalid) {
            return null;
        }
        if (apiKey != null || apiKeyFile == null) {
            return apiKey;
        }
        try (InputStream in = Files.newInputStream(apiKeyFile)) {
            byte[] raw = in.readNBytes(MAX_API_KEY_FILE_BYTES + 1);
            if (raw.length > MAX_API_KEY_FILE_BYTES) {
                log.warn("Ignoring AI API key file because it exceeds {} bytes.", MAX_API_KEY_FILE_BYTES);
                return null;
            }
            return parseApiKey(new String(raw, StandardCharsets.UTF_8));
        } catch (NoSuchFileException missing) {
            return null;
        } catch (IOException unreadable) {
            log.warn("Could not read AI API key file ({}).", unreadable.getClass().getSimpleName());
            return null;
        }
    }

    /** Strips optional UTF-8 BOM and surrounding whitespace, accepting only printable non-whitespace ASCII bearer tokens. */
    private static String parseApiKey(String raw) {
        if (raw == null) {
            return null;
        }
        String key = (raw.startsWith("\uFEFF") ? raw.substring(1) : raw).strip();
        return !key.isEmpty() && key.chars().allMatch(c -> c >= '!' && c <= '~') ? key : null;
    }

    public void setModelName(String modelName) {
        this.modelName = TextUtils.trimToNull(modelName);
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = requireRange(
                timeoutSeconds, MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS, "AI timeout", " seconds");
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = requireRange(
                maxOutputTokens, MIN_OUTPUT_TOKENS, MAX_OUTPUT_TOKENS, "AI max output tokens", "");
    }

    private static int requireRange(int value, int min, int max, String label, String suffix) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(label + " must be between " + min + " and " + max + suffix + ".");
        }
        return value;
    }

    @Override
    public String toString() {
        return "AiProperties[provider=" + provider
                + ", modelName=" + modelName
                + ", timeoutSeconds=" + timeoutSeconds
                + ", maxOutputTokens=" + maxOutputTokens
                + ", enableRealProvider=" + enableRealProvider
                + "]";
    }

    /** Credentials for Tencent Cloud's New Age oral evaluation service and the local audio decoder. */
    @Getter
    public static class Pronunciation {

        private String secretId;
        private String secretKey;
        private String soeAppId;
        private String ffmpegPath = "ffmpeg";

        public boolean configured() {
            return secretId != null && secretKey != null && soeAppId != null && soeAppId.matches("[0-9]{1,20}")
                    && ffmpegPath != null;
        }

        public void setSecretId(String secretId) {
            this.secretId = TextUtils.trimToNull(secretId);
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = TextUtils.trimToNull(secretKey);
        }

        public void setSoeAppId(String soeAppId) {
            this.soeAppId = TextUtils.trimToNull(soeAppId);
        }

        public void setFfmpegPath(String ffmpegPath) {
            this.ffmpegPath = TextUtils.trimToNull(ffmpegPath);
        }
    }

    /** Per-user AI request quota bounds under {@code app.ai.rate-limit}. */
    @Getter
    public static class RateLimit {

        private int requestsPerMinute = 10;
        private int requestsPerDay = 100;

        public void setRequestsPerMinute(int requestsPerMinute) {
            if (requestsPerMinute < 1) {
                throw new IllegalArgumentException("AI rate limits must be positive.");
            }
            this.requestsPerMinute = requestsPerMinute;
        }

        public void setRequestsPerDay(int requestsPerDay) {
            if (requestsPerDay < 1) {
                throw new IllegalArgumentException("AI rate limits must be positive.");
            }
            this.requestsPerDay = requestsPerDay;
        }
    }
}
