package com.example.aichinese.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.ConnectException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.entity.value.PronunciationFeedback;
import com.example.aichinese.entity.value.PronunciationFeedback.Issue;
import com.example.aichinese.entity.value.PronunciationFeedback.IssueKind;
import com.example.aichinese.entity.value.PronunciationFeedback.ToneComparison;
import com.example.aichinese.service.FileStorageService.PracticeUpload;
import com.fasterxml.jackson.databind.ObjectMapper;

class TencentPronunciationClientTests {

    private static final PracticeUpload RECORDING = new PracticeUpload(new byte[] { 1, 2 }, "audio/webm");
    private final AiProperties properties = new AiProperties();
    private final PracticeAudioDecoder decoder = mock(PracticeAudioDecoder.class);
    private final TencentSoeSocket socket = mock(TencentSoeSocket.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final TencentPronunciationClient client = new TencentPronunciationClient(properties, decoder, socket, mapper);

    @BeforeEach
    void configureTestAccount() {
        properties.setEnableRealProvider(true);
        properties.getPronunciation().setSoeAppId("1251234567");
        properties.getPronunciation().setSecretId("test-secret-id");
        properties.getPronunciation().setSecretKey("test-secret-key");
    }

    @Test
    void signsTheNewAgeWebSocketUrlAndEscapesTheReferenceAndSignature() throws Exception {
        URI signed = client.signedUri("山，你好吗？谢", false, true, 1700000000, 1234, "test-voice");

        assertThat(signed.getScheme()).isEqualTo("wss");
        assertThat(signed.getHost()).isEqualTo("soe.cloud.tencent.com");
        assertThat(signed.getPath()).isEqualTo("/soe/api/1251234567");
        assertThat(signed.getQuery()).contains("ref_text={::cmd{F_TDET=true}}山，你好吗？谢", "rec_mode=1");
        assertThat(signed.getRawQuery()).contains("signature=WdIG7pSeKWKjokBN%2BV2n%2BCKG27g%3D");
        assertThat(signed.getRawQuery()).doesNotContain("test-secret-key");
    }

    @Test
    void sendsThePcmAndUsesOnlyValidAcousticEvidence() throws Exception {
        byte[] pcm = { 1, 2, 3, 4 };
        when(decoder.pcm(RECORDING)).thenReturn(pcm);
        when(socket.assess(any(), same(pcm), eq(true), eq(30))).thenReturn(mapper.readTree("""
                {"PronAccuracy":84.4,"PronFluency":0.8,"PronCompletion":0.75,"SuggestedScore":79.1,"Words":[
                  {"Word":"山","MatchTag":0,"Tone":{"Valid":true,"RefTone":1,"HypothesisTone":4}},
                  {"Word":"你","MatchTag":3,"Tone":{"Valid":true,"RefTone":3,"HypTone":2}},
                  {"Word":"好","MatchTag":0,"Tone":{"Valid":true,"RefTone":3,"HypothesisTone":3}},
                  {"Word":"吗","MatchTag":0,"Tone":{"Valid":false,"RefTone":1,"HypothesisTone":4}},
                  {"ReferenceWord":"谢","Word":"*","MatchTag":2},
                  {"Word":"个人信息","MatchTag":3}]}
                """));

        var result = client.assess(RECORDING, "山，你好吗？谢");

        assertThat(result.failure()).isNull();
        assertThat(result.score()).isEqualTo(79);
        assertThat(result.feedback().accuracy()).isEqualTo(84);
        assertThat(result.feedback().fluency()).isEqualTo(80);
        assertThat(result.feedback().completeness()).isEqualTo(75);
        assertThat(result.feedback().issues()).containsExactly(
                new Issue("山", IssueKind.TONE_MISMATCH, 1, 4), new Issue("谢", IssueKind.OMITTED, null, null));
        assertThat(result.feedback().toneComparisons()).containsExactly(
                new ToneComparison("山", 1, 4), new ToneComparison("好", 3, 3));
        var sent = ArgumentCaptor.forClass(URI.class);
        verify(socket).assess(sent.capture(), same(pcm), eq(true), eq(30));
        assertThat(sent.getValue().getQuery()).contains("eval_mode=1", "server_engine_type=16k_zh", "voice_format=0");
    }

    @Test
    void readsSavedFeedbackFromBeforeToneComparisonsWereCollected() throws Exception {
        var feedback = mapper.readValue("""
                {"accuracy":84,"fluency":80,"completeness":75,"issues":[
                  {"text":"山","kind":"TONE_MISMATCH","expectedTone":1,"detectedTone":4}]}
                """, PronunciationFeedback.class);

        assertThat(feedback.toneComparisons()).isEmpty();
        assertThat(feedback.issues()).containsExactly(new Issue("山", IssueKind.TONE_MISMATCH, 1, 4));
        var scoresOnly = mapper.readValue("""
                {"accuracy":84,"fluency":80,"completeness":75}
                """, PronunciationFeedback.class);
        assertThat(scoresOnly.issues()).isEmpty();
        assertThat(scoresOnly.toneComparisons()).isEmpty();
    }

    @Test
    void streamsLongUploadsInParagraphModeAndReportsCloudFailures() throws Exception {
        byte[] longClip = new byte[60 * PracticeAudioDecoder.BYTES_PER_SECOND + Short.BYTES];
        when(decoder.pcm(RECORDING)).thenReturn(longClip);
        when(socket.assess(any(), same(longClip), eq(false), eq(30)))
                .thenThrow(new ExecutionException(new TencentSoeSocket.ServiceFailure(4105)));

        String target = "我们去学校。".repeat(7);
        var result = client.assess(RECORDING, target);

        assertThat(result.failure()).isEqualTo(TencentPronunciationClient.Failure.NO_SPEECH);
        assertThat(result.score()).isNull();
        var sent = ArgumentCaptor.forClass(URI.class);
        verify(socket).assess(sent.capture(), same(longClip), eq(false), eq(30));
        String query = URLDecoder.decode(sent.getValue().getRawQuery(), StandardCharsets.UTF_8);
        assertThat(query).contains("eval_mode=2", "rec_mode=0", "ref_text=" + target).doesNotContain("F_TDET");
    }

    /** A connection that fails (here a refused handshake) is a provider failure, logged by its type alone. */
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void aFailedConnectionIsAProviderFailureLoggedByItsTypeOnly(CapturedOutput output) throws Exception {
        when(decoder.pcm(RECORDING)).thenReturn(new byte[] { 1, 2 });
        when(socket.assess(any(), any(), eq(true), eq(30))).thenThrow(new ExecutionException(
                new ConnectException("Connection refused: wss://soe.cloud.tencent.com/soe/api/1?ref_text=谢谢")));

        var result = client.assess(RECORDING, "谢谢");

        assertThat(result.failure()).isEqualTo(TencentPronunciationClient.Failure.PROVIDER_FAILURE);
        assertThat(output).contains("Tencent pronunciation assessment failed with exception type ConnectException.")
                .doesNotContain("ref_text");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void protocolFailureLogsReasonInsteadOfFakeNegativeTencentCode(CapturedOutput output) throws Exception {
        when(decoder.pcm(RECORDING)).thenReturn(new byte[] { 1, 2 });
        when(socket.assess(any(), any(), eq(true), eq(30)))
                .thenThrow(new ExecutionException(new TencentSoeSocket.ProtocolFailure("closed_early")));

        var result = client.assess(RECORDING, "谢谢");

        assertThat(result.failure()).isEqualTo(TencentPronunciationClient.Failure.PROVIDER_FAILURE);
        assertThat(output).contains("Tencent pronunciation assessment protocol failure: closed_early.")
                .doesNotContain("returned code -1");
    }

    @Test
    void requiresTheNumericAccountAppIdBeforeDecoding() {
        properties.getPronunciation().setSoeAppId("business-app");
        assertThat(client.assess(RECORDING, "你好").failure()).isEqualTo(TencentPronunciationClient.Failure.PROVIDER_FAILURE);
        verifyNoInteractions(decoder, socket);
    }

    @Test
    void rejectsMixedReferenceMarkupBeforeDecoding() {
        var result = client.assess(RECORDING, "你好 {::cmd{F_TDET=false}}");

        assertThat(result.failure()).isEqualTo(TencentPronunciationClient.Failure.INVALID_TARGET);
        verifyNoInteractions(decoder, socket);
    }
}
