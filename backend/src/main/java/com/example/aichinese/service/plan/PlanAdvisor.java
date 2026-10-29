package com.example.aichinese.service.plan;

import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.MandarinLevel;
import com.example.aichinese.entity.enums.PlanStepKind;
import com.example.aichinese.service.ai.AiGateway;
import com.example.aichinese.service.ai.AiJson;
import com.example.aichinese.util.AiInputSanitizer;
import com.example.aichinese.util.TextUtils;

/**
 * SparkBot's part of a plan, through the AI module's gateway (availability, safety check, quota and the AI-usage
 * log under "Learning plans"). It only summarises the goal, maps it to skills, orders the steps the system chose,
 * explains each step in one sentence, lists outcomes and names a first milestone for an unrealistic timeline. Every
 * id it returns is checked against the catalogue it was sent and an unknown one is dropped; an unknown level is
 * ignored. When SparkBot is unavailable (or the daily AI limit is reached) there is no draft and the plan is built
 * from rules.
 */
@Component
public class PlanAdvisor {

    static final int MAX_TITLE = 60;
    static final int MAX_SUMMARY = 400;
    static final int MAX_REASON = 220;
    static final int MAX_ITEM = 120;
    static final int MAX_SKILL = 40;
    static final int MAX_MILESTONE = 300;
    static final int MAX_OUTCOMES = 6;
    static final int MAX_WORK_ON = 4;

    private final AiGateway gateway;

    public PlanAdvisor(AiGateway gateway) {
        this.gateway = gateway;
    }

