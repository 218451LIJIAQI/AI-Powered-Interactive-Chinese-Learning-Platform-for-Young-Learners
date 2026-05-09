package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.communication.CreateSupportTicketRequest;
import com.example.aichinese.dto.communication.SupportTicketDetailResponse;
import com.example.aichinese.dto.communication.SupportTicketMessageRequest;
import com.example.aichinese.dto.communication.SupportTicketResponse;
import com.example.aichinese.dto.communication.UpdateSupportTicketPriorityRequest;
import com.example.aichinese.dto.communication.UpdateSupportTicketStatusRequest;
import com.example.aichinese.entity.SupportTicket;
import com.example.aichinese.entity.SupportTicketMessage;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.entity.enums.SupportTicketPriority;
import com.example.aichinese.entity.enums.SupportTicketStatus;
import com.example.aichinese.repository.SupportTicketMessageRepository;
import com.example.aichinese.repository.SupportTicketRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;

@Service
public class SupportTicketService {

    private final SupportTicketRepository supportTicketRepository;
    private final SupportTicketMessageRepository supportTicketMessageRepository;
    private final UserRepository userRepository;
    private final CommunicationMapper communicationMapper;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final EmailService emailService;

    public SupportTicketService(
            SupportTicketRepository supportTicketRepository,
            SupportTicketMessageRepository supportTicketMessageRepository,
            UserRepository userRepository,
            CommunicationMapper communicationMapper,
            NotificationService notificationService,
            AuditLogService auditLogService,
            EmailService emailService) {
        this.supportTicketRepository = supportTicketRepository;
        this.supportTicketMessageRepository = supportTicketMessageRepository;
        this.userRepository = userRepository;
        this.communicationMapper = communicationMapper;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.emailService = emailService;
    }

    @Transactional
    public SupportTicketResponse createTicket(SecurityUserDetails currentUser, CreateSupportTicketRequest request) {
        User requester = requireUser(currentUser.getUserId());
        SupportTicket ticket = new SupportTicket();
        ticket.setCreatedByUser(requester);
        ticket.setSubject(request.subject().trim());
        ticket.setDescription(request.message().trim());
        ticket.setTicketStatus(SupportTicketStatus.OPEN);
        ticket.setTicketPriority(SupportTicketPriority.MEDIUM);
        SupportTicket savedTicket = supportTicketRepository.save(ticket);

        SupportTicketMessage message = new SupportTicketMessage();
        message.setTicket(savedTicket);
        message.setSenderUser(requester);
        message.setMessage(request.message().trim());
        supportTicketMessageRepository.save(message);

        notifyAdmins(
                NotificationType.SUPPORT_TICKET,
                "New support ticket",
                "A new support ticket has been created.",
                "/admin/support-tickets/" + savedTicket.getId(),
                requester,
                savedTicket.getId());

        return communicationMapper.toSupportTicketResponse(savedTicket);
    }

