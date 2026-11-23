package com.example.aichinese.service.learn;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.learn.AdminPracticeSetDetail;
import com.example.aichinese.dto.learn.PracticeSetRequest;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.PracticeCompletionRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.account.CurrentAccount;
import com.example.aichinese.service.learn.PracticeCatalog.SetContent;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import lombok.RequiredArgsConstructor;

/**
 * The admin Practice library: every practice set of the six types (made by the MandarinSpark team; the activities of
 * class lessons are not practice sets). New sets start as drafts; publish, unpublish, archive and restore go live at
 * once and are audited, and a set's first publication tells the active learners ("New in Practice"). Archiving hides
 * a set from students and keeps their XP and progress; a restored set is a draft again, while the Undo of an archive
 * puts it back as it was. The audit log (and so a set's History) keeps a new set, its status changes and the edits of
 * a published set; the edits of a draft are not audited.
 */
@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class PracticeLibraryService {

    private static final String CREATE_ACTION = "PRACTICE_SET_CREATE";
    private static final String UPDATE_ACTION = "PRACTICE_SET_UPDATE";
    private static final String PUBLISH_ACTION = "PRACTICE_SET_PUBLISH";
    private static final String UNPUBLISH_ACTION = "PRACTICE_SET_UNPUBLISH";
    private static final String ARCHIVE_ACTION = "PRACTICE_SET_ARCHIVE";
    private static final String RESTORE_ACTION = "PRACTICE_SET_RESTORE";
    private static final Set<String> STATUS_ACTIONS = Set.of(PUBLISH_ACTION, UNPUBLISH_ACTION, ARCHIVE_ACTION, RESTORE_ACTION);

    private static final Map<String, String> HISTORY_ACTIONS = Map.of(
            CREATE_ACTION, "CREATED",
            UPDATE_ACTION, "UPDATED",
            PUBLISH_ACTION, "PUBLISHED",
            UNPUBLISH_ACTION, "UNPUBLISHED",
            ARCHIVE_ACTION, "ARCHIVED",
            RESTORE_ACTION, "RESTORED");

    private final PracticeCatalog catalog;
    private final PracticeSetDirectory directory;
    private final PracticeCompletionRepository completionRepository;
    private final AuditLogService auditLogService;
    private final AuditLogRepository auditLogRepository;
    private final CurrentAccount currentAccount;
    private final LearnerNotifications learnerNotifications;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public AdminPracticeSetDetail detail(PracticeType type, Long id) {
        return toDetail(type, directory.require(type, id));
    }

    public AdminPracticeSetDetail create(SecurityUserDetails admin, PracticeType type, PracticeSetRequest request) {
        User author = currentAccount.findById(admin.getUserId());
        PracticeActivityContent set = catalog.create(type, request, author);
        audit(admin, CREATE_ACTION, type, set, "Admin created the practice set \"" + set.getTitle() + "\" as a draft.");
        return toDetail(type, set);
    }

    /** Edits go live at once, also on a published set, whose edit is audited; an archived set is read-only. */
    public AdminPracticeSetDetail update(SecurityUserDetails admin, PracticeType type, Long id, PracticeSetRequest request) {
        PracticeActivityContent set = lockSet(type, id);
        if (set.getContentStatus() == ContentStatus.ARCHIVED) {
            throw new ResponseStatusException(CONFLICT, "Restore this set before you edit it.");
        }
        catalog.update(type, set, request);
        if (set.getContentStatus() == ContentStatus.PUBLISHED) {
            audit(admin, UPDATE_ACTION, type, set,
                    "Admin edited the published practice set \"" + set.getTitle() + "\".");
        }
        return toDetail(type, set);
    }

    public AdminPracticeSetDetail publish(SecurityUserDetails admin, PracticeType type, Long id) {
        PracticeActivityContent set = lockSet(type, id);
        if (set.getContentStatus() == ContentStatus.PUBLISHED) {
            throw new ResponseStatusException(BAD_REQUEST, "This set is already published.");
        }
        if (set.getContentStatus() == ContentStatus.ARCHIVED) {
            throw new ResponseStatusException(BAD_REQUEST, "Restore this set instead of publishing it.");
        }
        // Only a never-published draft has no status transitions. Delivery records can disappear with a learner's account.
        boolean firstPublication = recentStatusChanges(type, id, 1).isEmpty();
        AdminPracticeSetDetail published =
                changeStatus(admin, type, set, ContentStatus.PUBLISHED, PUBLISH_ACTION, "published");
        if (firstPublication) {
            learnerNotifications.newPracticeSet(type, set);
        }
        return published;
    }

    /** Back to draft: hidden from students until it is published again. */
    public AdminPracticeSetDetail unpublish(SecurityUserDetails admin, PracticeType type, Long id) {
        PracticeActivityContent set = lockSet(type, id);
        if (set.getContentStatus() != ContentStatus.PUBLISHED) {
            throw new ResponseStatusException(BAD_REQUEST, "Only a published set can be unpublished.");
        }
        return changeStatus(admin, type, set, ContentStatus.DRAFT, UNPUBLISH_ACTION, "unpublished");
    }

    /** Archives a published set or returns a restored draft to the archive when the admin selects Undo. */
    public AdminPracticeSetDetail archive(SecurityUserDetails admin, PracticeType type, Long id) {
        PracticeActivityContent set = lockSet(type, id);
        boolean restoredDraft = set.getContentStatus() == ContentStatus.DRAFT
                && recentStatusChanges(type, id, 1).equals(List.of(RESTORE_ACTION));
        if (set.getContentStatus() != ContentStatus.PUBLISHED && !restoredDraft) {
            throw new ResponseStatusException(BAD_REQUEST, "Only a published set can be archived.");
        }
        return changeStatus(admin, type, set, ContentStatus.ARCHIVED, ARCHIVE_ACTION, "archived");
    }

    private List<String> recentStatusChanges(PracticeType type, Long id, int count) {
        return auditLogRepository.findRecentActions(type.referenceType(), id, STATUS_ACTIONS, Pageable.ofSize(count));
    }

    /** A restored set comes back as a draft (A4.19): an admin checks it and publishes it again. */
    public AdminPracticeSetDetail restore(SecurityUserDetails admin, PracticeType type, Long id) {
        PracticeActivityContent set = requireArchived(type, id);
        return changeStatus(admin, type, set, ContentStatus.DRAFT, RESTORE_ACTION, "restored");
    }

    /**
     * The Undo of Archive: the set goes back to what it was before (published, or a restored draft archived again by
     * the Undo of Restore), unlike Restore, which always makes a draft. Published again, it is audited as published;
     * learners are not told of it again.
     */
    public AdminPracticeSetDetail unarchive(SecurityUserDetails admin, PracticeType type, Long id) {
        PracticeActivityContent set = requireArchived(type, id);
        return recentStatusChanges(type, id, 2).equals(List.of(ARCHIVE_ACTION, RESTORE_ACTION))
                ? changeStatus(admin, type, set, ContentStatus.DRAFT, RESTORE_ACTION, "undid the archiving of")
                : changeStatus(admin, type, set, ContentStatus.PUBLISHED, PUBLISH_ACTION, "undid the archiving of");
    }

    private PracticeActivityContent requireArchived(PracticeType type, Long id) {
        PracticeActivityContent set = lockSet(type, id);
        if (set.getContentStatus() != ContentStatus.ARCHIVED) {
            throw new ResponseStatusException(BAD_REQUEST, "Only an archived set can be restored.");
        }
        return set;
    }

    /** Check platform ownership first, then reload the latest committed state under the set's write lock. */
    private PracticeActivityContent lockSet(PracticeType type, Long id) {
        PracticeActivityContent set = directory.require(type, id);
        entityManager.refresh(set, LockModeType.PESSIMISTIC_WRITE);
        return set;
    }

    private AdminPracticeSetDetail changeStatus(
            SecurityUserDetails admin,
            PracticeType type,
            PracticeActivityContent set,
            ContentStatus target,
            String action,
            String verb) {
        SetContent content = catalog.content(type, set);
        // Publishing a draft checks its material. Undoing an archive restores the previous published state.
        if (target == ContentStatus.PUBLISHED && set.getContentStatus() != ContentStatus.ARCHIVED) {
            catalog.validateForPublication(type, set, content);
        }
        set.setContentStatus(target);
        audit(admin, action, type, set, "Admin " + verb + " the practice set \"" + set.getTitle() + "\".");
        return toDetail(type, set, content);
    }

    private void audit(SecurityUserDetails admin, String action, PracticeType type, PracticeActivityContent set,
            String description) {
        auditLogService.record(admin, action, type.referenceType(), set.getId(), description);
    }

    private AdminPracticeSetDetail toDetail(PracticeType type, PracticeActivityContent set) {
        return toDetail(type, set, catalog.content(type, set));
    }

    private AdminPracticeSetDetail toDetail(PracticeType type, PracticeActivityContent set, SetContent content) {
        List<AdminPracticeSetDetail.HistoryEntry> history = new ArrayList<>(auditLogRepository
                .findByTargetTypeAndTargetIdAndActionTypeInOrderByCreatedAtDescIdDesc(
                        type.referenceType(), set.getId(), HISTORY_ACTIONS.keySet(), Pageable.ofSize(10)).stream()
                .map(entry -> new AdminPracticeSetDetail.HistoryEntry(
                        HISTORY_ACTIONS.get(entry.getActionType()), AuditLogService.actorName(entry), entry.getCreatedAt()))
                .toList());
        if (history.stream().noneMatch(entry -> "CREATED".equals(entry.action()))) {
            history.add(new AdminPracticeSetDetail.HistoryEntry("CREATED", authorName(set), set.getCreatedAt()));
        }
        return new AdminPracticeSetDetail(
                set.getId(),
                type,
                set.getTitle(),
                set.getLearningStage(),
                set.getHskLevel(),
                set.getXpReward(),
                set.getContentStatus(),
                authorName(set),
                completionRepository.countStudentsWhoPlayed(type, set.getId()),
                content.editableItems(),
                content.strokeCount(),
                content.characterStrokes(),
                history);
    }

    private static String authorName(PracticeActivityContent set) {
        return set.getCreatedByUser() == null ? null : set.getCreatedByUser().getDisplayName();
    }
}
