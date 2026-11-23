package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.ai.XiaomeiConversationRequest;
import com.example.aichinese.dto.ai.XiaomeiReplyRequest;
import com.example.aichinese.dto.ai.XiaomeiResponses;
import com.example.aichinese.dto.ai.XiaomeiTurn;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.entity.enums.PlanReason;
import com.example.aichinese.entity.enums.XiaomeiScenario;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.XiaomeiMemoryService;
import com.example.aichinese.service.XiaomeiService;
import com.example.aichinese.service.account.CurrentAccount;
import com.example.aichinese.service.ai.AiExecutionResult;
import com.example.aichinese.service.ai.AiGateway;

/**
 * Unit coverage of Xiaomei's "more natural" tip: {@code naturalVersion} and {@code tip} are both null when there is
 * nothing to correct, as the reply's contract says, also when the AI repeats the student's own line as the correction;
 * of the earlier turns sent with a reply; and of what she remembers: it is the context of every request, the summary
 * hands the facts SparkBot found to the memory and lists the notes, also without an AI answer.
 */
class XiaomeiServiceTests {

    private static final Map<String, Object> REPLY_LINE =
            Map.of("chinese", "很好！", "pinyin", "Hěn hǎo!", "translation", "Great!");
    private static final String CONTEXT =
            " What Xiaomei remembers about the learner from earlier chats: \"Studies Chinese at school\".";

    private final CurrentAccount currentAccount = mock(CurrentAccount.class);
    private final AiGateway gateway = mock(AiGateway.class);
    private final XiaomeiMemoryService memory = mock(XiaomeiMemoryService.class);
    private final XiaomeiService service = new XiaomeiService(currentAccount, gateway, memory);
    private SecurityUserDetails principal;

    @BeforeEach
    void signedInStudent() {
        User student = new User();
        student.setId(7L);
        student.setEmail("student@example.com");
        student.setPreferredLanguage("en");
        principal = new SecurityUserDetails(student).withSession(true, "session-1");
        when(currentAccount.require(principal)).thenReturn(student);
        when(memory.chatContext(any())).thenReturn("");
    }

    @Test
    void aCorrectionThatRepeatsTheStudentsLineComesWithoutATip() {
        answer(Map.of("reply", REPLY_LINE, "naturalVersion", "我很好。", "tip", "Say it like this."));

        XiaomeiResponses.Reply reply = service.reply(principal, request("我很好。"));

        assertThat(sentCall().sessionId()).isEqualTo("session-1");
        assertThat(reply.status()).isEqualTo(AiRequestStatus.SUCCESS);
        assertThat(reply.reply().chinese()).isEqualTo("很好！");
        assertThat(reply.naturalVersion()).isNull();
        assertThat(reply.tip()).isNull();
    }

    @Test
    void aRealCorrectionKeepsItsTip() {
        answer(Map.of("reply", REPLY_LINE, "naturalVersion", "我在学校学习中文。", "tip", "Place comes before the verb."));

        XiaomeiResponses.Reply reply = service.reply(principal, request("我学习中文在学校。"));

        assertThat(reply.naturalVersion()).isEqualTo("我在学校学习中文。");
        assertThat(reply.tip()).isEqualTo("Place comes before the verb.");
    }

    /** A turn as long as the request allows (300 characters) reaches the provider whole, not cut at 200. */
    @Test
    void aLongEarlierTurnIsSentWhole() {
        answer(Map.of("reply", REPLY_LINE));
        String longLine = "你".repeat(XiaomeiTurn.MAX_TEXT_LENGTH);

        service.reply(principal, new XiaomeiReplyRequest(XiaomeiScenario.MEET_XIAOMEI,
                List.of(new XiaomeiTurn(XiaomeiTurn.Speaker.XIAOMEI, longLine)), "好的。", "en"));

        assertThat(sentCall().history()).singleElement()
                .satisfies(turn -> assertThat(turn.content()).isEqualTo(longLine));
    }