    @Transactional(readOnly = true)
    public List<SupportTicketResponse> getMyTickets(SecurityUserDetails currentUser) {
        return supportTicketRepository.findByCreatedByUserIdOrderByUpdatedAtDesc(currentUser.getUserId()).stream()
                .map(communicationMapper::toSupportTicketResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SupportTicketDetailResponse getMyTicket(SecurityUserDetails currentUser, Long ticketId) {
        SupportTicket ticket = supportTicketRepository.findByIdAndCreatedByUserId(ticketId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Support ticket was not found."));
        return buildTicketDetail(ticket);
    }

    @Transactional
    public SupportTicketDetailResponse addMyMessage(SecurityUserDetails currentUser, Long ticketId, SupportTicketMessageRequest request) {
        User sender = requireUser(currentUser.getUserId());
        SupportTicket ticket = supportTicketRepository.findByIdAndCreatedByUserId(ticketId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Support ticket was not found."));
        ensureTicketOpenForRequester(ticket);
        saveMessage(ticket, sender, request.message().trim());
        notifyAdmins(
                NotificationType.SUPPORT_TICKET,
                "Support ticket reply",
                "A requester replied to support ticket #" + ticket.getId() + ".",
                "/admin/support-tickets/" + ticket.getId(),
                sender,
                ticket.getId());
        return buildTicketDetail(ticket);
    }

    @Transactional(readOnly = true)
    public List<SupportTicketResponse> getAdminTickets() {
        return supportTicketRepository.findAll().stream()
                .sorted(Comparator.comparing(SupportTicket::getUpdatedAt).reversed())
                .map(communicationMapper::toSupportTicketResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SupportTicketDetailResponse getAdminTicket(Long ticketId) {
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Support ticket was not found."));
        return buildTicketDetail(ticket);
    }

    @Transactional
    public SupportTicketDetailResponse addAdminMessage(SecurityUserDetails currentUser, Long ticketId, SupportTicketMessageRequest request) {
        User admin = requireUser(currentUser.getUserId());
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Support ticket was not found."));
        saveMessage(ticket, admin, request.message().trim());
        notifyRequester(ticket, "Support ticket reply", request.message().trim(), admin);
        auditLogService.safeRecord(
                admin,
                "SUPPORT_TICKET_REPLIED",
                "SUPPORT_TICKET",
                ticket.getId(),
                "Admin replied to support ticket #" + ticket.getId() + ".",
                null);
        return buildTicketDetail(ticket);
    }

    @Transactional
    public SupportTicketResponse updateStatus(SecurityUserDetails currentUser, Long ticketId, UpdateSupportTicketStatusRequest request) {
        User admin = requireUser(currentUser.getUserId());
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Support ticket was not found."));
        ticket.setTicketStatus(parseStatus(request.status()));
        notifyRequester(ticket, "Support ticket status updated", "Your support ticket is now " + ticket.getTicketStatus().name() + ".", admin);
        auditLogService.safeRecord(
                admin,
                "SUPPORT_TICKET_STATUS_UPDATED",
                "SUPPORT_TICKET",
                ticket.getId(),
                "Admin updated support ticket #" + ticket.getId() + " to " + ticket.getTicketStatus().name() + ".",
                null);
        return communicationMapper.toSupportTicketResponse(ticket);
    }

    @Transactional
    public SupportTicketResponse updatePriority(SecurityUserDetails currentUser, Long ticketId, UpdateSupportTicketPriorityRequest request) {
        User admin = requireUser(currentUser.getUserId());
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Support ticket was not found."));
        ticket.setTicketPriority(parsePriority(request.priority()));
        auditLogService.safeRecord(
                admin,
                "SUPPORT_TICKET_PRIORITY_UPDATED",
                "SUPPORT_TICKET",
                ticket.getId(),
                "Admin updated support ticket #" + ticket.getId() + " priority to " + ticket.getTicketPriority().name() + ".",
                null);
        return communicationMapper.toSupportTicketResponse(ticket);
    }

    private SupportTicketDetailResponse buildTicketDetail(SupportTicket ticket) {
        return new SupportTicketDetailResponse(
                communicationMapper.toSupportTicketResponse(ticket),
                supportTicketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId()).stream()
                        .map(communicationMapper::toSupportTicketMessageResponse)
                        .toList());
    }

    private void ensureTicketOpenForRequester(SupportTicket ticket) {
        if (ticket.getTicketStatus() == SupportTicketStatus.CLOSED) {
            throw new ResponseStatusException(BAD_REQUEST, "Closed tickets cannot receive requester replies unless reopened by an admin.");
        }
    }

    private void saveMessage(SupportTicket ticket, User sender, String messageText) {
        SupportTicketMessage message = new SupportTicketMessage();
        message.setTicket(ticket);
        message.setSenderUser(sender);
        message.setMessage(messageText);
        supportTicketMessageRepository.save(message);
        ticket.setUpdatedAt(LocalDateTime.now());
        supportTicketRepository.save(ticket);
    }

    private void notifyAdmins(
            NotificationType type,
            String title,
            String message,
            String targetPath,
            User actor,
            Long ticketId) {
        for (User admin : userRepository.findAllByRoleName(RoleName.ADMIN)) {
            notificationService.safeCreateNotification(admin, type, title, message, "SUPPORT_TICKET", ticketId, targetPath, actor);
        }
    }

    private void notifyRequester(SupportTicket ticket, String title, String message, User actor) {
        if (ticket.getCreatedByUser() == null) {
            return;
        }
        notificationService.safeCreateNotification(
                ticket.getCreatedByUser(),
                NotificationType.SUPPORT_TICKET,
                title,
                message,
                "SUPPORT_TICKET",
                ticket.getId(),
                "/support-tickets/" + ticket.getId(),
                actor);
        emailService.sendSupportTicketReply(ticket.getCreatedByUser(), ticket.getSubject(), message);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User could not be found."));
    }

    private SupportTicketStatus parseStatus(String rawStatus) {
        try {
            return SupportTicketStatus.valueOf(rawStatus.trim().toUpperCase());
        } catch (Exception exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid support ticket status.");
        }
    }

    private SupportTicketPriority parsePriority(String rawPriority) {
        try {
            return SupportTicketPriority.valueOf(rawPriority.trim().toUpperCase());
        } catch (Exception exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid support ticket priority.");
        }
    }
}
