package com.example.aichinese.service.support;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import com.example.aichinese.repository.SupportMessageRepository;
import com.example.aichinese.repository.SupportRequestRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.repository.projection.StatusCountProjection;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.AuditLogService.AuditTarget;
import com.example.aichinese.service.FileStorageService;
import com.example.aichinese.service.NotificationService;
import com.example.aichinese.service.StoredFileDownload;
import com.example.aichinese.service.TransactionalEmailDispatcher;
import com.example.aichinese.service.account.AccountRoles;
import com.example.aichinese.util.PaginationUtils;
import com.example.aichinese.util.TextUtils;

/**
 * Help & support: requests from students, teachers and visitors, answered by admins in the Support queue.
 *
 * <p>A request is OPEN while it waits for support, ANSWERED after a support reply (the requester's turn) and SOLVED
 * when the requester or support marks it solved. An answered request nobody replies to closes by itself 7 days after
 * the last support reply; that happens lazily, before any read or write, so it needs no scheduler.
 */
@Service
public class SupportService {

    private static final Duration AUTO_CLOSE_AFTER = Duration.ofDays(7);
    private static final int FIRST_NUMBER = 1000;
    private static final int VISITOR_SUBJECT_LENGTH = 80;
    private static final String TARGET_TYPE = "SupportRequest";
    private static final String ADMIN_SUPPORT_PATH = "/admin/support";
    private static final String NOT_FOUND_MESSAGE = "Support request was not found.";

    private final SupportRequestRepository requestRepository;
    private final SupportMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final TransactionalEmailDispatcher emailDispatcher;

    public SupportService(
            SupportRequestRepository requestRepository,
            SupportMessageRepository messageRepository,
            UserRepository userRepository,
            FileStorageService fileStorageService,
            NotificationService notificationService,
            AuditLogService auditLogService,
            TransactionalEmailDispatcher emailDispatcher) {
        this.requestRepository = requestRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.emailDispatcher = emailDispatcher;
    }

    // ----- requester (signed-in student or teacher) -----

    @Transactional
    public List<SupportRequestSummaryResponse> listOwn(SecurityUserDetails currentUser) {
        closeExpired();
        User requester = requireRequester(currentUser);
        return requestRepository.findByRequesterIdOrderByCreatedAtDescIdDesc(requester.getId()).stream()
                .map(request -> toSummary(request, requester))
                .toList();
    }

    @Transactional
    public SupportRequestDetailResponse getOwn(SecurityUserDetails currentUser, Long requestId) {
        closeExpired();
        User requester = requireRequester(currentUser);
        return toDetail(requireOwn(requester, requestId));
    }

    @Transactional
    public SupportRequestDetailResponse create(
            SecurityUserDetails currentUser, CreateSupportRequest form, MultipartFile screenshot) {
        User requester = requireRequester(currentUser);
        SupportRequest request = newRequest(
                requester,
                requester.getEmail(),
                AccountRoles.has(requester, RoleName.TEACHER) ? SupportRequesterRole.TEACHER : SupportRequesterRole.STUDENT,
                form.topic(),
                form.subject());
        addMessage(request, requester, false, form.message(), storeScreenshot(screenshot, requester));
        notifyAdmins(request, "New support request",
                requester.getDisplayName() + " sent #" + request.getNumber() + " “" + request.getSubject()
                        + "”. Reply within 2 working days.");
        return toDetail(request);
    }

    /** The requester answers support (or adds details); the request waits for support again. */
    @Transactional
    public SupportRequestDetailResponse replyAsRequester(
            SecurityUserDetails currentUser, Long requestId, String body) {
        closeExpired();
        User requester = requireRequester(currentUser);
        SupportRequest request = requireOwn(requester, requestId);
        requireNotSolved(request);
        addMessage(request, requester, false, body, null);
        request.setStatus(SupportRequestStatus.OPEN);
        request.setClosesAt(null);
        requestRepository.save(request);
        notifyAdmins(request, "Reply on a support request",
                requester.getDisplayName() + " replied on #" + request.getNumber() + " “" + request.getSubject() + "”.");
        return toDetail(request);
    }

    @Transactional
    public SupportRequestDetailResponse solveAsRequester(SecurityUserDetails currentUser, Long requestId) {
        closeExpired();
        User requester = requireRequester(currentUser);
        SupportRequest request = requireOwn(requester, requestId);
        if (request.getStatus() != SupportRequestStatus.SOLVED) {
            markSolved(request, SupportClosedBy.REQUESTER);
        }
        return toDetail(request);
    }

    @Transactional
    public StoredFileDownload ownAttachment(SecurityUserDetails currentUser, Long messageId) {
        User requester = requireRequester(currentUser);
        SupportMessage message = messageRepository.findDetailedById(messageId)
                .filter(found -> found.getRequest().getRequester() != null
                        && found.getRequest().getRequester().getId().equals(requester.getId()))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested file could not be found."));
        return attachmentOf(message);
    }

