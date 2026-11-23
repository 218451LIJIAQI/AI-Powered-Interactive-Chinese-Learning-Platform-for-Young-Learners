package com.example.aichinese.service.ai;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.service.FileStorageService.PracticeUpload;

import lombok.RequiredArgsConstructor;

/** Decodes a validated upload to the raw 16 kHz, 16-bit, mono PCM expected by Tencent. Uses only pipes. */
@Component
@RequiredArgsConstructor
class PracticeAudioDecoder {

    private static final Logger log = LoggerFactory.getLogger(PracticeAudioDecoder.class);
    static final int BYTES_PER_SECOND = 16_000 * Short.BYTES;
    private static final int MAX_SECONDS = 300;
    private static final int MAX_BYTES = MAX_SECONDS * BYTES_PER_SECOND;
    private static final int TIMEOUT_SECONDS = 15;

    private final AiProperties properties;

    byte[] pcm(PracticeUpload audio) throws DecodeException {
        if (audio == null || audio.contentType() == null || audio.data() == null || audio.data().length == 0) {
            throw new DecodeException(TencentPronunciationClient.Failure.UNREADABLE_AUDIO);
        }
        String format = switch (audio.contentType()) {
            case "audio/webm", "video/webm" -> "matroska";
            case "audio/wav", "audio/x-wav" -> "wav";
            case "audio/mpeg", "audio/mp3" -> "mp3";
            default -> throw new DecodeException(TencentPronunciationClient.Failure.UNREADABLE_AUDIO);
        };
        Process decoder;
        try {
            // Decoding stops one second past the limit, so a longer recording reads as too long instead of being cut.
            decoder = new ProcessBuilder(properties.getPronunciation().getFfmpegPath(),
                    "-hide_banner", "-loglevel", "error", "-nostdin", "-max_alloc", "33554432", "-threads", "1",
                    "-protocol_whitelist", "pipe", "-f", format, "-i", "pipe:0", "-map", "0:a:0",
                    "-t", Integer.toString(MAX_SECONDS + 1),
                    "-ac", "1", "-ar", "16000", "-c:a", "pcm_s16le", "-f", "s16le", "pipe:1")
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException exception) {
            log.warn("FFmpeg audio decoder could not start ({}).", exception.getClass().getSimpleName());
            throw new DecodeException(TencentPronunciationClient.Failure.DECODER_UNAVAILABLE);
        }

        try (var io = Executors.newVirtualThreadPerTaskExecutor()) {
            // Drain output while writing input: either stream can fill before the other one closes.
            var writer = io.submit(() -> {
                try (var input = decoder.getOutputStream()) {
                    input.write(audio.data());
                } catch (IOException ignored) {
                    // A decoder can close its input early. Its output and exit code decide the result.
                }
            });
            var output = io.submit(() -> {
                try (var decoded = decoder.getInputStream()) {
                    return decoded.readNBytes(MAX_BYTES + Short.BYTES);
                }
            });
            try {
                byte[] pcm = output.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (pcm.length > MAX_BYTES) {
                    throw new DecodeException(TencentPronunciationClient.Failure.AUDIO_TOO_LONG);
                }
                if (!decoder.waitFor(1, TimeUnit.SECONDS)) {
                    log.warn("FFmpeg audio decoder did not exit cleanly within shutdown grace period.");
                    throw new DecodeException(TencentPronunciationClient.Failure.DECODER_UNAVAILABLE);
                }
                if (decoder.exitValue() != 0 || pcm.length == 0 || pcm.length % Short.BYTES != 0) {
                    log.warn("FFmpeg audio decoding failed (exitCode={}, decodedBytes={}).",
                            decoder.exitValue(), pcm.length);
                    throw new DecodeException(TencentPronunciationClient.Failure.UNREADABLE_AUDIO);
                }
                return pcm;
            } catch (ExecutionException exception) {
                if (exception.getCause() instanceof Error error) {
                    throw error;
                }
                log.warn("FFmpeg audio decoding stream failed ({}).",
                        exception.getCause() == null ? "Unknown" : exception.getCause().getClass().getSimpleName());
                throw new DecodeException(TencentPronunciationClient.Failure.UNREADABLE_AUDIO);
            } catch (TimeoutException exception) {
                log.warn("FFmpeg audio decoder timed out after {} seconds.", TIMEOUT_SECONDS);
                throw new DecodeException(TencentPronunciationClient.Failure.DECODER_UNAVAILABLE);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new DecodeException(TencentPronunciationClient.Failure.DECODER_UNAVAILABLE);
            } finally {
                if (decoder.isAlive()) {
                    decoder.destroyForcibly();
                }
                writer.cancel(true);
                output.cancel(true);
                io.shutdownNow();
            }
        }
    }

    static class DecodeException extends Exception {
        private final TencentPronunciationClient.Failure failure;

        DecodeException(TencentPronunciationClient.Failure failure) {
            this.failure = failure;
        }

        TencentPronunciationClient.Failure failure() {
            return failure;
        }
    }
}
