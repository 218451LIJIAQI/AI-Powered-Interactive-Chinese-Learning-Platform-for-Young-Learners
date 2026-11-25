package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.ai.AiStoryLineResponse;
import com.example.aichinese.dto.ai.XiaomeiConversationRequest;
import com.example.aichinese.dto.ai.XiaomeiReplyRequest;
import com.example.aichinese.dto.ai.XiaomeiResponses;
import com.example.aichinese.dto.ai.XiaomeiTurn;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.XiaomeiScenario;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.account.CurrentAccount;
import com.example.aichinese.service.ai.AiExecutionResult;
import com.example.aichinese.service.ai.AiGateway;
import com.example.aichinese.service.ai.AiJson;
import com.example.aichinese.service.ai.AiPromptBuilder;
import com.example.aichinese.service.ai.AiProviderPrompt;
import com.example.aichinese.util.AiInputSanitizer;
import com.example.aichinese.util.LanguageCodes;
import com.example.aichinese.util.PersonalDataRedactor;

import lombok.RequiredArgsConstructor;

/**
 * Talk with Xiaomei: a spoken-style conversation partner for beginners. The conversation lives in the student's
 * browser and arrives with each request (oldest turn first); nothing of it is stored here, only one AI-usage log row
 * per request. What Xiaomei remembers about the learner ({@link XiaomeiMemoryService}: the plan's goal and a few short
 * notes) is the context of every request, and the summary may add notes. Xiaomei's persona comes from the prompt
 * builder; this service adds the scenario, the context, the turns and the JSON shape of each answer.
 */
@Service
@RequiredArgsConstructor
public class XiaomeiService {

    private static final int MAX_EXPRESSIONS = 4;
    private static final int MAX_WORDS = 12;

    private final CurrentAccount currentAccount;
    private final AiGateway gateway;
    private final XiaomeiMemoryService memory;

    /** Xiaomei's next line to the student's message, with a "more natural" tip when the message needs one. */
    public XiaomeiResponses.Reply reply(SecurityUserDetails currentUser, XiaomeiReplyRequest request) {
        User user = currentAccount.require(currentUser);
        String language = LanguageCodes.forRequest(request.languagePreference(), user.getPreferredLanguage());
        String message = PersonalDataRedactor.redact(AiInputSanitizer.requireLong(request.message(), "Message is required."));
        String prompt = scenario(request.scenario())
                + memory.chatContext(user)
                + " The learner now says: \"" + message + "\"."
                + " Reply as Xiaomei. If the learner wrote English or Malay, answer in simple Chinese and gently"
                + " encourage them to try it in Chinese."
                + " If the learner's Chinese has a mistake or sounds unnatural, give naturalVersion: the same meaning"
                + " in natural Chinese, and tip: one short sentence in the learner's language that explains the"
                + " change; otherwise naturalVersion and tip are null. Never correct a sentence that is already fine."
                + " Return keys: reply (object with chinese, pinyin and translation in "
                + AiPromptBuilder.translationLanguage(language) + "), naturalVersion, tip.";
        AiExecutionResult result = gateway.execute(
                AiGateway.AiCall.json(user, currentUser.getSessionId(), AiFeatureKey.XIAOMEI_CONVERSATION, language, prompt,
                                json -> line(AiJson.object(json, "reply")) != null)
                        .withHistory(history(request.turns())));
        // A "more natural" version that repeats the student's own line corrects nothing: then there is no tip either.
        String suggested = AiJson.text(result.json(), "naturalVersion");
        String naturalVersion = suggested == null || suggested.equals(message) ? null : suggested;
        return new XiaomeiResponses.Reply(
                result.status(),
                result.userMessage(),
                line(AiJson.object(result.json(), "reply")),
                naturalVersion,
                naturalVersion == null ? null : AiJson.text(result.json(), "tip"));
    }

    /**
     * The end-of-chat summary: goodbye, feedback, Xiaomei's useful expressions and the words the student used, and
     * what Xiaomei will remember: the plan's goal and up to two short facts the learner shared (kept by the memory
     * service only when they are safe to keep). Without an AI answer the notes are still listed.
     */
    public XiaomeiResponses.Summary summary(SecurityUserDetails currentUser, XiaomeiConversationRequest request) {
        User user = currentAccount.require(currentUser);
        List<AiProviderPrompt.AiMessage> turns = history(request.turns());
        if (turns.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "The conversation has no turns yet.");
        }
        String language = LanguageCodes.forRequest(request.languagePreference(), user.getPreferredLanguage());
        String translation = AiPromptBuilder.translationLanguage(language);
        String prompt = scenario(request.scenario())
                + memory.chatContext(user)
                + " The learner has ended the conversation. Return keys:"
                + " goodbye (Xiaomei's short goodbye: object with chinese, pinyin and translation in " + translation + "),"
                + " feedback (two short, warm sentences in the learner's language: what went well, and one thing to try"
                + " next time with an example word),"
                + " newExpressions (up to " + MAX_EXPRESSIONS + " useful expressions Xiaomei used: objects with chinese,"
                + " pinyin and translation in " + translation + "),"
                + " wordsUsed (the Chinese words the learner used, at most " + MAX_WORDS + ": objects with chinese,"
                + " pinyin and translation = the meaning in " + translation + "),"
                + " remember (an array of at most " + XiaomeiMemoryService.MAX_FACTS_PER_CHAT + " short facts the"
                + " learner shared about themselves in this chat that would help Xiaomei in later chats, each a phrase of"
                + " at most 60 characters in the learner's language, such as \"Studies Chinese at school\"; never a name,"
                + " an e-mail address, a phone number, an address or another contact detail, and nothing Xiaomei already"
                + " remembers; an empty array when there is nothing worth remembering).";
        AiExecutionResult result = gateway.execute(
                AiGateway.AiCall.json(user, currentUser.getSessionId(), AiFeatureKey.XIAOMEI_CONVERSATION, language, prompt,
                                json -> AiJson.hasText(json, "feedback"))
                        .withHistory(turns));
        Map<String, Object> json = result.json();
        return new XiaomeiResponses.Summary(
                result.status(),
                result.userMessage(),
                line(AiJson.object(json, "goodbye")),
                AiJson.text(json, "feedback"),
                AiJson.objects(json, "newExpressions", MAX_EXPRESSIONS, XiaomeiService::line),
                AiJson.objects(json, "wordsUsed", MAX_WORDS, XiaomeiService::line),
                memory.remember(currentUser, AiJson.strings(json, "remember")));
    }

    private static String scenario(XiaomeiScenario scenario) {
        return "Scenario: " + scenario.situation();
    }

    /** The conversation so far as provider turns: Xiaomei speaks as the assistant, the student as the user. */
    private static List<AiProviderPrompt.AiMessage> history(List<XiaomeiTurn> turns) {
        return turns.stream()
                .map(turn -> new AiProviderPrompt.AiMessage(
                        turn.speaker() == XiaomeiTurn.Speaker.XIAOMEI ? "assistant" : "user",
                        AiInputSanitizer.sanitize(turn.text(), XiaomeiTurn.MAX_TEXT_LENGTH)))
                .filter(turn -> turn.content() != null)
                .toList();
    }

    /** A line with Chinese text, or null. */
    private static AiStoryLineResponse line(Map<?, ?> value) {
        String chinese = AiJson.text(value, "chinese");
        return chinese == null
                ? null
                : new AiStoryLineResponse(chinese, AiJson.text(value, "pinyin"), AiJson.text(value, "translation"));
    }
}
