package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.ai.XiaomeiResponses;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.XiaomeiMemoryNote;
import com.example.aichinese.entity.enums.PlanReason;
import com.example.aichinese.repository.LearningPlanRepository;
import com.example.aichinese.repository.LearningPlanRepository.XiaomeiPlanGoal;
import com.example.aichinese.repository.XiaomeiMemoryNoteRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.account.CurrentAccount;
import com.example.aichinese.util.AiInputSanitizer;
import com.example.aichinese.util.PersonalDataRedactor;
import com.example.aichinese.util.TextUtils;

import lombok.RequiredArgsConstructor;

/**
 * What Talk with Xiaomei remembers about a learner (S7.14, S7.15): the goal of their active learning plan, and short facts
 * the learner shared in a chat, at most two a chat, as SparkBot found them while writing the chat summary. At most five
 * notes are remembered (the oldest chat note makes way), none twice, none longer than 80 characters, and none with the
 * learner's name, an e-mail address, a phone or ID number, a long number, a link or a word of the content-safety list.
 *
 * <p>The notes are the learner's alone: they are the context of the learner's own chats with Xiaomei and are listed in
 * the chat summary ("Xiaomei will remember"), where "Forget" takes one away at once and its Undo brings it back. A
 * forgotten chat note is deleted for good once the Undo window has passed; a forgotten plan goal stays forgotten until
 * the plan's goal changes. Account deletion deletes them all.</p>
 */
@Service
@RequiredArgsConstructor
public class XiaomeiMemoryService {

    private static final int MAX_NOTES = 5;
    static final int MAX_FACTS_PER_CHAT = 2;
    /** How long a forgotten chat note can be brought back (the Undo of "Forget"). */
    private static final Duration UNDO_WINDOW = Duration.ofMinutes(10);
    private static final String NOTE_NOT_FOUND = "Xiaomei no longer remembers this.";

    /** An address, a link, a redaction placeholder or a run of five digits or more (a postcode, an account number). */
    private static final Pattern CONTACT_DETAIL =
            Pattern.compile("(?i)@|https?://|www\\.|\\[(EMAIL|PHONE|ID_NUMBER)]|\\p{Nd}{5,}");
    private static final Pattern HAN_NAME = Pattern.compile("\\p{IsHan}+");
    /** Splits a learner's display name into contiguous Han and non-Han letter runs, ignoring punctuation. */
    private static final Pattern NAME_TOKENS = Pattern.compile("\\p{IsHan}+|[\\p{L}&&[^\\p{IsHan}]]+");
    /** A final full stop (or exclamation mark) does not make a note another note. */
    private static final Pattern FINAL_STOP = Pattern.compile("[.。!！]+$");
    /** A usable fact must contain at least one letter or digit (rejects punctuation-only notes like "." or "!!!"). */
    private static final Pattern LETTER_OR_DIGIT = Pattern.compile("[\\p{L}\\p{N}]");

    private final XiaomeiMemoryNoteRepository noteRepository;
    private final LearningPlanRepository planRepository;
    private final ContentSafetyService contentSafetyService;
    private final CurrentAccount currentAccount;