    public Optional<PlanComposer.AiDraft> draft(
            User user,
            String language,
            PlanComposer.Goal goal,
            PlanComposer.Placement placement,
            PlanComposer.Candidates candidates,
            LocalDate today) {
        String prompt = prompt(goal, placement, candidates, today, language);
        try {
            var result = gateway.execute(AiGateway.AiCall.json(user, AiFeatureKey.LEARNING_PLAN, language, prompt,
                            json -> AiJson.hasText(json, "summary") && json.get("steps") instanceof List<?>)
                    .withRelatedEntity("LEARNING_PLAN", null));
            if (!result.succeeded()) {
                return Optional.empty();
            }
            return Optional.of(parse(result.json(), candidates));
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == TOO_MANY_REQUESTS.value()) {
                // The daily AI limit: the plan is still built, from rules.
                return Optional.empty();
            }
            throw exception;
        }
    }

    // ===== Prompt =====

    static String prompt(
            PlanComposer.Goal goal,
            PlanComposer.Placement placement,
            PlanComposer.Candidates candidates,
            LocalDate today,
            String language) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Help plan the Mandarin learning path of a young adult beginner on MandarinSpark.")
                .append(" The platform already chose the Learn units and the level; you only summarise the goal, map it")
                .append(" to skills, order the steps and explain them. Use only the ids listed below.\n");
        prompt.append("Main reason: ").append(goal.reason().name().toLowerCase(Locale.ROOT).replace('_', ' '));
        if (goal.reasonText() != null) {
            prompt.append(" (in their words: \"").append(goal.reasonText()).append("\")");
        }
        prompt.append(".\nGoals they picked: ").append(goal.effectiveGoals().stream()
                .map(planGoal -> planGoal.name().toLowerCase(Locale.ROOT).replace('_', ' '))
                .collect(Collectors.joining(", "))).append(".\n");
        if (goal.ownWords() != null) {
            prompt.append("Their goal in their own words: \"").append(goal.ownWords()).append("\".\n");
        }
        if (goal.deadline() != null) {
            long weeks = Math.max(1, (ChronoUnit.DAYS.between(today, goal.deadline()) + 6) / 7);
            prompt.append("Deadline: ").append(goal.deadline()).append(" (in about ").append(weeks).append(" weeks).\n");
        } else {
            prompt.append("No deadline.\n");
        }
        prompt.append("Time: ").append(goal.pace().minutesPerDay()).append(" minutes a day, ")
                .append(goal.pace().daysPerWeek()).append(" days a week.\n");
        if (!goal.focus().isEmpty()) {
            prompt.append("They want to get better at: ").append(goal.focus().stream()
                    .map(focus -> focus.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", "))).append(".\n");
        }
        prompt.append("Level check: ").append(levelName(placement.level()))
                .append(placement.headStart() ? " with a head start" : "");
        if (!placement.knownSkills().isEmpty()) {
            prompt.append("; already knows ").append(placement.knownSkills().stream()
                    .map(skill -> skill.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", ")));
        }
        if (!placement.newSkills().isEmpty()) {
            prompt.append("; new: ").append(placement.newSkills().stream()
                    .map(skill -> skill.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", ")));
        }
        prompt.append(".\nLearn units of the plan, required, in this order:\n");
        for (PlanCatalogue.Unit unit : candidates.units()) {
            prompt.append("- U").append(unit.id()).append(": Unit ").append(unit.number()).append(" \"")
                    .append(unit.title()).append(" ").append(unit.titleZh()).append("\" (")
                    .append(levelName(PlanComposer.levelOfPath(unit.level()))).append("; lessons: ")
                    .append(unit.lessons().stream().map(stop -> stop.title()).collect(Collectors.joining(", ")))
                    .append(")\n");
        }
        if (!candidates.classes().isEmpty()) {
            prompt.append("Teacher classes the student may add (optional; at most 2):\n");
            for (PlanCatalogue.ClassOption option : candidates.classes()) {
                prompt.append("- C").append(option.id()).append(": \"").append(option.title()).append("\" (")
                        .append(TextUtils.defaultIfBlank(option.levelName(), "any level")).append("; ")
                        .append(TextUtils.defaultIfBlank(option.topicName(), "general"))
                        .append(option.joined() ? "; the student already joined it" : "").append("): ")
                        .append(TextUtils.defaultIfBlank(AiInputSanitizer.sanitize(option.description(), 200), ""))
                        .append("\n");
            }
        }
        prompt.append("Write every text in the learner's language (").append(language).append(").")
                .append(" Return JSON with keys: goalTitle (at most 6 words, e.g. \"Travel to Shanghai\"),")
                .append(" summary (one or two sentences to the student, in the second person, saying what they want to")
                .append(" be able to do), workOn (2 to 4 short skill topics the goal needs, e.g. \"food words\"),")
                .append(" targetLevel (NEW_TO_MANDARIN, BEGINNER_1 or BEGINNER_2: a realistic level for the time given),")
                .append(" steps (the plan in order: every U id above in the same order, plus 0 to 2 C ids placed where")
                .append(" they help most; each item {id, reason} with reason one short sentence on why the step helps")
                .append(" this goal), outcomes (4 to 6 short things they should be able to do by the end, each starting")
                .append(" with a verb), unrealistic (true only when the goal cannot be reached in the time given, such")
                .append(" as fluency in a few weeks), milestone (only when unrealistic: one sentence with a first")
                .append(" milestone they can reach by the deadline). Never promise results or grades.");
        return prompt.toString();
    }

    private static String levelName(MandarinLevel level) {
        return switch (level) {
            case BEGINNER_2 -> "Beginner 2 (HSK 2)";
            case BEGINNER_1 -> "Beginner 1 (HSK 1)";
            default -> "New to Mandarin";
        };
    }

    // ===== Validation =====

    /** Keeps only what can be trusted: known ids (each once), a known level, and texts within their limits. */
    public static PlanComposer.AiDraft parse(Map<String, Object> json, PlanComposer.Candidates candidates) {
        Set<Long> unitIds = candidates.units().stream().map(PlanCatalogue.Unit::id).collect(Collectors.toSet());
        Set<Long> classIds = candidates.classes().stream().map(PlanCatalogue.ClassOption::id).collect(Collectors.toSet());
        Set<String> seen = new HashSet<>();
        List<PlanComposer.DraftStep> steps = new ArrayList<>();
        for (Map<?, ?> item : AiJson.objects(json, "steps", 20, item -> item)) {
            String id = TextUtils.trimToNull(AiJson.text(item, "id"));
            if (id == null || id.length() < 2 || !seen.add(id.toUpperCase(Locale.ROOT))) {
                continue;
            }
            Long refId = parseId(id.substring(1));
            char kind = Character.toUpperCase(id.charAt(0));
            String reason = clip(AiJson.text(item, "reason"), MAX_REASON);
            if (kind == 'U' && refId != null && unitIds.contains(refId)) {
                steps.add(new PlanComposer.DraftStep(PlanStepKind.LEARN_UNIT, refId, reason));
            } else if (kind == 'C' && refId != null && classIds.contains(refId)) {
                steps.add(new PlanComposer.DraftStep(PlanStepKind.CLASS, refId, reason));
            }
        }
        MandarinLevel target = MandarinLevel.tryFrom(AiJson.text(json, "targetLevel")).orElse(null);
        boolean unrealistic = Boolean.TRUE.equals(json.get("unrealistic"))
                || "true".equalsIgnoreCase(String.valueOf(json.get("unrealistic")));
        return new PlanComposer.AiDraft(
                clip(AiJson.text(json, "goalTitle"), MAX_TITLE),
                clip(AiJson.text(json, "summary"), MAX_SUMMARY),
                AiJson.strings(json, "workOn").stream().map(text -> clip(text, MAX_SKILL)).filter(Objects::nonNull)
                        .limit(MAX_WORK_ON).toList(),
                target,
                steps,
                AiJson.strings(json, "outcomes").stream().map(text -> clip(text, MAX_ITEM)).filter(Objects::nonNull)
                        .limit(MAX_OUTCOMES).toList(),
                unrealistic,
                unrealistic ? clip(AiJson.text(json, "milestone"), MAX_MILESTONE) : null);
    }

    private static Long parseId(String digits) {
        try {
            return Long.valueOf(digits.strip());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String clip(String text, int max) {
        String clean = TextUtils.trimToNull(text);
        return clean == null ? null : TextUtils.safeTruncate(clean.replaceAll("\\s+", " "), max);
    }
}
