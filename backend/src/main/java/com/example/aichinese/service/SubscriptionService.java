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
import com.example.aichinese.entity.enums.PaymentStatus;
import com.example.aichinese.repository.StudentSubscriptionRepository;
import com.example.aichinese.repository.SubscriptionPlanRepository;
import com.example.aichinese.security.SecurityUserDetails;

@Service
public class SubscriptionService {

    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final StudentSubscriptionRepository studentSubscriptionRepository;
    private final StudentLearningAccessService studentLearningAccessService;
    private final CommerceMoneyService commerceMoneyService;
    private final CommerceSettingsService commerceSettingsService;

    public SubscriptionService(
            SubscriptionPlanRepository subscriptionPlanRepository,
            StudentSubscriptionRepository studentSubscriptionRepository,
            StudentLearningAccessService studentLearningAccessService,
            CommerceMoneyService commerceMoneyService,
            CommerceSettingsService commerceSettingsService) {
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.studentSubscriptionRepository = studentSubscriptionRepository;
        this.studentLearningAccessService = studentLearningAccessService;
        this.commerceMoneyService = commerceMoneyService;
        this.commerceSettingsService = commerceSettingsService;
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
    public SubscriptionPlanResponse createPlan(SubscriptionPlanRequest request) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setName(request.name().trim());
        plan.setPrice(commerceMoneyService.normalize(request.price()));
        plan.setCurrencyCode(commerceSettingsService.getDefaultCurrency());
        plan.setBenefits(request.benefits().trim());
        plan.setActive(request.active() == null || request.active());
        return mapPlan(subscriptionPlanRepository.save(plan));
    }

    @Transactional
    public SubscriptionPlanResponse updatePlan(Long id, SubscriptionPlanRequest request) {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Subscription plan was not found."));
        plan.setName(request.name().trim());
        plan.setPrice(commerceMoneyService.normalize(request.price()));
        plan.setBenefits(request.benefits().trim());
        if (request.active() != null) {
            plan.setActive(request.active());
        }
        return mapPlan(subscriptionPlanRepository.save(plan));
    }

    @Transactional
    public SubscriptionPlanResponse setPlanActive(Long id, boolean active) {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Subscription plan was not found."));
        plan.setActive(active);
        return mapPlan(subscriptionPlanRepository.save(plan));
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
