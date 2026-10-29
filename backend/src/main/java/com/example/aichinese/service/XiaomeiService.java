package com.example.aichinese.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.aichinese.dto.ai.XiaomeiConversationRequest;
import com.example.aichinese.dto.ai.XiaomeiReplyRequest;
import com.example.aichinese.dto.ai.XiaomeiResponses;
import com.example.aichinese.dto.ai.XiaomeiTurn;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.XiaomeiScenario;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.ai.AiExecutionResult;
import com.example.aichinese.service.ai.AiGateway;
import com.example.aichinese.service.ai.AiJson;
import com.example.aichinese.service.ai.AiProviderPrompt;
import com.example.aichinese.service.ai.AiUserResolver;
import com.example.aichinese.util.AiInputSanitizer;
import com.example.aichinese.util.PersonalDataRedactor;

/**
 * Talk with Xiaomei: a spoken-style conversation partner for beginners. The conversation lives in the student's
 * browser and arrives with each request (oldest turn first); nothing of it is stored here, only one AI-usage log row
 * per request. Xiaomei's persona comes from the prompt builder; this service adds the scenario, the turns and the
 * JSON shape of each answer.
 */
@Service
public class XiaomeiService {

    private static final int SUGGESTION_COUNT = 3;
    private static final int MAX_EXPRESSIONS = 4;
    private static final int MAX_WORDS = 12;

    private final AiUserResolver users;
    private final AiGateway gateway;

    public XiaomeiService(AiUserResolver users, AiGateway gateway) {
        this.users = users;
        this.gateway = gateway;
    }

    /** Xiaomei's next line to the student's message, with a "more natural" tip when the message needs one. */
    public XiaomeiResponses.Reply reply(SecurityUserDetails currentUser, XiaomeiReplyRequest request) {
        User user = users.require(currentUser);
        String language = AiUserResolver.language(user, request.languagePreference());
        String message = PersonalDataRedactor.redact(AiService.requireLong(request.message(), "Message is required."));
        String prompt = scenario(request.scenario())
                + " The learner now says: \"" + message + "\"."
                + " Reply as Xiaomei. If the learner wrote English or Malay, answer in simple Chinese and gently"
                + " encourage them to try it in Chinese."
                + " If the learner's Chinese has a mistake or sounds unnatural, give naturalVersion: the same meaning"
                + " in natural Chinese, and tip: one short sentence in the learner's language that explains the"
                + " change; otherwise naturalVersion and tip are null. Never correct a sentence that is already fine."
                + " Return keys: reply (object with chinese, pinyin and translation in "
                + AiUserResolver.translationLanguage(language) + "), naturalVersion, tip.";
        AiExecutionResult result = gateway.execute(
                AiGateway.AiCall.json(user, AiFeatureKey.XIAOMEI_CONVERSATION, language, prompt,
                                json -> line(AiJson.object(json, "reply")) != null)
                        .withHistory(history(request.turns())));
        if (!result.succeeded()) {
            return new XiaomeiResponses.Reply(result.status().name(), result.userMessage(), null, null, null);
        }
        String naturalVersion = AiJson.text(result.json(), "naturalVersion");
        return new XiaomeiResponses.Reply(
                result.status().name(),
                null,
                line(AiJson.object(result.json(), "reply")),
                naturalVersion == null || naturalVersion.equals(message) ? null : naturalVersion,
                naturalVersion == null ? null : AiJson.text(result.json(), "tip"));
    }

    /** "Help me reply": three short, different things the student could say next. */
    public XiaomeiResponses.Suggestions suggestions(SecurityUserDetails currentUser, XiaomeiConversationRequest request) {
        User user = users.require(currentUser);
        String language = AiUserResolver.language(user, request.languagePreference());
        String prompt = scenario(request.scenario())
                + " The learner is stuck. Suggest " + SUGGESTION_COUNT + " short, different replies the learner could"
                + " say next to your last line (HSK 1-2, at most 15 characters each)."
                + " Return keys: suggestions (array of objects with chinese, pinyin and translation in "
                + AiUserResolver.translationLanguage(language) + ").";
        AiExecutionResult result = gateway.execute(
                AiGateway.AiCall.json(user, AiFeatureKey.XIAOMEI_CONVERSATION, language, prompt,
                                json -> !lines(json, "suggestions", SUGGESTION_COUNT).isEmpty())
                        .withHistory(history(request.turns())));
        if (!result.succeeded()) {
            return new XiaomeiResponses.Suggestions(result.status().name(), result.userMessage(), List.of());
        }
        return new XiaomeiResponses.Suggestions(
                result.status().name(), null, lines(result.json(), "suggestions", SUGGESTION_COUNT));
    }

    /** The end-of-chat summary: goodbye, feedback, Xiaomei's useful expressions and the words the student used. */
    public XiaomeiResponses.Summary summary(SecurityUserDetails currentUser, XiaomeiConversationRequest request) {
        User user = users.require(currentUser);
        String language = AiUserResolver.language(user, request.languagePreference());
        String translation = AiUserResolver.translationLanguage(language);
        String prompt = scenario(request.scenario())
                + " The learner has ended the conversation. Return keys:"
                + " goodbye (Xiaomei's short goodbye: object with chinese, pinyin and translation in " + translation + "),"
                + " feedback (two short, warm sentences in the learner's language: what went well, and one thing to try"
                + " next time with an example word),"
                + " newExpressions (up to " + MAX_EXPRESSIONS + " useful expressions Xiaomei used: objects with chinese,"
                + " pinyin and translation in " + translation + "),"
                + " wordsUsed (the Chinese words the learner used, at most " + MAX_WORDS + ": objects with chinese,"
                + " pinyin and translation = the meaning in " + translation + ").";
        AiExecutionResult result = gateway.execute(
                AiGateway.AiCall.json(user, AiFeatureKey.XIAOMEI_CONVERSATION, language, prompt,
                                json -> AiJson.hasText(json, "feedback"))
                        .withHistory(history(request.turns())));
        if (!result.succeeded()) {
            return new XiaomeiResponses.Summary(result.status().name(), result.userMessage(), null, null, List.of(),
                    List.of());
        }
        Map<String, Object> json = result.json();
        return new XiaomeiResponses.Summary(
                result.status().name(),
                null,
                line(AiJson.object(json, "goodbye")),
                AiJson.text(json, "feedback"),
                lines(json, "newExpressions", MAX_EXPRESSIONS),
                lines(json, "wordsUsed", MAX_WORDS));
    }

    private static String scenario(XiaomeiScenario scenario) {
        return "Scenario: " + scenario.situation();
    }

    /** The conversation so far as provider turns: Xiaomei speaks as the assistant, the student as the user. */
    private static List<AiProviderPrompt.AiMessage> history(List<XiaomeiTurn> turns) {
        return turns.stream()
                .map(turn -> new AiProviderPrompt.AiMessage(
                        turn.speaker() == XiaomeiTurn.Speaker.XIAOMEI ? "assistant" : "user",
                        AiInputSanitizer.sanitizeShort(turn.text())))
                .filter(turn -> turn.content() != null)
                .toList();
    }

    private static List<XiaomeiResponses.Line> lines(Map<?, ?> json, String key, int maxItems) {
        return AiJson.objects(json, key, maxItems, XiaomeiService::line);
    }

    /** A line with Chinese text, or null. */
    private static XiaomeiResponses.Line line(Map<?, ?> value) {
        String chinese = AiJson.text(value, "chinese");
        return chinese == null
                ? null
                : new XiaomeiResponses.Line(chinese, AiJson.text(value, "pinyin"), AiJson.text(value, "translation"));
    }
}
