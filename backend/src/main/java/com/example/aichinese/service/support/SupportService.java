package com.example.aichinese.service.support;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.support.CreateSupportRequest;
import com.example.aichinese.dto.support.SupportQueueResponse;
import com.example.aichinese.dto.support.SupportRequestCreatedResponse;
import com.example.aichinese.dto.support.SupportRequestDetailResponse;
import com.example.aichinese.dto.support.SupportRequestSummaryResponse;
import com.example.aichinese.dto.support.VisitorSupportRequest;
import com.example.aichinese.entity.SupportMessage;
import com.example.aichinese.entity.SupportRequest;
import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.entity.enums.SupportClosedBy;
import com.example.aichinese.entity.enums.SupportRequestStatus;
import com.example.aichinese.entity.enums.SupportRequesterRole;
import com.example.aichinese.entity.enums.SupportTopic;
import com.example.aichinese.exception.FriendlyErrorMessageResolver;
import com.example.aichinese.repository.SupportMessageRepository;
import com.example.aichinese.repository.SupportRequestRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.AuditLogService.AuditTarget;
import com.example.aichinese.service.AuthService;
import com.example.aichinese.service.FileStorageService;
import com.example.aichinese.service.NotificationService;
import com.example.aichinese.service.StoredFileDownload;
import com.example.aichinese.service.TransactionalEmailDispatcher;
import com.example.aichinese.service.account.CurrentAccount;
import com.example.aichinese.util.PaginationUtils;
import com.example.aichinese.util.TextUtils;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockModeType;

/**
 * Support requests: a learner's "Report a problem" and a visitor's message on the public Help page, answered by admins
 * in the Support queue. Support's replies go to the requester by e-mail (learners have no support pages).
 *
 * <p>A request is OPEN while it waits for support, ANSWERED after a support reply and SOLVED when support marks it
 * solved (with or without a reply; a solved request can be reopened, the Undo of "Mark as solved"). An answered request
 * closes 7 days after the last support reply. Reading the queue closes expired requests in bounded batches. A write
 * checks the expiry of the request it locks.</p>
 */
@Service
public class SupportService {

    private static final Duration AUTO_CLOSE_AFTER = Duration.ofDays(7);
    private static final int AUTO_CLOSE_BATCH_SIZE = 500;
    private static final int VISITOR_SUBJECT_LENGTH = 80;
    public static final String TARGET_TYPE = "SupportRequest";
    private static final String ADMIN_SUPPORT_PATH = "/admin/support";
    private static final String NOT_FOUND_MESSAGE = "Support request was not found.";

    private final SupportRequestRepository requestRepository;
    private final SupportMessageRepository messageRepository;
    private final SupportRequestNumberSequence numberSequence;
    private final CurrentAccount currentAccount;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final TransactionalEmailDispatcher emailDispatcher;
    private final EntityManager entityManager;
    private final TransactionTemplate readOnlyTransaction;
    private final TransactionTemplate writeTransaction;

