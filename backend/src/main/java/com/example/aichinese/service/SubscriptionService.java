package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.commerce.StudentSubscriptionResponse;
import com.example.aichinese.dto.commerce.SubscriptionPlanRequest;
import com.example.aichinese.dto.commerce.SubscriptionPlanResponse;
import com.example.aichinese.entity.PaymentRecord;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.StudentSubscription;
import com.example.aichinese.entity.SubscriptionPlan;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.PaymentStatus;
import com.example.aichinese.repository.StudentSubscriptionRepository;
import com.example.aichinese.repository.SubscriptionPlanRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;

@Service
public class SubscriptionService {

    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final StudentSubscriptionRepository studentSubscriptionRepository;
    private final StudentLearningAccessService studentLearningAccessService;
    private final CommerceMoneyService commerceMoneyService;
    private final CommerceSettingsService commerceSettingsService;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;

    public SubscriptionService(
            SubscriptionPlanRepository subscriptionPlanRepository,
            StudentSubscriptionRepository studentSubscriptionRepository,
            StudentLearningAccessService studentLearningAccessService,
            CommerceMoneyService commerceMoneyService,
            CommerceSettingsService commerceSettingsService,
            AuditLogService auditLogService,
            UserRepository userRepository) {
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.studentSubscriptionRepository = studentSubscriptionRepository;
        this.studentLearningAccessService = studentLearningAccessService;
        this.commerceMoneyService = commerceMoneyService;
        this.commerceSettingsService = commerceSettingsService;
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<SubscriptionPlanResponse> getActivePlans() {
        return subscriptionPlanRepository.findByActiveTrueOrderByPriceAsc().stream()
                .map(this::mapPlan)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SubscriptionPlanResponse> getAllPlans() {
        return subscriptionPlanRepository.findAll().stream()
                .map(this::mapPlan)
                .toList();
    }

    @Transactional(readOnly = true)
    public StudentSubscriptionResponse getCurrentStudentSubscription(SecurityUserDetails currentUser) {
        studentLearningAccessService.requireStudent(currentUser);
        return studentSubscriptionRepository
                .findFirstByStudentUserIdAndSubscriptionStatusOrderByCreatedAtDesc(currentUser.getUserId(), PaymentStatus.PAID)
                .map(this::mapStudentSubscription)
                .orElse(null);
    }

    @Transactional
    public SubscriptionPlanResponse createPlan(SecurityUserDetails currentUser, SubscriptionPlanRequest request) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setName(request.name().trim());
        plan.setPrice(commerceMoneyService.normalize(request.price()));
        plan.setCurrencyCode(commerceSettingsService.getDefaultCurrency());
        plan.setBenefits(request.benefits().trim());
        plan.setActive(request.active() == null || request.active());
        SubscriptionPlan saved = subscriptionPlanRepository.save(plan);
        // Audit-log every privileged subscription-plan mutation. Subscription
        // plans drive what students pay and what they receive, so an
        // unattributed price/benefits change would be a real financial-trust
        // gap. The previous version of this method had no audit row at all.
        auditLogService.safeRecord(
                resolveActor(currentUser),
                "ADMIN_SUBSCRIPTION_PLAN_CREATE",
                "SubscriptionPlan",
                saved.getId(),
                "Admin created subscription plan \"" + saved.getName() + "\".",
                null);
        return mapPlan(saved);
    }

    @Transactional
    public SubscriptionPlanResponse updatePlan(SecurityUserDetails currentUser, Long id, SubscriptionPlanRequest request) {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Subscription plan was not found."));
        plan.setName(request.name().trim());
        plan.setPrice(commerceMoneyService.normalize(request.price()));
        plan.setBenefits(request.benefits().trim());
        if (request.active() != null) {
            plan.setActive(request.active());
        }
        SubscriptionPlan saved = subscriptionPlanRepository.save(plan);
        auditLogService.safeRecord(
                resolveActor(currentUser),
                "ADMIN_SUBSCRIPTION_PLAN_UPDATE",
                "SubscriptionPlan",
                saved.getId(),
                "Admin updated subscription plan \"" + saved.getName() + "\".",
                null);
        return mapPlan(saved);
    }

    @Transactional
    public SubscriptionPlanResponse setPlanActive(SecurityUserDetails currentUser, Long id, boolean active) {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Subscription plan was not found."));
        // No-op short-circuit (mirrors the SupportTicketService.updateStatus
        // / AdminTeacherManagementService.approveTeacher pattern from earlier
        // waves): if the plan is already in the target state, skip the save
        // and skip the audit-log entry rather than writing a redundant
        // "activated" row that confuses the admin trail.
        if (plan.isActive() == active) {
            return mapPlan(plan);
        }
        plan.setActive(active);
        SubscriptionPlan saved = subscriptionPlanRepository.save(plan);
        auditLogService.safeRecord(
                resolveActor(currentUser),
                active ? "ADMIN_SUBSCRIPTION_PLAN_ACTIVATE" : "ADMIN_SUBSCRIPTION_PLAN_DEACTIVATE",
                "SubscriptionPlan",
                saved.getId(),
                "Admin " + (active ? "activated" : "deactivated") + " subscription plan \"" + saved.getName() + "\".",
                null);
        return mapPlan(saved);
    }

    /**
     * Resolves the {@link User} entity behind the JWT {@link SecurityUserDetails}
     * so that audit rows record the admin acting on the plan. Returns {@code
     * null} if the lookup fails — {@code auditLogService.safeRecord} accepts a
     * null actor and falls back to "system" attribution rather than aborting
     * the privileged operation when the audit write itself runs into trouble.
     */
    private User resolveActor(SecurityUserDetails currentUser) {
        if (currentUser == null) {
            return null;
        }
        return userRepository.findById(currentUser.getUserId()).orElse(null);
    }

    @Transactional
    public StudentSubscription createPendingSubscription(StudentProfile student, Long planId, PaymentRecord paymentRecord) {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Subscription plan was not found."));
        StudentSubscription subscription = new StudentSubscription();
        subscription.setStudent(student);
        subscription.setPlan(plan);
        subscription.setPaymentRecord(paymentRecord);
        subscription.setSubscriptionStatus(PaymentStatus.PENDING);
        subscription.setStartsAt(LocalDateTime.now());
        subscription.setEndsAt(LocalDateTime.now().plusDays(30));
        return studentSubscriptionRepository.save(subscription);
    }

    @Transactional
    public void activateSubscription(Long paymentId) {
        // Direct lookup by payment record id keeps activation O(matches) instead
        // of scanning every StudentSubscription in the database. The result list
        // is typically of size 0 or 1, but we still iterate to remain robust if
        // a future payment retry ever produces multiple linked rows.
        if (paymentId == null) {
            return;
        }
        for (StudentSubscription subscription : studentSubscriptionRepository.findByPaymentRecordId(paymentId)) {
            subscription.setSubscriptionStatus(PaymentStatus.PAID);
        }
    }

    private SubscriptionPlanResponse mapPlan(SubscriptionPlan plan) {
        return new SubscriptionPlanResponse(
                plan.getId(),
                plan.getName(),
                commerceMoneyService.normalize(plan.getPrice()),
                plan.getCurrencyCode(),
                plan.getBenefits(),
                plan.isActive());
    }

    private StudentSubscriptionResponse mapStudentSubscription(StudentSubscription subscription) {
        return new StudentSubscriptionResponse(
                subscription.getId(),
                mapPlan(subscription.getPlan()),
                subscription.getSubscriptionStatus(),
                subscription.getStartsAt(),
                subscription.getEndsAt(),
                subscription.getPaymentRecord() != null ? subscription.getPaymentRecord().getId() : null);
    }
}
