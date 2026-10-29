package com.example.aichinese.service.admin;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.audit.AuditCategory;
import com.example.aichinese.dto.audit.AuditEntryResponse;
import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.util.CsvWriter;
import com.example.aichinese.util.PaginationUtils;
import com.example.aichinese.util.TextUtils;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

/** The admin audit log: search with filters, one entry, the actions in use, an account's entries and CSV export. */
@Service
public class AuditLogQueryService {

    public static final String TARGET_USER = "User";
    public static final String TARGET_TEACHER_PROFILE = "TeacherProfile";
    /** Upper bound of one CSV export (about a year of a busy platform's admin activity). */
    static final int MAX_EXPORT_ROWS = 5000;
    private static final int RECENT_ENTRY_LIMIT = 5;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");
    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final TeacherProfileRepository teacherProfileRepository;

    public AuditLogQueryService(
            AuditLogRepository auditLogRepository,
            UserRepository userRepository,
            TeacherProfileRepository teacherProfileRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.teacherProfileRepository = teacherProfileRepository;
    }

    /** The audit log's filters; every one is optional and {@code from} / {@code to} are inclusive days. */
    public record AuditFilter(String keyword, String actionType, String actorEmail, LocalDate from, LocalDate to) {

        public AuditFilter {
            if (from != null && to != null && from.isAfter(to)) {
                throw new ResponseStatusException(BAD_REQUEST, "The from date must be on or before the to date.");
            }
        }
    }

    /** Newest first. */
    @Transactional(readOnly = true)
    public PageResponse<AuditEntryResponse> search(AuditFilter filter, int page, int size) {
        Page<AuditLog> entries = auditLogRepository.findAll(
                specification(filter), newestFirst(page, size));
        return PageResponse.of(
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

    /** The latest entries by or about an account (its user row or its teacher profile). */
    @Transactional(readOnly = true)
    public List<AuditEntryResponse> recentForUser(User user, Long teacherProfileId) {
        Specification<AuditLog> byOrAbout = (root, query, cb) -> {
            List<Predicate> any = new ArrayList<>();
            any.add(cb.equal(root.get("actorUser").get("id"), user.getId()));
            any.add(cb.and(cb.equal(root.get("targetType"), TARGET_USER), cb.equal(root.get("targetId"), user.getId())));
            if (teacherProfileId != null) {
                any.add(cb.and(
                        cb.equal(root.get("targetType"), TARGET_TEACHER_PROFILE),
                        cb.equal(root.get("targetId"), teacherProfileId)));
            }
            return cb.or(any.toArray(Predicate[]::new));
        };
        return toResponses(auditLogRepository.findAll(byOrAbout, newestFirst(0, RECENT_ENTRY_LIMIT))
                .getContent());
    }

    /** Every matching entry (up to {@link #MAX_EXPORT_ROWS}), newest first, as CSV text. */
    @Transactional(readOnly = true)
    public String exportCsv(AuditFilter filter) {
        List<AuditLog> entries = auditLogRepository.findAll(
                        specification(filter),
                        PageRequest.of(0, MAX_EXPORT_ROWS, NEWEST_FIRST))
                .getContent();
        CsvWriter csv = new CsvWriter()
                .row(List.of("Time", "Action", "Category", "Actor", "Actor email", "Target", "Reason", "Description"));
        for (AuditEntryResponse entry : toResponses(entries)) {
            csv.row(Arrays.asList(
                    CSV_TIME.format(entry.createdAt()),
                    entry.actionType(),
                    entry.category().name(),
                    entry.actorName(),
                    entry.actorEmail(),
                    entry.targetLabel(),
                    entry.reason(),
                    entry.description()));
        }
        return csv.toString();
    }

    private Specification<AuditLog> specification(AuditFilter filter) {
        String keyword = lowerOrNull(filter.keyword());
        String actionType = lowerOrNull(filter.actionType());
        String actorEmail = lowerOrNull(filter.actorEmail());
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (actionType != null) {
                predicates.add(cb.equal(cb.lower(root.get("actionType")), actionType));
            }
            if (actorEmail != null) {
                predicates.add(cb.like(cb.lower(root.get("actorEmail")), "%" + actorEmail + "%"));
            }
            if (keyword != null) {
                // "Event, target or name": the action, what it is about, and who did it.
                String pattern = "%" + keyword + "%";
                Join<AuditLog, User> actor = root.join("actorUser", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("actionType")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("targetLabel"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("actorEmail"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(actor.get("displayName"), "")), pattern)));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("createdAt"), filter.from().atStartOfDay()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThan(root.<LocalDateTime>get("createdAt"), filter.to().plusDays(1).atStartOfDay()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * Entries written before targets were labelled get their label from the account or teacher profile they point
     * to, looked up for the whole page at once; {@code targetUserId} lets the audit log open that account.
     */
    private List<AuditEntryResponse> toResponses(List<AuditLog> entries) {
        Map<Long, User> users = usersById(idsOf(entries, TARGET_USER));
        Map<Long, TeacherProfile> profiles = teacherProfileRepository.findAllById(idsOf(entries, TARGET_TEACHER_PROFILE))
                .stream()
                .collect(Collectors.toMap(TeacherProfile::getId, profile -> profile));
        Map<Long, User> profileUsers = usersById(profiles.values().stream()
                .map(profile -> profile.getUser().getId())
                .collect(Collectors.toSet()));

        return entries.stream().map(entry -> {
            User targetUser = switch (TextUtils.defaultIfBlank(entry.getTargetType(), "")) {
                case TARGET_USER -> users.get(entry.getTargetId());
                case TARGET_TEACHER_PROFILE -> {
                    TeacherProfile profile = profiles.get(entry.getTargetId());
                    yield profile == null ? null : profileUsers.get(profile.getUser().getId());
                }
                default -> null;
            };
            String label = entry.getTargetLabel() != null
                    ? entry.getTargetLabel()
                    : targetUser == null ? null : targetUser.getDisplayName();
            User actor = entry.getActorUser();
            return new AuditEntryResponse(
                    entry.getId(),
                    entry.getActionType(),
                    AuditCategory.of(entry.getActionType()),
                    actor == null ? null : actor.getDisplayName(),
                    entry.getActorEmail(),
                    entry.getTargetType(),
                    label,
                    targetUser == null ? null : targetUser.getId(),
                    entry.getReason(),
                    entry.getDescription(),
                    entry.getCreatedAt());
        }).toList();
    }

    private Map<Long, User> usersById(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, User> byId = new HashMap<>();
        userRepository.findByIdIn(ids).forEach(user -> byId.put(user.getId(), user));
        return byId;
    }

    private static Set<Long> idsOf(List<AuditLog> entries, String targetType) {
        return entries.stream()
                .filter(entry -> targetType.equals(entry.getTargetType()) && entry.getTargetId() != null)
                .map(AuditLog::getTargetId)
                .collect(Collectors.toSet());
    }

    private static PageRequest newestFirst(int page, int size) {
        return PaginationUtils.pageRequest(page, size).withSort(NEWEST_FIRST);
    }

    private static String lowerOrNull(String value) {
        String trimmed = TextUtils.trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }
}
