package com.example.aichinese.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.service.FileStorageService.PracticeUpload;

/** Real, synthetic audio fixtures through the same executable used by the application, plus decoder guard tests. */
class PracticeAudioDecoderTests {

    @TempDir
    Path directory;

    private final String ffmpeg = System.getenv("AI_FFMPEG_PATH");

    @EnabledIfEnvironmentVariable(named = "AI_FFMPEG_PATH", matches = ".+")
    @ParameterizedTest
    @CsvSource({"webm,audio/webm,libopus", "wav,audio/wav,pcm_s16le", "mp3,audio/mpeg,libmp3lame"})
    void convertsAnUploadedRecordingTo16KhzMonoPcm(String suffix, String mimeType, String codec) throws Exception {
        Path file = directory.resolve("voice." + suffix);
        Process fixture = new ProcessBuilder(ffmpeg, "-hide_banner", "-loglevel", "error", "-f", "lavfi", "-i",
                "sine=frequency=440:sample_rate=48000:duration=1", "-ac", "2", "-c:a", codec, file.toString())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.INHERIT).start();
        try {
            assertThat(fixture.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(fixture.exitValue()).isZero();
            byte[] pcm = decoder().pcm(new PracticeUpload(Files.readAllBytes(file), mimeType));
            assertThat(pcm.length).isBetween(31_900, 33_100);
            assertThat(pcm.length % Short.BYTES).isZero();
        } finally {
            fixture.destroyForcibly();
        }
    }

    @EnabledIfEnvironmentVariable(named = "AI_FFMPEG_PATH", matches = ".+")
    @Test
    void damagedAudioReturnsASpecificFailure() {
        var upload = new PracticeUpload(new byte[] { 1, 2 }, "audio/webm");
        assertThatThrownBy(() -> decoder().pcm(upload)).isInstanceOfSatisfying(PracticeAudioDecoder.DecodeException.class,
                error -> assertThat(error.failure()).isEqualTo(TencentPronunciationClient.Failure.UNREADABLE_AUDIO));
    }

    @Test
    void rejectsMissingEmptyOrUnsupportedAudioBeforeLaunchingFfmpeg() {
        PracticeAudioDecoder missingExecutableDecoder = decoderWithPath("definitely-not-a-real-ffmpeg-binary");
        for (PracticeUpload invalid : new PracticeUpload[] {
                null,
                new PracticeUpload(null, "audio/webm"),
                new PracticeUpload(new byte[0], "audio/webm"),
                new PracticeUpload(new byte[] { 1 }, null),
                new PracticeUpload(new byte[] { 1 }, "application/pdf")
        }) {
            assertThatThrownBy(() -> missingExecutableDecoder.pcm(invalid))
                    .isInstanceOfSatisfying(PracticeAudioDecoder.DecodeException.class,
                            error -> assertThat(error.failure()).isEqualTo(TencentPronunciationClient.Failure.UNREADABLE_AUDIO));
        }

        assertThatThrownBy(() -> missingExecutableDecoder.pcm(new PracticeUpload(new byte[] { 1, 2 }, "audio/webm")))
                .isInstanceOfSatisfying(PracticeAudioDecoder.DecodeException.class,
                        error -> assertThat(error.failure()).isEqualTo(TencentPronunciationClient.Failure.DECODER_UNAVAILABLE));
    }

    private PracticeAudioDecoder decoder() {
        return decoderWithPath(ffmpeg);
    }

    private static PracticeAudioDecoder decoderWithPath(String path) {
        AiProperties properties = new AiProperties();
        properties.getPronunciation().setFfmpegPath(path);
        return new PracticeAudioDecoder(properties);
    }
}
