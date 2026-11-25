package com.example.aichinese.service.admin;

import static com.example.aichinese.service.AuditLogService.TARGET_USER;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.audit.AuditCategory;
import com.example.aichinese.dto.audit.AuditEntryResponse;
import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.learn.LearnClock;
import com.example.aichinese.util.CsvWriter;
import com.example.aichinese.util.PaginationUtils;
import com.example.aichinese.util.TextUtils;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import lombok.RequiredArgsConstructor;

/** The admin audit log: search with filters, one entry, the actions in use, an account's entries and CSV export. */
@Service
@RequiredArgsConstructor
public class AuditLogQueryService {

    /** Upper bound of one CSV export (about a year of a busy platform's admin activity). */
    private static final int MAX_EXPORT_ROWS = 5000;
    private static final int RECENT_ENTRY_LIMIT = 5;

    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;
    private final LearnClock clock;
    private final EntityManager entityManager;

    /**
     * The audit log's filters; every one is optional and {@code from} / {@code to} are inclusive platform days.
     * {@code userId} keeps the entries by or about one account exactly (see {@link #byOrAbout}), whatever the account
     * is called now or was called when the entry was written.
     */
    public record AuditFilter(
            String keyword, String actionType, String actorEmail, LocalDate from, LocalDate to, Long userId) {

        public AuditFilter {
            validateDateBounds(from);
            validateDateBounds(to);
            if (from != null && to != null && from.isAfter(to)) {
                throw new ResponseStatusException(BAD_REQUEST, "The from date must be on or before the to date.");
            }
        }

        private static void validateDateBounds(LocalDate date) {
            if (date != null && (date.getYear() < 1001 || date.getYear() > 9998)) {
                throw new ResponseStatusException(BAD_REQUEST, "Date filter is out of the supported range.");
            }
        }
    }

    /** Newest first. */
    @Transactional(readOnly = true)
    public PageResponse<AuditEntryResponse> search(AuditFilter filter, int page, int size) {
        Page<AuditLog> entries = PaginationUtils.readPage(PaginationUtils.newestFirst(page, size),
                request -> auditLogRepository.findAll(specification(filter), request));
        return new PageResponse<>(
                toResponses(entries.getContent()), entries.getNumber(), entries.getSize(), entries.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AuditEntryResponse get(Long id) {
        AuditLog entry = auditLogRepository.findDetailedById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Audit entry was not found."));
        return toResponses(List.of(entry)).get(0);
    }

    @Transactional(readOnly = true)
    public List<String> actionTypes() {
        return auditLogRepository.findDistinctActionTypes();
    }

    /**
     * The latest events of an account itself ("Account created", a password change, a deactivation), newest first: the
     * entries about the account, not what the account did elsewhere (an admin's class authoring).
     */
    @Transactional(readOnly = true)
    public List<AuditEntryResponse> recentForAccount(Long userId) {
        return toResponses(auditLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtDescIdDesc(TARGET_USER, userId,
                        Pageable.ofSize(RECENT_ENTRY_LIMIT)));
    }

    /** Every matching entry (up to {@link #MAX_EXPORT_ROWS}), newest first, as CSV text; the export is itself recorded. */
    @Transactional
    public String exportCsv(SecurityUserDetails admin, AuditFilter filter) {
        List<ExportRow> rows = exportRows(filter);
        Set<Long> missingTargetUserIds = rows.stream()
                .filter(ExportRow::needsAccountName)
                .map(ExportRow::targetId)
                .collect(Collectors.toSet());
        Map<Long, String> displayNames = resolveDisplayNames(missingTargetUserIds);
        String csv = renderCsv(rows, displayNames);
        auditLogService.record(admin, "AUDIT_LOG_EXPORTED", "AUDIT_LOG", null,
                "Admin exported " + rows.size() + " audit log rows as CSV.");
        return csv;
    }

    String renderCsv(List<ExportRow> rows, Map<Long, String> displayNames) {
        CsvWriter csv = new CsvWriter()
                .row(List.of("Time", "Action", "Category", "Actor", "Actor email", "Target", "Reason", "Description"));
        for (ExportRow row : rows) {
            String label = row.needsAccountName() ? displayNames.get(row.targetId()) : row.targetLabel();
            csv.row(Arrays.asList(
                    clock.platformOffsetDateTimeOf(row.time()), row.actionType(), AuditCategory.of(row.actionType()).name(),
                    row.actorName(), row.actorEmail(), label, row.reason(), row.description()));
        }
        return csv.toString();
    }

    record ExportRow(LocalDateTime time, String actionType, String actorName, String actorEmail,
                     String targetType, Long targetId, String targetLabel, String reason, String description) {
        boolean needsAccountName() {
            return targetLabel == null && targetId != null && TARGET_USER.equals(targetType);
        }
    }

    /** Reads only the exported values so up to 5,000 rows can be downloaded without loading managed entities. */
    private List<ExportRow> exportRows(AuditFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<ExportRow> query = cb.createQuery(ExportRow.class);
        Root<AuditLog> root = query.from(AuditLog.class);
        Join<AuditLog, User> actor = root.join("actorUser", JoinType.LEFT);
        query.select(cb.construct(ExportRow.class,
                        root.get("createdAt"),
                        root.get("actionType"),
                        actor.get("displayName"),
                        root.get("actorEmail"),
                        root.get("targetType"),
                        root.get("targetId"),
                        root.get("targetLabel"),
                        root.get("reason"),
                        root.get("description")))
                .where(filterPredicates(filter, root, actor, cb))
                .orderBy(cb.desc(root.get("createdAt")), cb.desc(root.get("id")));
        return entityManager.createQuery(query)
                .setMaxResults(MAX_EXPORT_ROWS)
                .getResultList();
    }

    private record TargetAccount(String displayName, boolean deleted) {
    }

    private Map<Long, TargetAccount> resolveTargetAccounts(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, TargetAccount> accounts = new HashMap<>(userIds.size());
        for (Object[] row : entityManager.createQuery(
                "select u.id, u.displayName, u.deletedAt from User u where u.id in :ids", Object[].class)
                .setParameter("ids", userIds)
                .getResultList()) {
            accounts.put((Long) row[0], new TargetAccount((String) row[1], row[2] != null));
        }
        return accounts;
    }

    private Map<Long, String> resolveDisplayNames(Set<Long> userIds) {
        Map<Long, TargetAccount> accounts = resolveTargetAccounts(userIds);
        Map<Long, String> names = new HashMap<>(accounts.size());
        accounts.forEach((id, account) -> names.put(id, account.displayName()));
        return names;
    }

    private Specification<AuditLog> specification(AuditFilter filter) {
        return (root, query, cb) -> cb.and(filterPredicates(filter, root, null, cb));
    }

    private Predicate[] filterPredicates(
            AuditFilter filter, Root<AuditLog> root, Join<AuditLog, User> existingActorJoin, CriteriaBuilder cb) {
        String keyword = TextUtils.lowerCaseOrNull(filter.keyword());
        String actionType = TextUtils.trimToNull(filter.actionType());
        String actorEmail = TextUtils.lowerCaseOrNull(filter.actorEmail());
        List<Predicate> predicates = new ArrayList<>();
        if (filter.userId() != null) {
            predicates.add(byOrAbout(root, cb, filter.userId()));
        }
        if (actionType != null) {
            predicates.add(cb.equal(root.get("actionType"), actionType.toUpperCase(Locale.ROOT)));
        }
        if (actorEmail != null) {
            predicates.add(contains(cb, root.get("actorEmail"), actorEmail));
        }
        if (keyword != null) {
            // "Event, target or name": the action, what it is about, and who did it.
            Join<AuditLog, User> actor = existingActorJoin != null
                    ? existingActorJoin
                    : root.join("actorUser", JoinType.LEFT);
            predicates.add(cb.or(
                    contains(cb, root.get("actionType"), keyword),
                    contains(cb, root.get("targetLabel"), keyword),
                    contains(cb, root.get("actorEmail"), keyword),
                    contains(cb, actor.get("displayName"), keyword)));
        }
        // Platform days (LearnClock), as everywhere else a day is meant; createdAt is server-local.
        if (filter.from() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("createdAt"), clock.startOf(filter.from())));
        }
        if (filter.to() != null) {
            predicates.add(cb.lessThan(root.<LocalDateTime>get("createdAt"), clock.startOf(filter.to().plusDays(1))));
        }
        return predicates.toArray(Predicate[]::new);
    }