    /** The notes Xiaomei remembers about the signed-in learner, the plan's goal first (kept in step with the plan). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<XiaomeiResponses.MemoryNote> notes(SecurityUserDetails currentUser) {
        return remembered(prepare(currentAccount.lock(currentUser)));
    }

    /**
     * After a chat summary: remembers up to two of the facts SparkBot found (the usable ones, see {@link #usableFact}),
     * the oldest chat notes making way, and answers with the notes. A fact Xiaomei already remembers, or one forgotten
     * during the Undo window, is not added again. Without facts (no AI, or nothing worth remembering) the plan's goal is
     * still kept in step.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<XiaomeiResponses.MemoryNote> remember(SecurityUserDetails currentUser, List<String> facts) {
        User user = currentAccount.lock(currentUser);
        List<XiaomeiMemoryNote> notes = prepare(user);
        Set<String> known = new HashSet<>();
        for (XiaomeiMemoryNote note : notes) {
            String knownText = note.isPlanGoal()
                    ? goalForPrompt(note.getText(), note.getPlanReason())
                    : note.getText();
            String normalizedKey = key(knownText);
            if (normalizedKey != null) {
                known.add(normalizedKey);
            }
        }
        List<XiaomeiMemoryNote> added = new ArrayList<>();
        for (String fact : facts) {
            if (added.size() == MAX_FACTS_PER_CHAT) {
                break;
            }
            String text = usableFact(fact, user.getDisplayName());
            if (text != null && known.add(key(text))) {
                added.add(noteRepository.save(newNote(user, text, null)));
            }
        }
        notes.addAll(added);
        keepWithinLimit(notes, added);
        return remembered(notes);
    }

    /**
     * "Forget": the note leaves Xiaomei's memory at once; answers with the notes she still remembers. A note that is
     * not there (another learner's, or one deleted for good) is a 404, and the notes are still tidied up.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = ResponseStatusException.class)
    public List<XiaomeiResponses.MemoryNote> forget(SecurityUserDetails currentUser, Long noteId) {
        List<XiaomeiMemoryNote> notes = prepare(currentAccount.lock(currentUser));
        XiaomeiMemoryNote note = find(notes, noteId);
        if (note.getForgottenAt() == null) {
            note.setForgottenAt(LocalDateTime.now());
        }
        return remembered(notes);
    }

    /**
     * The Undo of "Forget"; answers with the notes. A chat note comes back while the Undo window lasts (then it is
     * deleted for good: a 404, as for "Forget"); the plan's goal comes back until the plan's goal changes.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = ResponseStatusException.class)
    public List<XiaomeiResponses.MemoryNote> restore(SecurityUserDetails currentUser, Long noteId) {
        List<XiaomeiMemoryNote> notes = prepare(currentAccount.lock(currentUser));
        XiaomeiMemoryNote note = find(notes, noteId);
        if (note.getForgottenAt() != null) {
            note.setForgottenAt(null);
            keepWithinLimit(notes, List.of(note));
        }
        return remembered(notes);
    }

    /**
     * What Xiaomei is told about the learner in a chat (her replies and the summary): the goal of the active plan
     * unless the learner told her to forget it, and the facts she remembers, as one sentence of the prompt; empty when
     * she remembers nothing. Reads only, so a reply never waits for a write.
     */
    @Transactional(readOnly = true)
    public String chatContext(User user) {
        List<XiaomeiMemoryNote> notes = noteRepository.findByStudentUserIdOrderByCreatedAtAscIdAsc(user.getId());
        List<String> parts = new ArrayList<>();
        XiaomeiPlanGoal goal = planRepository.findFirstByStudentUserIdAndActiveTrueOrderByVersionDesc(user.getId())
                .filter(XiaomeiMemoryService::hasPromptDescription)
                .orElse(null);
        boolean goalRemembered = goal != null && notes.stream()
                .noneMatch(note -> isPlanGoalNote(goal, note) && note.getForgottenAt() != null);
        String described = goalRemembered ? goalForPrompt(goal) : null;
        if (described != null) {
            parts.add("the goal of their learning plan is \"" + described + "\"");
        }
        List<String> facts = notes.stream()
                .filter(note -> !note.isPlanGoal() && note.getForgottenAt() == null)
                .map(XiaomeiMemoryNote::getText)
                .toList();
        // A newly activated plan can occupy a slot before the next write evicts the oldest chat fact.
        int factLimit = goalRemembered ? MAX_NOTES - 1 : MAX_NOTES;
        int first = Math.max(0, facts.size() - factLimit);
        for (String fact : facts.subList(first, facts.size())) {
            parts.add("\"" + fact + "\"");
        }
        if (parts.isEmpty()) {
            return "";
        }
        return " What Xiaomei remembers about the learner from earlier chats (bring it up only when it fits naturally,"
                + " and never ask for more personal details): " + String.join("; ", parts) + ".";
    }