    /**
     * Requests support answered that wait for this user (the Help & support badge). Read-only, like every count, so
     * it also works inside the nav-count read-only transaction: a request past its closing time no longer counts.
     */
    @Transactional(readOnly = true)
    public long countAwaitingRequester(Long userId) {
        return requestRepository.countAnsweredStillOpen(userId, LocalDateTime.now());
    }

    // ----- visitor (public Help page) -----

    @Transactional
    public SupportRequestCreatedResponse createForVisitor(VisitorSupportRequest form) {
        String email = form.email().toLowerCase(Locale.ROOT);
        SupportRequest request = newRequest(
                null, email, SupportRequesterRole.VISITOR, form.topic(), subjectFrom(form.message()));
        addMessage(request, null, false, form.message(), null);
        notifyAdmins(request, "New support request",
                "A visitor (" + email + ") sent #" + request.getNumber() + " “" + request.getSubject()
                        + "”. Reply within 2 working days.");
        return new SupportRequestCreatedResponse(request.getNumber(), email);
    }

    // ----- admin queue -----

    @Transactional
    public SupportQueueResponse queue(SupportRequestStatus status, int page, int size) {
        closeExpired();
        Page<SupportRequest> requests = status == SupportRequestStatus.SOLVED
                ? requestRepository.findByStatusOrderBySolvedAtDescIdDesc(status, PaginationUtils.pageRequest(page, size))
                : requestRepository.findByStatusOrderByUpdatedAtAscIdAsc(status, PaginationUtils.pageRequest(page, size));
        return new SupportQueueResponse(
                counts(),
                PageResponse.from(requests, request -> toSummary(request, request.getRequester())));
    }

    @Transactional
    public SupportRequestDetailResponse get(Long requestId) {
        closeExpired();
        return toDetail(require(requestId));
    }

    /** Support answers: the request becomes ANSWERED and closes by itself 7 days later if nobody replies. */
    @Transactional
    public SupportRequestDetailResponse reply(SecurityUserDetails currentUser, Long requestId, String body) {
        closeExpired();
        User agent = requireUser(currentUser);
        SupportRequest request = require(requestId);
        requireNotSolved(request);
        LocalDateTime now = LocalDateTime.now();
        addMessage(request, agent, true, body, null);
        request.setStatus(SupportRequestStatus.ANSWERED);
        request.setLastSupportReplyAt(now);
        request.setClosesAt(now.plus(AUTO_CLOSE_AFTER));
        requestRepository.save(request);

        User requester = request.getRequester();
        if (requester != null) {
            notificationService.safeCreateNotification(
                    requester,
                    NotificationType.SUPPORT_REQUEST,
                    "Support replied to your request",
                    agent.getDisplayName() + " answered #" + request.getNumber() + " “" + request.getSubject()
                            + "”. Reply, or mark it solved.",
                    TARGET_TYPE,
                    request.getId(),
                    requesterPath(request),
                    agent);
        } else {
            emailDispatcher.enqueueSupportReply(request.getRequesterEmail(), request.getNumber(), request.getSubject(), body);
        }
        auditLogService.record(agent, "SUPPORT_REPLY_SENT", target(request), null,
                "Support replied to request #" + request.getNumber() + ".");
        return toDetail(request);
    }

    @Transactional
    public SupportRequestDetailResponse solve(SecurityUserDetails currentUser, Long requestId) {
        closeExpired();
        User agent = requireUser(currentUser);
        SupportRequest request = require(requestId);
        if (request.getStatus() == SupportRequestStatus.SOLVED) {
            return toDetail(request);
        }
        markSolved(request, SupportClosedBy.SUPPORT);
        if (request.getRequester() != null) {
            notificationService.safeCreateNotification(
                    request.getRequester(),
                    NotificationType.SUPPORT_REQUEST,
                    "Your request was marked as solved",
                    "#" + request.getNumber() + " “" + request.getSubject()
                            + "” is solved. If it happens again, send a new request.",
                    TARGET_TYPE,
                    request.getId(),
                    requesterPath(request),
                    agent);
        }
        auditLogService.record(agent, "SUPPORT_REQUEST_SOLVED", target(request), null,
                "Support marked request #" + request.getNumber() + " as solved.");
        return toDetail(request);
    }