    /** Literal substring, including the underscore in an address or an audit action. */
    private static Predicate contains(CriteriaBuilder cb, Expression<String> field, String lowercaseText) {
        return cb.gt(cb.locate(cb.lower(field), lowercaseText), 0);
    }

    /** The entries by or about one account: the account acted, or the entry is about the account. Matched by id. */
    private static Predicate byOrAbout(Root<AuditLog> root, CriteriaBuilder cb, Long userId) {
        return cb.or(
                cb.equal(root.get("actorUser").get("id"), userId),
                cb.and(cb.equal(root.get("targetType"), TARGET_USER), cb.equal(root.get("targetId"), userId)));
    }

    /**
     * Entries without a label (such as EMAIL_CONFIRMED) get one from the account they point to; the accounts are looked
     * up for the whole page at once. {@code targetUserId} lets the audit log open that account.
     */
    private List<AuditEntryResponse> toResponses(List<AuditLog> entries) {
        Set<Long> userIds = entries.stream()
                .filter(entry -> TARGET_USER.equals(entry.getTargetType()) && entry.getTargetId() != null)
                .map(AuditLog::getTargetId)
                .collect(Collectors.toSet());
        Map<Long, TargetAccount> accounts = resolveTargetAccounts(userIds);

        return entries.stream().map(entry -> {
            Long targetId = TARGET_USER.equals(entry.getTargetType()) ? entry.getTargetId() : null;
            TargetAccount account = targetId == null ? null : accounts.get(targetId);
            boolean targetExists = account != null;
            String label = entry.getTargetLabel() != null
                    ? entry.getTargetLabel()
                    : targetExists ? account.displayName() : null;
            User actor = entry.getActorUser();
            return new AuditEntryResponse(
                    entry.getId(),
                    entry.getActionType(),
                    AuditCategory.of(entry.getActionType()),
                    actor == null ? null : actor.getDisplayName(),
                    entry.getActorEmail(),
                    label,
                    targetExists && !account.deleted() ? targetId : null,
                    entry.getReason(),
                    entry.getDescription(),
                    entry.getCreatedAt());
        }).toList();
    }
}