    public SupportService(
            SupportRequestRepository requestRepository,
            SupportMessageRepository messageRepository,
            SupportRequestNumberSequence numberSequence,
            CurrentAccount currentAccount,
            FileStorageService fileStorageService,
            NotificationService notificationService,
            AuditLogService auditLogService,
            TransactionalEmailDispatcher emailDispatcher,
            EntityManager entityManager,
            PlatformTransactionManager transactionManager) {
        this.requestRepository = requestRepository;
        this.messageRepository = messageRepository;
        this.numberSequence = numberSequence;
        this.currentAccount = currentAccount;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.emailDispatcher = emailDispatcher;
        this.entityManager = entityManager;
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.writeTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    // A learner ("Report a problem")

    /**
     * Checks account role and topic and stages any screenshot to disk outside database locks, then persists the request
     * and allocates its sequential number in a short READ_COMMITTED transaction.
     */
    public SupportRequestCreatedResponse create(
            SecurityUserDetails currentUser, CreateSupportRequest form, MultipartFile screenshot) {
        readOnlyTransaction.executeWithoutResult(status -> {
            User preview = currentAccount.require(currentUser);
            if (preview.requirePrimaryRole() != RoleName.STUDENT) {
                throw new ResponseStatusException(FORBIDDEN, FriendlyErrorMessageResolver.permissionDeniedMessage());
            }
        });
        requireSelectableTopic(form.topic());
        try (var staged = stageScreenshot(screenshot)) {
            return writeTransaction.execute(status -> {
                User requester = currentAccount.lock(currentUser);
                if (requester.requirePrimaryRole() != RoleName.STUDENT) {
                    throw new ResponseStatusException(FORBIDDEN, FriendlyErrorMessageResolver.permissionDeniedMessage());
                }
                UploadedFile attachment = staged == null
                        ? null
                        : fileStorageService.saveStagedUpload(staged, requester);
                SupportRequest request = newRequest(
                        requester, requester.getEmail(), null, SupportRequesterRole.STUDENT, form.topic(), form.subject());
                addMessage(request, requester, false, form.message(), attachment);
                notifyAdminsOfNewRequest(request, requester.getDisplayName());
                return new SupportRequestCreatedResponse(request.getNumber(), request.getRequesterEmail());
            });
        }
    }

    // Visitor (the public Help page)

    @Transactional
    public SupportRequestCreatedResponse createForVisitor(VisitorSupportRequest form) {
        requireSelectableTopic(form.topic());
        String email = AuthService.normalizeEmail(form.email());
        // The visitor has no account: the replies e-mailed to them follow the language they wrote from.
        SupportRequest request = newRequest(
                null, email, form.language(), SupportRequesterRole.VISITOR, form.topic(), subjectFrom(form.message()));
        addMessage(request, null, false, form.message(), null);
        notifyAdminsOfNewRequest(request, "A visitor (" + email + ")");
        return new SupportRequestCreatedResponse(request.getNumber(), email);
    }

    // Admin queue

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SupportQueueResponse queue(SupportRequestStatus status, int page, int size) {
        LocalDateTime now = LocalDateTime.now();
        // Lock candidate expired requests on PRIMARY in ascending id order (matching account deletion) in bounded batches.
        List<Long> expiredIds = requestRepository.findExpiredIds(now);
        for (int from = 0; from < expiredIds.size(); from += AUTO_CLOSE_BATCH_SIZE) {
            List<Long> batch = expiredIds.subList(from, Math.min(from + AUTO_CLOSE_BATCH_SIZE, expiredIds.size()));
            List<Long> lockedIds = requestRepository.lockIdsInOrder(batch);
            if (!lockedIds.isEmpty()) {
                requestRepository.closeExpiredByIds(lockedIds, now);
            }
        }
        PageRequest pageRequest = PaginationUtils.pageRequest(page, size);
        Map<SupportRequestStatus, Long> counts = counts();
        long total = counts.getOrDefault(status, 0L);
        List<SupportRequest> requests = List.of();
        if (pageRequest.getOffset() < total && pageRequest.getOffset() <= Integer.MAX_VALUE) {
            requests = status == SupportRequestStatus.SOLVED
                    ? requestRepository.findByStatusOrderBySolvedAtDescIdDesc(status, pageRequest)
                    : requestRepository.findByStatusOrderByUpdatedAtAscIdAsc(status, pageRequest);
        }
        return new SupportQueueResponse(counts, new PageResponse<>(
                requests.stream().map(SupportService::toSummary).toList(), pageRequest.getPageNumber(),
                pageRequest.getPageSize(), total));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SupportRequestDetailResponse get(Long requestId) {
        SupportRequest request = require(requestId);
        LocalDateTime now = LocalDateTime.now();
        if (isAutoCloseDue(request, now)) {
            requestRepository.closeExpiredByIds(List.of(request.getId()), now);
            request = require(requestId);
        }
        return toDetail(request);
    }

    /**
     * Support answers: the reply is e-mailed to the requester (a learner at the account's address, in the account's
     * language; a visitor at the address and in the language of the Help page form). The request becomes ANSWERED and
     * closes by itself 7 days later.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SupportRequestDetailResponse reply(SecurityUserDetails currentUser, Long requestId, String body) {
        LockedSupportWrite locked = lockForWrite(currentUser, requestId);
        User agent = locked.agent();
        SupportRequest request = locked.request();
        if (request.getStatus() == SupportRequestStatus.SOLVED) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "This request is solved. Reopen it first if you need to reply.");
        }
        LocalDateTime now = LocalDateTime.now();
        addMessage(request, agent, true, body, null);
        request.setStatus(SupportRequestStatus.ANSWERED);
        request.setLastSupportReplyAt(now);
        request.setClosesAt(now.plus(AUTO_CLOSE_AFTER));
        // The queue orders by the last change, so a reply moves the request.
        request.setUpdatedAt(now);

        User requester = request.getRequester();
        String email = requester == null ? request.getRequesterEmail() : requester.getEmail();
        String language = requester == null ? request.getRequesterLanguage() : requester.getPreferredLanguage();
        emailDispatcher.enqueueSupportReply(email, language, request.getNumber(), request.getSubject(), body);
        auditLogService.record(agent, "SUPPORT_REPLY_SENT", target(request), null,
                "Support replied to request #" + request.getNumber() + ".");
        return toDetail(request);
    }

    /** Marks the request solved, answered or not; solving it again changes nothing. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SupportRequestDetailResponse solve(SecurityUserDetails currentUser, Long requestId) {
        LockedSupportWrite locked = lockForWrite(currentUser, requestId);
        User agent = locked.agent();
        SupportRequest request = locked.request();
        if (request.getStatus() == SupportRequestStatus.SOLVED) {
            return toDetail(request);
        }
        LocalDateTime now = LocalDateTime.now();
        markSolved(request, SupportClosedBy.SUPPORT, now);
        request.setUpdatedAt(now);
        auditLogService.record(agent, "SUPPORT_REQUEST_SOLVED", target(request), null,
                "Support marked request #" + request.getNumber() + " as solved.");
        return toDetail(request);
    }

    /**
     * Reopens a solved request (the Undo of "Mark as solved"): back to ANSWERED when support has replied, otherwise to
     * OPEN. An answered request closes by itself 7 days after the last support reply again, or 7 days after reopening
     * when that time has passed.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SupportRequestDetailResponse reopen(SecurityUserDetails currentUser, Long requestId) {
        LockedSupportWrite locked = lockForWrite(currentUser, requestId);
        User agent = locked.agent();
        SupportRequest request = locked.request();
        if (request.getStatus() != SupportRequestStatus.SOLVED) {
            throw new ResponseStatusException(CONFLICT, "Only a solved request can be reopened.");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastReply = request.getLastSupportReplyAt();
        if (lastReply == null) {
            request.setStatus(SupportRequestStatus.OPEN);
            request.setClosesAt(null);
        } else {
            LocalDateTime closesAt = lastReply.plus(AUTO_CLOSE_AFTER);
            request.setStatus(SupportRequestStatus.ANSWERED);
            request.setClosesAt(closesAt.isAfter(now) ? closesAt : now.plus(AUTO_CLOSE_AFTER));
        }
        request.setSolvedAt(null);
        request.setClosedBy(null);
        request.setUpdatedAt(now);
        auditLogService.record(agent, "SUPPORT_REQUEST_REOPENED", target(request), null,
                "Support reopened request #" + request.getNumber() + ".");
        return toDetail(request);
    }

    public StoredFileDownload attachment(Long messageId) {
        UploadedFile attachment = messageRepository.findAttachmentByMessageId(messageId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested file could not be found."));
        return fileStorageService.loadSupportScreenshot(attachment);
    }

    /** Requests waiting for support (the admin Support badge and dashboard); closing never changes OPEN ones. */
    @Transactional(readOnly = true)
    public long countOpen() {
        return requestRepository.countByStatus(SupportRequestStatus.OPEN);
    }

    /** The oldest requests waiting for support, for the admin dashboard. */
    @Transactional(readOnly = true)
    public List<SupportRequestSummaryResponse> oldestOpen(int limit) {
        return requestRepository.findByStatusOrderByUpdatedAtAscIdAsc(
                        SupportRequestStatus.OPEN, PaginationUtils.pageRequest(0, limit)).stream()
                .map(SupportService::toSummary)
                .toList();
    }

    private record LockedSupportWrite(User agent, SupportRequest request) {
    }

    /**
     * The request and acting admin, locked for a write. Account locks precede the request, matching deletion and admin
     * status changes. READ_COMMITTED on the HTTP write operations also lets their returned thread include replies
     * committed while waiting for these locks. Expire just this request: a reply must not first lock every expired
     * request through the queue's bulk update.
     */
    private LockedSupportWrite lockForWrite(SecurityUserDetails currentUser, Long requestId) {
        SupportRequest request = require(requestId);
        User requester = request.getRequester();
        User actor = currentAccount.lockWith(currentUser, requester);
        if (requester != null && requester.getDeletedAt() != null) {
            throw new ResponseStatusException(NOT_FOUND, NOT_FOUND_MESSAGE);
        }
        // No account lock guards a visitor's request, yet deleting the account with its address removes it (under the
        // request's lock): a write that waited for that lock, or came after it, finds no request.
        try {
            entityManager.refresh(request, LockModeType.PESSIMISTIC_WRITE);
        } catch (EntityNotFoundException deleted) {
            throw new ResponseStatusException(NOT_FOUND, NOT_FOUND_MESSAGE, deleted);
        }
        expireIfDue(request);
        return new LockedSupportWrite(actor, request);
    }

    private void expireIfDue(SupportRequest request) {
        LocalDateTime now = LocalDateTime.now();
        if (isAutoCloseDue(request, now)) {
            markSolved(request, SupportClosedBy.AUTO, request.getClosesAt());
            request.setUpdatedAt(now);
        }
    }

    private static boolean isAutoCloseDue(SupportRequest request, LocalDateTime now) {
        return request.getStatus() == SupportRequestStatus.ANSWERED
                && request.getClosesAt() != null
                && !request.getClosesAt().isAfter(now);
    }

    private static void requireSelectableTopic(SupportTopic topic) {
        if (topic == null || !topic.isSelectable()) {
            throw new ResponseStatusException(BAD_REQUEST, FriendlyErrorMessageResolver.invalidRequestMessage());
        }
    }

    private SupportRequest newRequest(User requester, String email, String language, SupportRequesterRole role,
            SupportTopic topic, String subject) {
        SupportRequest request = new SupportRequest();
        request.setNumber(numberSequence.nextNumber());
        request.setRequester(requester);
        request.setRequesterEmail(email);
        request.setRequesterLanguage(language);
        request.setRequesterRole(role);
        request.setTopic(topic);
        request.setSubject(subject);
        return requestRepository.saveAndFlush(request);
    }

    private void addMessage(SupportRequest request, User author, boolean fromSupport, String body, UploadedFile attachment) {
        SupportMessage message = new SupportMessage();
        message.setRequest(request);
        message.setAuthor(author);
        message.setFromSupport(fromSupport);
        message.setBody(body);
        message.setAttachment(attachment);
        messageRepository.save(message);
    }

    private void markSolved(SupportRequest request, SupportClosedBy closedBy, LocalDateTime solvedAt) {
        request.setStatus(SupportRequestStatus.SOLVED);
        request.setSolvedAt(solvedAt);
        request.setClosedBy(closedBy);
        request.setClosesAt(null);
    }

    private FileStorageService.StagedUpload stageScreenshot(MultipartFile screenshot) {
        if (screenshot == null || screenshot.isEmpty()) {
            return null;
        }
        return fileStorageService.stageSupportScreenshot(screenshot);
    }

    /** {@code sender} names who sent the request: the learner, or the visitor with the address. */
    private void notifyAdminsOfNewRequest(SupportRequest request, String sender) {
        String message = sender + " sent #" + request.getNumber() + " “" + request.getSubject()
                + "”. Reply within 2 working days.";
        Long requestId = request.getId();
        notificationService.notifyActiveAdmins(NotificationType.SUPPORT_REQUEST, "New support request", message,
                TARGET_TYPE, requestId, ADMIN_SUPPORT_PATH + "?request=" + requestId,
                () -> requestRepository.findIdWithReadLock(requestId).isPresent());
    }

    private Map<SupportRequestStatus, Long> counts() {
        Map<SupportRequestStatus, Long> counts = new EnumMap<>(SupportRequestStatus.class);
        for (SupportRequestStatus status : SupportRequestStatus.values()) {
            counts.put(status, 0L);
        }
        requestRepository.groupByStatus().forEach(row -> counts.put(row.getStatus(), row.getCount()));
        return counts;
    }

    private SupportRequestDetailResponse toDetail(SupportRequest request) {
        List<SupportRequestDetailResponse.Message> messages = messageRepository
                .findByRequestIdOrderByCreatedAtAscIdAsc(request.getId()).stream()
                .map(message -> new SupportRequestDetailResponse.Message(
                        message.getId(),
                        message.isFromSupport(),
                        message.getAuthor() == null ? null : message.getAuthor().getDisplayName(),
                        message.getBody(),
                        message.getCreatedAt(),
                        message.getAttachment() == null ? null : new SupportRequestDetailResponse.Attachment(
                                message.getAttachment().getFileName(),
                                message.getAttachment().getFileSize())))
                .toList();
        User requester = request.getRequester();
        return new SupportRequestDetailResponse(toSummary(request), requester == null ? null : requester.getId(), messages);
    }

    private static SupportRequestSummaryResponse toSummary(SupportRequest request) {
        User requester = request.getRequester();
        return new SupportRequestSummaryResponse(
                request.getId(),
                request.getNumber(),
                request.getTopic(),
                request.getSubject(),
                request.getStatus(),
                requester == null ? null : requester.getDisplayName(),
                requester == null ? request.getRequesterEmail() : requester.getEmail(),
                request.getRequesterRole(),
                request.getCreatedAt(),
                request.getUpdatedAt(),
                request.getClosesAt(),
                request.getSolvedAt(),
                request.getClosedBy());
    }

    /**
     * The visitor form has no subject: the first non-blank line of the message (see {@code VisitorSupportRequest}),
     * shortened, stands in for it.
     */
    private static String subjectFrom(String message) {
        return message.lines()
                .map(line -> TextUtils.truncateWithEllipsis(line, VISITOR_SUBJECT_LENGTH))
                .filter(subject -> subject != null)
                .findFirst()
                .orElseGet(() -> TextUtils.truncateWithEllipsis(message, VISITOR_SUBJECT_LENGTH));
    }

    private static AuditTarget target(SupportRequest request) {
        return new AuditTarget(TARGET_TYPE, request.getId(), numberLabel(request) + " " + request.getSubject());
    }

    /**
     * "#1041": the audit label of a request deleted with its requester's account, which keeps the number but not the
     * subject (the requester's own words).
     */
    public static String numberLabel(SupportRequest request) {
        return "#" + request.getNumber();
    }

    private SupportRequest require(Long requestId) {
        return requestRepository.findDetailedById(requestId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, NOT_FOUND_MESSAGE));
    }
}
