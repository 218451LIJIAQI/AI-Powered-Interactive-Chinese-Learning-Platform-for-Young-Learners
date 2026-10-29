package com.example.aichinese.service.learn;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.learn.AdminPracticeSetDetail;
import com.example.aichinese.dto.learn.AdminPracticeSetRow;
import com.example.aichinese.dto.learn.PracticeSetRequest;
import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningStage;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.PracticeCompletionRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.learn.PracticeCatalog.SetContent;

/**
 * The admin Practice library: every practice set of the seven types (made by the MandarinSpark team, never by
 * teachers). New sets start as drafts; publish, unpublish, archive and restore go live at once and are audited.
 * Archiving hides a set from students and keeps their XP and progress.
 */
@Service
public class PracticeLibraryService {

    private static final Map<String, String> HISTORY_ACTIONS = Map.of(
            "PRACTICE_SET_CREATE", "CREATED",
            "PRACTICE_SET_UPDATE", "UPDATED",
            "PRACTICE_SET_PUBLISH", "PUBLISHED",
            "PRACTICE_SET_UNPUBLISH", "UNPUBLISHED",
            "PRACTICE_SET_ARCHIVE", "ARCHIVED",
            "PRACTICE_SET_RESTORE", "RESTORED");

    private final PracticeCatalog catalog;
    private final PracticeCompletionRepository completionRepository;
    private final AuditLogService auditLogService;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public PracticeLibraryService(
            PracticeCatalog catalog,
            PracticeCompletionRepository completionRepository,
            AuditLogService auditLogService,
            AuditLogRepository auditLogRepository,
            UserRepository userRepository) {
        this.catalog = catalog;
        this.completionRepository = completionRepository;
        this.auditLogService = auditLogService;
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    /** Filters of the library list; null means any. */
    public record Filter(String keyword, PracticeType type, ContentStatus status, LearningStage stage) {
    }

    /**
     * Newest change first across every type. The library is platform content of a bounded size, so the rows are
     * merged and paged in memory.
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminPracticeSetRow> list(Filter filter, int page, int size) {
        String keyword = filter.keyword() == null || filter.keyword().isBlank()
                ? null
                : filter.keyword().strip().toLowerCase(Locale.ROOT);
        List<Row> rows = new ArrayList<>();
        for (PracticeType type : PracticeType.values()) {
            if (filter.type() != null && filter.type() != type) {
                continue;
            }
            catalog.sets(type, filter.status()).stream()
                    .filter(set -> filter.stage() == null || filter.stage() == set.getLearningStage())
                    .filter(set -> keyword == null
                            || (set.getTitle() != null && set.getTitle().toLowerCase(Locale.ROOT).contains(keyword)))
                    .forEach(set -> rows.add(new Row(type, set)));
        }
        rows.sort(Comparator.comparing((Row row) -> row.set().getUpdatedAt(), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(row -> row.set().getId(), Comparator.reverseOrder()));
        return PageResponse.fromList(rows, page, size, slice -> slice.stream().map(this::toRow).toList());
    }

    @Transactional(readOnly = true)
    public AdminPracticeSetDetail detail(PracticeType type, Long id) {
        return toDetail(type, catalog.require(type, id));
    }

    @Transactional
    public AdminPracticeSetDetail create(SecurityUserDetails admin, PracticeType type, PracticeSetRequest request) {
        User author = userRepository.findById(admin.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Admin user could not be found."));
        PracticeActivityContent set = catalog.create(type, request, author);
        audit(admin, "PRACTICE_SET_CREATE", type, set, "Admin created the practice set \"" + set.getTitle() + "\" as a draft.");
        return toDetail(type, set);
    }

    /** Edits go live at once, also on a published set. */
    @Transactional
    public AdminPracticeSetDetail update(SecurityUserDetails admin, PracticeType type, Long id, PracticeSetRequest request) {
        PracticeActivityContent set = catalog.update(type, catalog.require(type, id), request);
        audit(admin, "PRACTICE_SET_UPDATE", type, set, "Admin edited the practice set \"" + set.getTitle() + "\".");
        return toDetail(type, set);
    }

    @Transactional
    public AdminPracticeSetDetail publish(SecurityUserDetails admin, PracticeType type, Long id) {
        PracticeActivityContent set = catalog.require(type, id);
        if (set.getContentStatus() == ContentStatus.PUBLISHED) {
            throw new ResponseStatusException(BAD_REQUEST, "This set is already published.");
        }
        if (set.getContentStatus() == ContentStatus.ARCHIVED) {
            throw new ResponseStatusException(BAD_REQUEST, "Restore this set instead of publishing it.");
        }
        if (catalog.itemCount(type, set) == 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Add at least one item to this set.");
        }
        return changeStatus(admin, type, set, ContentStatus.PUBLISHED, "PRACTICE_SET_PUBLISH", "published");
    }

    /** Back to draft: hidden from students until it is published again. */
    @Transactional
    public AdminPracticeSetDetail unpublish(SecurityUserDetails admin, PracticeType type, Long id) {
        PracticeActivityContent set = catalog.require(type, id);
        if (set.getContentStatus() != ContentStatus.PUBLISHED) {
            throw new ResponseStatusException(BAD_REQUEST, "Only a published set can be unpublished.");
        }
        return changeStatus(admin, type, set, ContentStatus.DRAFT, "PRACTICE_SET_UNPUBLISH", "unpublished");
    }

    @Transactional
    public AdminPracticeSetDetail archive(SecurityUserDetails admin, PracticeType type, Long id) {
        PracticeActivityContent set = catalog.require(type, id);
        if (set.getContentStatus() != ContentStatus.PUBLISHED) {
            throw new ResponseStatusException(BAD_REQUEST, "Only a published set can be archived.");
        }
        return changeStatus(admin, type, set, ContentStatus.ARCHIVED, "PRACTICE_SET_ARCHIVE", "archived");
    }

    /** An archived set was published before, so a restore publishes it again. */
    @Transactional
    public AdminPracticeSetDetail restore(SecurityUserDetails admin, PracticeType type, Long id) {
        PracticeActivityContent set = catalog.require(type, id);
        if (set.getContentStatus() != ContentStatus.ARCHIVED) {
            throw new ResponseStatusException(BAD_REQUEST, "Only an archived set can be restored.");
        }
        return changeStatus(admin, type, set, ContentStatus.PUBLISHED, "PRACTICE_SET_RESTORE", "restored");
    }

    // ===== Internals =====

    private AdminPracticeSetDetail changeStatus(
            SecurityUserDetails admin,
            PracticeType type,
            PracticeActivityContent set,
            ContentStatus target,
            String action,
            String verb) {
        set.setContentStatus(target);
        PracticeActivityContent saved = catalog.save(type, set);
        audit(admin, action, type, saved, "Admin " + verb + " the practice set \"" + saved.getTitle() + "\".");
        return toDetail(type, saved);
    }

    private void audit(SecurityUserDetails admin, String action, PracticeType type, PracticeActivityContent set,
            String description) {
        auditLogService.record(admin, action, targetType(type), set.getId(), description);
    }

    private static String targetType(PracticeType type) {
        return "PRACTICE_" + type.name();
    }

    private AdminPracticeSetRow toRow(Row row) {
        PracticeActivityContent set = row.set();
        return new AdminPracticeSetRow(
                set.getId(),
                row.type(),
                set.getTitle(),
                set.getLearningStage(),
                set.getHskLevel(),
                catalog.itemCount(row.type(), set),
                set.getContentStatus(),
                set.getUpdatedAt());
    }

    private AdminPracticeSetDetail toDetail(PracticeType type, PracticeActivityContent set) {
        SetContent content = catalog.content(type, set);
        List<AdminPracticeSetDetail.HistoryEntry> history = auditLogRepository
                .findTop10ByTargetTypeAndTargetIdOrderByCreatedAtDesc(targetType(type), set.getId()).stream()
                .filter(entry -> HISTORY_ACTIONS.containsKey(entry.getActionType()))
                .map(entry -> new AdminPracticeSetDetail.HistoryEntry(
                        HISTORY_ACTIONS.get(entry.getActionType()), actorName(entry), entry.getCreatedAt()))
                .toList();
        if (history.stream().noneMatch(entry -> "CREATED".equals(entry.action()))) {
            List<AdminPracticeSetDetail.HistoryEntry> withCreation = new ArrayList<>(history);
            withCreation.add(new AdminPracticeSetDetail.HistoryEntry("CREATED", authorName(set), set.getCreatedAt()));
            history = withCreation;
        }
        return new AdminPracticeSetDetail(
                set.getId(),
                type,
                set.getTitle(),
                set.getLearningStage(),
                set.getHskLevel(),
                set.getXpReward() == null ? 0 : set.getXpReward(),
                set.getContentStatus(),
                authorName(set),
                set.getCreatedAt(),
                set.getUpdatedAt(),
                completionRepository.countStudentsWhoPlayed(type, set.getId()),
                content.items().stream().map(PracticeCatalog.Item::data).toList(),
                content.storyLines(),
                content.strokeCount(),
                history);
    }

    private static String authorName(PracticeActivityContent set) {
        return set.getCreatedByUser() == null ? null : set.getCreatedByUser().getDisplayName();
    }

    private static String actorName(AuditLog entry) {
        return entry.getActorUser() != null ? entry.getActorUser().getDisplayName() : entry.getActorEmail();
    }

    private record Row(PracticeType type, PracticeActivityContent set) {
    }
}
