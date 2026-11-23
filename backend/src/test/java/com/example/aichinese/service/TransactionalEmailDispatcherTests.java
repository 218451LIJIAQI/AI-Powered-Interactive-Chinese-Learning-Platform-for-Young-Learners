package com.example.aichinese.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import com.example.aichinese.entity.User;
import com.example.aichinese.service.TransactionalEmailDispatcher.ReviewQueue;
import com.example.aichinese.service.TransactionalEmailDispatcher.WaitingQueue;

/**
 * Every e-mail in its reader's language (en, ms, zh-CN), with links, names, numbers and typed text as they are, and
 * English for an account without a known language.
 */
class TransactionalEmailDispatcherTests {

    private static final String EMAIL = "student@example.com";
    private static final String LINK = "https://app.example.com/reset-password?token=abc123";
    private static final String LINE = System.lineSeparator();
    private static final String SWITCH_OFF_NOTE = "You get this email because Daily summary is switched on in Account > "
            + "Email notifications. Switch it off there to stop these emails.";
    private static final EmailTexts TEXTS = new EmailTexts();

    @Test
    void passwordResetIsInTheAccountsLanguage() {
        assertEmail(dispatcher -> dispatcher.enqueuePasswordReset(recipient("en"), LINK),
                "Password reset request",
                "Click the link below to set a new password (link expires in 60 minutes):" + LINE + LINK);
        assertEmail(dispatcher -> dispatcher.enqueuePasswordReset(recipient("ms"), LINK),
                "Permintaan tetapan semula kata laluan",
                "Klik pautan di bawah untuk menetapkan kata laluan baharu (pautan tamat tempoh dalam 60 minit):"
                        + LINE + LINK);
        assertEmail(dispatcher -> dispatcher.enqueuePasswordReset(recipient("zh-CN"), LINK),
                "密码重置请求",
                "点击下面的链接设置新密码（链接 60 分钟后失效）：" + LINE + LINK);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "fr", "zh", "MS" })
    void anAccountWithoutAKnownLanguageGetsEnglish(String language) {
        Sent sent = delivered(dispatcher -> dispatcher.enqueuePasswordReset(recipient(language), LINK));

        assertThat(sent.subject()).isEqualTo("Password reset request");
        assertThat(sent.body()).startsWith("We received a request to reset the password for your account.");
    }

    /** The link goes to the new address, in the language of the account that asked. */
    @Test
    void emailChangeConfirmationGoesToTheNewAddressInTheAccountsLanguage() {
        Sent sent = assertEmail(
                dispatcher -> dispatcher.enqueueEmailChangeConfirmation(recipient("zh-CN"), "new@example.com", LINK),
                "确认你的新邮箱地址", "（链接只能使用一次，有效期 60 分钟）：" + LINE + LINK);
        assertThat(sent.email()).isEqualTo("new@example.com");
        assertEmail(dispatcher -> dispatcher.enqueueEmailChangeConfirmation(recipient("en"), "new@example.com", LINK),
                "Confirm your new email address", "(the link works once, for 60 minutes):" + LINE + LINK);
        assertEmail(dispatcher -> dispatcher.enqueueEmailChangeConfirmation(recipient("ms"), "new@example.com", LINK),
                "Sahkan alamat e-mel baharu anda", "(pautan berfungsi sekali, selama 60 minit):" + LINE + LINK);
    }

    @Test
    void signupConfirmationGreetsTheStudentByName() {
        assertEmail(dispatcher -> dispatcher.enqueueSignupConfirmation(named("en", "Nur Aina"), LINK),
                "Confirm your email address", "Hi Nur Aina, welcome to MandarinSpark!");
        assertEmail(dispatcher -> dispatcher.enqueueSignupConfirmation(named("ms", "Nur Aina"), LINK),
                "Sahkan alamat e-mel anda", "Hai Nur Aina, selamat datang ke MandarinSpark!");
        Sent chinese = assertEmail(dispatcher -> dispatcher.enqueueSignupConfirmation(named("zh-CN", "美玲"), LINK),
                "确认你的邮箱地址", "美玲，你好，欢迎来到 MandarinSpark！");
        assertThat(chinese.body()).contains("（链接只能使用一次，有效期 3 天）：" + LINE + LINK);

        assertThat(delivered(dispatcher -> dispatcher.enqueueSignupConfirmation(named("ms", " "), LINK)).body())
                .startsWith("Hai, selamat datang ke MandarinSpark!");
        assertThat(delivered(dispatcher -> dispatcher.enqueueSignupConfirmation(named("en", null), LINK)).body())
                .startsWith("Hi there, welcome to MandarinSpark!");
    }

    /** The invitee has no account yet: the invite is in the language of the admin who sends it. */
    @Test
    void adminInviteIsInTheInvitingAdminsLanguage() {
        Sent sent = assertEmail(dispatcher -> dispatcher.enqueueAdminInvite("new.admin@example.com",
                        named("ms", "Farid"), LINK),
                "Anda dijemput menjadi pentadbir MandarinSpark",
                "Farid menjemput anda untuk membantu menguruskan MandarinSpark.");
        assertThat(sent.email()).isEqualTo("new.admin@example.com");
        assertThat(sent.body()).contains("(pautan berfungsi selama 7 hari):" + LINE + LINK);
        assertEmail(dispatcher -> dispatcher.enqueueAdminInvite("new.admin@example.com", named("en", "Farid"), LINK),
                "You are invited to be a MandarinSpark admin", "Farid invited you to help run MandarinSpark.");
        assertEmail(dispatcher -> dispatcher.enqueueAdminInvite("new.admin@example.com", named("zh-CN", " "), LINK),
                "你受邀成为 MandarinSpark 管理员", "一位管理员 邀请你协助管理 MandarinSpark。");
    }

    /** The request number and subject stay as they are; the footer follows the supplied requester language. */
    @Test
    void supportReplyFormatsTheFooterInTheSuppliedLanguage() {
        String reply = "Reset your password again: $1 {subject}";
        String subject = "Re: #1042 Can't sign in";
        Sent english = assertEmail(dispatcher -> dispatcher.enqueueSupportReply(EMAIL, "en", 1042, "Can't sign in", reply),
                subject, "MandarinSpark support. Reply to this email or send us a message on the Help page.");
        assertThat(english.email()).isEqualTo(EMAIL);
        assertThat(english.body()).startsWith(reply + LINE + LINE);
        assertEmail(dispatcher -> dispatcher.enqueueSupportReply(EMAIL, "ms", 1042, "Can't sign in", reply),
                subject, "Sokongan MandarinSpark. Balas e-mel ini atau hantar mesej kepada kami di halaman Bantuan.");
        assertEmail(dispatcher -> dispatcher.enqueueSupportReply(EMAIL, "zh-CN", 1042, "Can't sign in", reply),
                subject, "MandarinSpark 客服。直接回复这封邮件，或在帮助页面给我们留言。");
        // A request sent before its language was kept: English.
        assertEmail(dispatcher -> dispatcher.enqueueSupportReply(EMAIL, null, 1042, "Can't sign in", reply),
                subject, "MandarinSpark support. Reply to this email");
        // The requester typed the subject: control characters in it do not reach the header.
        String typedSubject = "Can't\u000Bsign\tin";
        assertThat(delivered(dispatcher -> dispatcher.enqueueSupportReply(EMAIL, "en", 1042, typedSubject, reply))
                .subject()).isEqualTo(subject);
    }

    @Test
    void dailySummaryListsEachWaitingQueueWithItsCountAndLinkInTheAdminsLanguage() {
        List<WaitingQueue> waiting = List.of(
                new WaitingQueue(ReviewQueue.AI_REPORTS, 1, "https://app.example.com/admin/ai"),
                new WaitingQueue(ReviewQueue.SUPPORT_REQUESTS, 3, "https://app.example.com/admin/support"));

        Sent english = delivered(dispatcher -> dispatcher.enqueueReviewQueueDigest(recipient("en"), waiting));
        assertThat(english.subject()).isEqualTo("Your MandarinSpark daily summary");
        assertThat(english.body())
                .startsWith("Waiting for an admin on MandarinSpark:")
                .contains("AI reports: 1" + LINE + "https://app.example.com/admin/ai")
                .contains("Support requests: 3" + LINE + "https://app.example.com/admin/support")
                .doesNotContain("Teacher applications")
                .doesNotContain("Content reviews")
                .endsWith(SWITCH_OFF_NOTE);

        Sent malay = delivered(dispatcher -> dispatcher.enqueueReviewQueueDigest(recipient("ms"), waiting));
        assertThat(malay.subject()).isEqualTo("Ringkasan harian MandarinSpark anda");
        assertThat(malay.body())
                .contains("Laporan AI: 1" + LINE + "https://app.example.com/admin/ai")
                .contains("Permintaan sokongan: 3" + LINE + "https://app.example.com/admin/support")
                .contains("Matikannya di sana");

        Sent chinese = delivered(dispatcher -> dispatcher.enqueueReviewQueueDigest(recipient("zh-CN"), List.of(
                new WaitingQueue(ReviewQueue.SUPPORT_REQUESTS, 12, "https://app.example.com/admin/support"))));
        assertThat(chinese.subject()).isEqualTo("你的 MandarinSpark 每日汇总");
        assertThat(chinese.body())
                .startsWith("MandarinSpark 上等待管理员处理的事项：")
                .contains("支持请求：12" + LINE + "https://app.example.com/admin/support")
                .doesNotContain("AI 举报");
    }

    private record Sent(String email, String subject, String body) {
    }

    /** Sends one e-mail and checks its subject and one line of its body. */
    private static Sent assertEmail(Consumer<TransactionalEmailDispatcher> enqueue, String subject, String bodyLine) {
        Sent sent = delivered(enqueue);
        assertThat(sent.subject()).isEqualTo(subject);
        assertThat(sent.body()).contains(bodyLine);
        return sent;
    }

    /** Enqueues one e-mail on a fresh dispatcher, delivers its event and returns what was sent. */
    private static Sent delivered(Consumer<TransactionalEmailDispatcher> enqueue) {
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        List<Sent> sent = new ArrayList<>();
        TransactionalEmailDispatcher dispatcher = new TransactionalEmailDispatcher(
                eventPublisher, (recipient, subject, body) -> sent.add(new Sent(recipient, subject, body)), TEXTS);
        enqueue.accept(dispatcher);
        ArgumentCaptor<TransactionalEmailDispatcher.EmailDeliveryRequested> event =
                ArgumentCaptor.forClass(TransactionalEmailDispatcher.EmailDeliveryRequested.class);
        verify(eventPublisher).publishEvent(event.capture());
        dispatcher.deliver(event.getValue());
        assertThat(sent).hasSize(1);
        return sent.getFirst();
    }

    private static User recipient(String language) {
        return named(language, "Nur Aina");
    }

    private static User named(String language, String name) {
        User recipient = new User();
        recipient.setEmail(EMAIL);
        recipient.setDisplayName(name);
        recipient.setPreferredLanguage(language);
        return recipient;
    }
}