    /**
     * A fact fit to remember, cleaned up; null when it is too long or holds a name of the learner, an e-mail address, a
     * phone or ID number, a long number, a link or a word of the content-safety list.
     */
    String usableFact(String fact, String learnerName) {
        String text = TextUtils.collapseWhitespaceToNull(
                AiInputSanitizer.sanitize(fact, AiInputSanitizer.DEFAULT_SHORT_INPUT_LIMIT));
        if (text == null) {
            return null;
        }
        String normalizedText = Normalizer.normalize(text, Normalizer.Form.NFKC);
        boolean unsafe = text.length() > XiaomeiMemoryNote.MAX_TEXT_LENGTH
                || !LETTER_OR_DIGIT.matcher(normalizedText).find()
                || !text.equals(PersonalDataRedactor.redact(text))
                || !normalizedText.equals(PersonalDataRedactor.redact(normalizedText))
                || CONTACT_DETAIL.matcher(normalizedText).find()
                || contentSafetyService.containsSensitiveContent(text)
                || namesTheLearner(normalizedText, learnerName);
        return unsafe ? null : text;
    }

    /**
     * Under the account lock, read committed includes notes saved by the preceding request.
     * Deletes the chat notes whose Undo is over and keeps the plan's goal in step with the active plan; returns every
     * note of the learner, remembered or forgotten, oldest first.
     */
    private List<XiaomeiMemoryNote> prepare(User user) {
        noteRepository.deleteChatNotesForgottenBefore(user.getId(), LocalDateTime.now().minus(UNDO_WINDOW));
        List<XiaomeiMemoryNote> notes =
                new ArrayList<>(noteRepository.findByStudentUserIdOrderByCreatedAtAscIdAsc(user.getId()));
        syncPlanGoal(user, notes);
        keepWithinLimit(notes, List.of());
        return notes;
    }

    /**
     * The goal of the active plan is a note: a new goal replaces the note of the one before (forgotten or not), a goal
     * the learner told Xiaomei to forget stays forgotten, and without an active plan there is no such note.
     */
    private void syncPlanGoal(User user, List<XiaomeiMemoryNote> notes) {
        XiaomeiPlanGoal goal = planRepository.findFirstByStudentUserIdAndActiveTrueOrderByVersionDesc(user.getId())
                .filter(XiaomeiMemoryService::hasPromptDescription)
                .orElse(null);
        boolean noted = false;
        for (var iterator = notes.iterator(); iterator.hasNext();) {
            XiaomeiMemoryNote note = iterator.next();
            if (!note.isPlanGoal()) {
                continue;
            }
            if (goal != null && isPlanGoalNote(goal, note) && !noted) {
                noted = true;
            } else {
                noteRepository.delete(note);
                iterator.remove();
            }
        }
        if (goal != null && !noted) {
            notes.add(noteRepository.save(newNote(user, goal.goalTitle(), goal.reason())));
        }
    }

    private static boolean isPlanGoalNote(XiaomeiPlanGoal goal, XiaomeiMemoryNote note) {
        return note.isPlanGoal() && goal.reason() == note.getPlanReason()
                && Objects.equals(goal.goalTitle(), note.getText());
    }

    /** At most {@link #MAX_NOTES} remembered notes: the oldest chat notes not in {@code keep} make way for good. */
    private void keepWithinLimit(List<XiaomeiMemoryNote> notes, Collection<XiaomeiMemoryNote> keep) {
        long excess = notes.stream().filter(note -> note.getForgottenAt() == null).count() - MAX_NOTES;
        for (var iterator = notes.iterator(); excess > 0 && iterator.hasNext();) {
            XiaomeiMemoryNote note = iterator.next();
            if (note.getForgottenAt() == null && !note.isPlanGoal() && !keep.contains(note)) {
                noteRepository.delete(note);
                iterator.remove();
                excess--;
            }
        }
    }