    @Transactional
    public StoredFileDownload attachment(Long messageId) {
        return attachmentOf(messageRepository.findDetailedById(messageId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested file could not be found.")));
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
                        SupportRequestStatus.OPEN, PaginationUtils.pageRequest(0, limit))
                .map(request -> toSummary(request, request.getRequester()))
                .getContent();
    }

    // ----- shared -----

    /** Closes answered requests whose 7 days without a reply have passed (inside the caller's transaction). */
    private void closeExpired() {
        requestRepository.closeExpired(LocalDateTime.now());
    }

    private SupportRequest newRequest(
            User requester, String email, SupportRequesterRole role, SupportTopic topic, String subject) {
        SupportRequest request = new SupportRequest();
        // Numbers are unique (uk_support_requests_number); two requests sent in the same instant fail one of them,
        // which the requester simply sends again.
        request.setNumber(Math.max(FIRST_NUMBER - 1, requestRepository.findHighestNumber()) + 1);
        request.setRequester(requester);
        request.setRequesterEmail(email);
        request.setRequesterRole(role);
        request.setTopic(topic);
        request.setSubject(subject);
        request.setStatus(SupportRequestStatus.OPEN);
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
        // The queue orders by the last change, so a reply moves the request.
        request.setUpdatedAt(LocalDateTime.now());
    }

    private void markSolved(SupportRequest request, SupportClosedBy closedBy) {
        request.setStatus(SupportRequestStatus.SOLVED);
        request.setSolvedAt(LocalDateTime.now());
        request.setClosedBy(closedBy);
        request.setClosesAt(null);
        requestRepository.save(request);
    }

    private UploadedFile storeScreenshot(MultipartFile screenshot, User owner) {
        if (screenshot == null || screenshot.isEmpty()) {
            return null;
        }
        return fileStorageService.storeSupportScreenshot(screenshot, owner);
    }

    private StoredFileDownload attachmentOf(SupportMessage message) {
        if (message.getAttachment() == null) {
            throw new ResponseStatusException(NOT_FOUND, "Requested file could not be found.");
        }
        return fileStorageService.loadSupportScreenshot(message.getAttachment());
    }

    private void notifyAdmins(SupportRequest request, String title, String message) {
        for (User admin : userRepository.findAllByRoleName(RoleName.ADMIN)) {
            if (admin.isActive()) {
                notificationService.safeCreateNotification(
                        admin,
                        NotificationType.SUPPORT_REQUEST,
                        title,
                        message,
                        TARGET_TYPE,
                        request.getId(),
                        ADMIN_SUPPORT_PATH + "?request=" + request.getId(),
                        request.getRequester());
            }
        }
    }

    private Map<SupportRequestStatus, Long> counts() {
        Map<SupportRequestStatus, Long> counts = new EnumMap<>(SupportRequestStatus.class);
        for (SupportRequestStatus status : SupportRequestStatus.values()) {
            counts.put(status, 0L);
        }
        for (StatusCountProjection row : requestRepository.countGroupedByStatus()) {
            SupportRequestStatus.tryFrom(row.getStatus()).ifPresent(status -> counts.put(status, row.getCount()));
        }
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
        return new SupportRequestDetailResponse(
                toSummary(request, requester), requester == null ? null : requester.getId(), messages);
    }

    private static SupportRequestSummaryResponse toSummary(SupportRequest request, User requester) {
        return new SupportRequestSummaryResponse(
                request.getId(),
                request.getNumber(),
                request.getTopic(),
                request.getSubject(),
                request.getStatus(),
                requester == null ? null : requester.getDisplayName(),
                request.getRequesterEmail(),
                request.getRequesterRole(),
                request.getCreatedAt(),
                request.getUpdatedAt(),
                request.getLastSupportReplyAt(),
                request.getClosesAt(),
                request.getSolvedAt(),
                request.getClosedBy());
    }

    /** The visitor form has no subject: the first line of the message, shortened, stands in for it. */
    private static String subjectFrom(String message) {
        String firstLine = message.strip().lines().findFirst().orElse("").strip();
        return TextUtils.truncateWithEllipsis(firstLine, VISITOR_SUBJECT_LENGTH);
    }

    private static String requesterPath(SupportRequest request) {
        String root = request.getRequesterRole() == SupportRequesterRole.TEACHER ? "/teacher" : "/student";
        return root + "/support/" + request.getId();
    }

    private static AuditTarget target(SupportRequest request) {
        return new AuditTarget(TARGET_TYPE, request.getId(), "#" + request.getNumber() + " " + request.getSubject());
    }

    private static void requireNotSolved(SupportRequest request) {
        if (request.getStatus() == SupportRequestStatus.SOLVED) {
            throw new ResponseStatusException(BAD_REQUEST, "This request is solved. Send a new request if you need more help.");
        }
    }

    private SupportRequest requireOwn(User requester, Long requestId) {
        return requestRepository.findByIdAndRequesterId(requestId, requester.getId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, NOT_FOUND_MESSAGE));
    }

    private SupportRequest require(Long requestId) {
        return requestRepository.findDetailedById(requestId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, NOT_FOUND_MESSAGE));
    }

    /** Students and teachers (applicants included) send requests; admins answer them in the Support queue. */
    private User requireRequester(SecurityUserDetails currentUser) {
        User user = requireUser(currentUser);
        if (AccountRoles.has(user, RoleName.ADMIN)) {
            throw new ResponseStatusException(FORBIDDEN, "Admins answer support requests in the Support queue.");
        }
        return user;
    }

    private User requireUser(SecurityUserDetails currentUser) {
        return userRepository.findWithRolesById(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User was not found."));
    }
}