    @Test
    void whatXiaomeiRemembersIsTheContextOfHerReplies() {
        when(memory.chatContext(any())).thenReturn(CONTEXT);
        answer(Map.of("reply", REPLY_LINE));

        service.reply(principal, request("你好"));

        assertThat(sentCall().userPrompt())
                .startsWith("Scenario: " + XiaomeiScenario.MEET_XIAOMEI.situation() + CONTEXT)
                .contains("The learner now says: \"你好\"");
    }

    @Test
    void theSummaryAsksForFactsToRememberAndListsTheNotesTheMemoryKeeps() {
        when(memory.chatContext(any())).thenReturn(CONTEXT);
        List<XiaomeiResponses.MemoryNote> notes = List.of(
                new XiaomeiResponses.MemoryNote(1L, "Travel to Shanghai", PlanReason.TRAVEL),
                new XiaomeiResponses.MemoryNote(2L, "Studies Chinese at school", null));
        when(memory.remember(any(), any())).thenReturn(notes);
        answer(Map.of("feedback", "Nice work!", "remember",
                List.of("Studies Chinese at school", 7, true, "\u00a0", "Likes bubble tea")));

        XiaomeiResponses.Summary summary = service.summary(principal, conversation());

        AiGateway.AiCall call = sentCall();
        assertThat(call.sessionId()).isEqualTo("session-1");
        assertThat(call.userPrompt())
                .contains(CONTEXT)
                .contains("remember (an array of at most 2 short facts")
                .contains("never a name, an e-mail address, a phone number");
        verify(memory).remember(any(), eq(List.of("Studies Chinese at school", "Likes bubble tea")));
        assertThat(summary.feedback()).isEqualTo("Nice work!");
        assertThat(summary.memory()).isEqualTo(notes);
    }

    /** The plan's goal is no AI writing: without an AI answer the summary still lists what Xiaomei remembers. */
    @Test
    void theNotesAreListedWhenTheAiIsUnavailable() {
        List<XiaomeiResponses.MemoryNote> notes =
                List.of(new XiaomeiResponses.MemoryNote(1L, null, PlanReason.WORK));
        when(memory.remember(any(), any())).thenReturn(notes);
        when(gateway.execute(any())).thenReturn(AiExecutionResult.unavailable("AI tutor is currently unavailable."));

        XiaomeiResponses.Summary summary = service.summary(principal, conversation());

        assertThat(summary.status()).isEqualTo(AiRequestStatus.UNAVAILABLE);
        assertThat(summary.feedback()).isNull();
        verify(memory).remember(any(), eq(List.of()));
        assertThat(summary.memory()).isEqualTo(notes);
    }

    @Test
    void summaryRejectsConversationWhoseTurnsSanitizeToEmptyWithoutCallingGateway() {
        XiaomeiConversationRequest emptyAfterSanitizing = new XiaomeiConversationRequest(
                XiaomeiScenario.MEET_XIAOMEI,
                List.of(new XiaomeiTurn(XiaomeiTurn.Speaker.STUDENT, "<|im_start|>\u200b<|im_end|>")),
                "en");

        assertThatThrownBy(() -> service.summary(principal, emptyAfterSanitizing))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getReason()).isEqualTo("The conversation has no turns yet.");
                });
        verifyNoInteractions(gateway);
    }

    private void answer(Map<String, Object> json) {
        when(gateway.execute(any())).thenReturn(AiExecutionResult.success("{}", json));
    }

    private AiGateway.AiCall sentCall() {
        ArgumentCaptor<AiGateway.AiCall> call = ArgumentCaptor.forClass(AiGateway.AiCall.class);
        verify(gateway).execute(call.capture());
        return call.getValue();
    }

    private static XiaomeiReplyRequest request(String message) {
        return new XiaomeiReplyRequest(XiaomeiScenario.MEET_XIAOMEI, List.of(), message, "en");
    }

    private static XiaomeiConversationRequest conversation() {
        return new XiaomeiConversationRequest(XiaomeiScenario.MEET_XIAOMEI,
                List.of(new XiaomeiTurn(XiaomeiTurn.Speaker.STUDENT, "我在学校学中文。")), "en");
    }
}