    private static boolean hasPromptDescription(XiaomeiPlanGoal goal) {
        return goalForPrompt(goal) != null;
    }

    private static String goalForPrompt(XiaomeiPlanGoal goal) {
        return goalForPrompt(goal.goalTitle(), goal.reason());
    }

    /** The goal as Xiaomei is told it: the title, or what the reason means; null when it says nothing ("other"). */
    private static String goalForPrompt(String goalTitle, PlanReason reason) {
        if (goalTitle != null && !goalTitle.isBlank()) {
            return goalTitle.strip();
        }
        if (reason == null) {
            return null;
        }
        return switch (reason) {
            case TRAVEL -> "learning Chinese for travel";
            case WORK -> "learning Chinese for work";
            case STUDY -> "learning Chinese for study or an exchange";
            case EVERYDAY -> "using Chinese in daily life";
            case JUST_FOR_ME -> "learning Chinese out of personal interest";
            case OTHER -> null;
        };
    }

    private static XiaomeiMemoryNote find(List<XiaomeiMemoryNote> notes, Long noteId) {
        return notes.stream()
                .filter(note -> note.getId().equals(noteId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, NOTE_NOT_FOUND));
    }

    private static XiaomeiMemoryNote newNote(User user, String text, PlanReason planReason) {
        XiaomeiMemoryNote note = new XiaomeiMemoryNote();
        note.setStudentUser(user);
        note.setText(text);
        note.setPlanReason(planReason);
        return note;
    }

    /** The notes as the summary lists them: the plan's goal first, then the chat notes, oldest first. */
    private static List<XiaomeiResponses.MemoryNote> remembered(List<XiaomeiMemoryNote> notes) {
        return notes.stream()
                .filter(note -> note.getForgottenAt() == null)
                .sorted(Comparator.comparing((XiaomeiMemoryNote note) -> !note.isPlanGoal()))
                .map(note -> new XiaomeiResponses.MemoryNote(note.getId(), note.getText(), note.getPlanReason()))
                .toList();
    }

    /** Two notes are the same note whatever their case, spacing and final full stop. */
    private static String key(String text) {
        String spaced = TextUtils.collapseWhitespaceToNull(text);
        if (spaced == null) {
            return null;
        }
        return FINAL_STOP.matcher(spaced).replaceAll("").toLowerCase(Locale.ROOT);
    }

    /**
     * True when the text holds a part of the learner's name of two letters or more: a whole word of a name in letters,
     * and for a Chinese name also the given name without the family name ("小明" of "李小明").
     */
    private static boolean namesTheLearner(String normalizedText, String learnerName) {
        if (learnerName == null) {
            return false;
        }
        String normalizedName = Normalizer.normalize(learnerName.strip(), Normalizer.Form.NFKC);
        Matcher tokens = NAME_TOKENS.matcher(normalizedName);
        while (tokens.find()) {
            String part = tokens.group();
            int letters = part.codePointCount(0, part.length());
            if (letters < 2) {
                continue;
            }
            if (HAN_NAME.matcher(part).matches()) {
                String givenName = letters > 2 ? part.substring(part.offsetByCodePoints(0, 1)) : part;
                if (normalizedText.contains(givenName)) {
                    return true;
                }
            } else if (wholeWord(part).matcher(normalizedText).find()) {
                return true;
            }
        }
        return false;
    }

    /** Exclude Han characters from word boundaries so Latin names adjacent to Chinese text (e.g. "我是Amira") still match. */
    private static Pattern wholeWord(String word) {
        return Pattern.compile("(?<![\\p{L}\\p{N}&&[^\\p{IsHan}]])" + Pattern.quote(word) + "(?![\\p{L}\\p{N}&&[^\\p{IsHan}]])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }
}
