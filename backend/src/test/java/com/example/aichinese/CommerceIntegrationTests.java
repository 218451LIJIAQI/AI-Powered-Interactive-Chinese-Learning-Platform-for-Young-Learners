package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.aichinese.entity.PaymentRecord;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.NotificationRepository;
import com.example.aichinese.repository.PaymentEntitlementRepository;
import com.example.aichinese.repository.PaymentRecordRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.service.PaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "DEFAULT_ADMIN_EMAIL=admin@example.com",
        "DEFAULT_ADMIN_PASSWORD=SuperSecure123!",
        "app.payment.provider=stripe",
        "app.payment.enable-real-provider=false",
        "app.payment.mock-success-enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommerceIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeacherProfileRepository teacherProfileRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private PaymentRecordRepository paymentRecordRepository;

    @Autowired
    private PaymentEntitlementRepository paymentEntitlementRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PaymentService paymentService;

    @Test
    void grantAccessAfterPaymentIsIdempotent() throws Exception {
        // Regression test for the EntitlementService idempotency guard. The
        // PaymentService.verifyPaymentStatus path has a small race window
        // where two concurrent calls can both observe PENDING and both call
        // markPaymentPaid before either has committed; before the guard was
        // added this produced duplicate PaymentEntitlement rows for the same
        // (order, item) tuple. The fix lets us call markPaymentPaid more
        // than once on the same payment without double-granting access.
        String teacherEmail = uniqueEmail("idem-t");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        Long lessonId = createPaidLesson(teacherToken, "Idempotency Lesson");
        submitApproveAndPublishLesson(lessonId, teacherToken, adminToken);

        String studentEmail = uniqueEmail("idem-s");
        String studentToken = registerStudentAndLogin(studentEmail);

        mockMvc.perform(post("/api/v1/students/cart/items")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "itemType", "LESSON",
                        "itemId", lessonId))))
                .andExpect(status().isOk());

        MvcResult checkoutResult = mockMvc.perform(post("/api/v1/students/checkout/cart")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();

        Long paymentId = objectMapper.readTree(checkoutResult.getResponse().getContentAsString())
                .path("data").path("paymentId").asLong();

        long entitlementsAfterFirstPayment = paymentEntitlementRepository.findAll().stream()
                .filter(entitlement -> entitlement.getPaymentRecord() != null
                        && entitlement.getPaymentRecord().getId().equals(paymentId)
                        && "LESSON".equals(entitlement.getItemType())
                        && lessonId.equals(entitlement.getItemId()))
                .count();
        assertThat(entitlementsAfterFirstPayment).isEqualTo(1L);

        // Replay markPaymentPaid against the same payment record (simulating
        // the concurrent verifyPaymentStatus race) and confirm the
        // entitlement count does not grow.
        PaymentRecord paymentRecord = paymentRecordRepository.findById(paymentId).orElseThrow();
        paymentService.markPaymentPaid(paymentRecord, "REPLAY", "REPLAY");

        long entitlementsAfterReplay = paymentEntitlementRepository.findAll().stream()
                .filter(entitlement -> entitlement.getPaymentRecord() != null
                        && entitlement.getPaymentRecord().getId().equals(paymentId)
                        && "LESSON".equals(entitlement.getItemType())
                        && lessonId.equals(entitlement.getItemId()))
                .count();
        assertThat(entitlementsAfterReplay)
                .as("markPaymentPaid replay must not insert a duplicate entitlement row")
                .isEqualTo(1L);

        // Wave-5 regression: the replay must also not emit a duplicate
        // "Payment successful" notification. Before the markPaymentPaid
        // idempotency short-circuit was added, two concurrent
        // verifyPaymentStatus calls (e.g. the student opened two browser
        // tabs and refreshed payment status on each) would each fire a
        // notification, spamming the recipient.
        Long studentUserId = paymentRecord.getStudent().getUser().getId();
        long paymentSuccessNotifications = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(studentUserId)
                .stream()
                .filter(notification -> "Payment successful".equals(notification.getTitle()))
                .count();
        assertThat(paymentSuccessNotifications)
                .as("markPaymentPaid replay must not emit a duplicate Payment-successful notification")
                .isEqualTo(1L);
    }

    @Test
    void studentCanAddRemoveAndClearCart() throws Exception {
        String teacherEmail = uniqueEmail("commerce-cart-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        Long lessonId = createPaidLesson(teacherToken, "Paid Cart Lesson");
        submitApproveAndPublishLesson(lessonId, teacherToken, adminToken);

        String studentToken = registerStudentAndLogin(uniqueEmail("commerce-cart-student"));

        mockMvc.perform(post("/api/v1/students/cart/items")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "itemType", "LESSON",
                        "itemId", lessonId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].title").value("Paid Cart Lesson"));

        MvcResult cartResult = mockMvc.perform(get("/api/v1/students/cart")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();

        Long cartItemId = objectMapper.readTree(cartResult.getResponse().getContentAsString())
                .path("data").path("items").get(0).path("cartItemId").asLong();

        mockMvc.perform(delete("/api/v1/students/cart/items/" + cartItemId)
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.empty").value(true));

        mockMvc.perform(post("/api/v1/students/cart/items")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "itemType", "LESSON",
                        "itemId", lessonId))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/students/cart/clear")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.empty").value(true));
    }

    @Test
    void studentCanFetchAnEmptyCartBeforeAddingAnyItems() throws Exception {
        String studentToken = registerStudentAndLogin(uniqueEmail("commerce-empty-cart-student"));

        mockMvc.perform(get("/api/v1/students/cart")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.empty").value(true));
    }

    @Test
    void cartCheckoutCreatesPaymentAndUnlocksAccessInMockMode() throws Exception {
        String teacherEmail = uniqueEmail("commerce-checkout-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        Long lessonId = createPaidLesson(teacherToken, "Mock Paid Lesson");
        submitApproveAndPublishLesson(lessonId, teacherToken, adminToken);

        String studentEmail = uniqueEmail("commerce-checkout-student");
        String studentToken = registerStudentAndLogin(studentEmail);

        mockMvc.perform(post("/api/v1/students/cart/items")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "itemType", "LESSON",
                        "itemId", lessonId))))
                .andExpect(status().isOk());

        MvcResult checkoutResult = mockMvc.perform(post("/api/v1/students/checkout/cart")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"))
                .andReturn();

        Long paymentId = objectMapper.readTree(checkoutResult.getResponse().getContentAsString())
                .path("data").path("paymentId").asLong();

        mockMvc.perform(get("/api/v1/payments/status/" + paymentId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"));

        User studentUser = userRepository.findByEmailWithRoles(studentEmail).orElseThrow();
        Long studentId = studentProfileRepository.findByUserId(studentUser.getId()).orElseThrow().getId();

        assertThat(paymentRecordRepository.findById(paymentId)).isPresent();
        assertThat(paymentEntitlementRepository.existsByStudentIdAndItemTypeAndItemIdAndActiveTrue(studentId, "LESSON", lessonId))
                .isTrue();

        mockMvc.perform(get("/api/v1/students/lessons/" + lessonId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locked").value(false));
    }

    @Test
    void checkoutRequiresStudentRoleAndAdminCommerceRoutesStayProtected() throws Exception {
        String teacherEmail = uniqueEmail("commerce-roles-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        String studentToken = registerStudentAndLogin(uniqueEmail("commerce-roles-student"));

        mockMvc.perform(post("/api/v1/students/checkout/cart")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/refund-requests")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/teachers/earnings")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void guestCannotAccessPaymentStatusAndOtherStudentCannotViewIt() throws Exception {
        String teacherEmail = uniqueEmail("pay-owner-t");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        Long lessonId = createPaidLesson(teacherToken, "Ownership Check Lesson");
        submitApproveAndPublishLesson(lessonId, teacherToken, adminToken);

        String ownerStudentToken = registerStudentAndLogin(uniqueEmail("pay-owner-s"));
        MvcResult paymentResult = mockMvc.perform(post("/api/v1/students/checkout/buy-now")
                .with(csrf())
                .header("Authorization", "Bearer " + ownerStudentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "itemType", "LESSON",
                        "itemId", lessonId))))
                .andExpect(status().isOk())
                .andReturn();

        Long paymentId = objectMapper.readTree(paymentResult.getResponse().getContentAsString())
                .path("data").path("paymentId").asLong();

        mockMvc.perform(get("/api/v1/payments/status/" + paymentId))
                .andExpect(status().isUnauthorized());

        String otherStudentToken = registerStudentAndLogin(uniqueEmail("pay-other-s"));
        mockMvc.perform(get("/api/v1/payments/status/" + paymentId)
                .header("Authorization", "Bearer " + otherStudentToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void studentCanCreateRefundRequestAndAdminCanManageIt() throws Exception {
        String teacherEmail = uniqueEmail("commerce-refund-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        Long lessonId = createPaidLesson(teacherToken, "Refundable Lesson");
        submitApproveAndPublishLesson(lessonId, teacherToken, adminToken);

        String studentToken = registerStudentAndLogin(uniqueEmail("commerce-refund-student"));

        MvcResult buyNowResult = mockMvc.perform(post("/api/v1/students/checkout/buy-now")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "itemType", "LESSON",
                        "itemId", lessonId))))
                .andExpect(status().isOk())
                .andReturn();

        Long orderId = objectMapper.readTree(buyNowResult.getResponse().getContentAsString())
                .path("data").path("orderId").asLong();

        MvcResult refundResult = mockMvc.perform(post("/api/v1/students/refund-requests")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "orderId", orderId,
                        "reason", "Purchased by mistake"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundStatus").value("PENDING"))
                .andReturn();

        Long refundId = objectMapper.readTree(refundResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/admin/refund-requests/" + refundId + "/approve")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundStatus").value("APPROVED"));

        mockMvc.perform(post("/api/v1/admin/refund-requests/" + refundId + "/mark-refunded")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundStatus").value("REFUNDED"));

        // Regression test for RefundService.requireTransition: once the
        // refund has reached REFUNDED, clicking Approve again must fail
        // with 400 instead of silently rewinding the state machine and
        // emitting a duplicate "Refund approved" notification + audit row.
        mockMvc.perform(post("/api/v1/admin/refund-requests/" + refundId + "/approve")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // Double-clicking mark-refunded on a refund that is already REFUNDED
        // must also fail — otherwise the admin could trigger
        // revokeAccessAfterRefund twice against the same order.
        mockMvc.perform(post("/api/v1/admin/refund-requests/" + refundId + "/mark-refunded")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void teacherCanRequestPayoutAndAdminCanApproveRejectOrMarkPaid() throws Exception {
        String teacherEmail = uniqueEmail("commerce-payout-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        Long lessonId = createPaidLesson(teacherToken, "Teacher Earning Lesson");
        submitApproveAndPublishLesson(lessonId, teacherToken, adminToken);

        String studentToken = registerStudentAndLogin(uniqueEmail("commerce-payout-student"));

        mockMvc.perform(post("/api/v1/students/checkout/buy-now")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "itemType", "LESSON",
                        "itemId", lessonId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"));

        MvcResult payoutResult = mockMvc.perform(post("/api/v1/teachers/payout-requests")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "amount", 10.00,
                        "requestedNote", "Monthly payout"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payoutStatus").value("PENDING"))
                .andReturn();

        Long payoutId = objectMapper.readTree(payoutResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/admin/payout-requests/" + payoutId + "/approve")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payoutStatus").value("APPROVED"));

        mockMvc.perform(post("/api/v1/admin/payout-requests/" + payoutId + "/mark-paid")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payoutStatus").value("PAID"));

        // Regression test for PayoutService.requireTransition: mark-paid on a
        // PAID payout must fail, so a double-click doesn't emit a duplicate
        // notification or audit-log row.
        mockMvc.perform(post("/api/v1/admin/payout-requests/" + payoutId + "/mark-paid")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void adminCanManageSubscriptionPlansAndCommissionSetting() throws Exception {
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        String studentToken = registerStudentAndLogin(uniqueEmail("commerce-admin-student"));

        MvcResult planResult = mockMvc.perform(post("/api/v1/admin/subscription-plans")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "name", "Starter Plan " + UUID.randomUUID(),
                        "price", 9.90,
                        "benefits", "Starter access",
                        "active", true))))
                .andExpect(status().isOk())
                .andReturn();

        Long planId = objectMapper.readTree(planResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(patch("/api/v1/admin/subscription-plans/" + planId + "/deactivate")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        // Wave-10 audit-log invariant: privileged subscription-plan
        // mutations must each leave a corresponding row in the audit log.
        // The Wave-9 SubscriptionService refactor added these audit calls;
        // this assertion locks them in so a future regression that drops
        // the audit-log call will fail loudly rather than silently
        // weakening the financial admin trail.
        assertThat(auditLogRepository.existsByActionTypeAndTargetTypeAndTargetId(
                "ADMIN_SUBSCRIPTION_PLAN_CREATE", "SubscriptionPlan", planId))
                .as("create-plan must produce an ADMIN_SUBSCRIPTION_PLAN_CREATE audit row")
                .isTrue();
        assertThat(auditLogRepository.existsByActionTypeAndTargetTypeAndTargetId(
                "ADMIN_SUBSCRIPTION_PLAN_DEACTIVATE", "SubscriptionPlan", planId))
                .as("deactivate-plan must produce an ADMIN_SUBSCRIPTION_PLAN_DEACTIVATE audit row")
                .isTrue();

        // Wave-10 idempotency assertion: deactivating an already-deactivated
        // plan must NOT add a second DEACTIVATE row to the audit log. The
        // SubscriptionService.setPlanActive no-op short-circuit (W9.2)
        // returns early when the requested state matches the current one.
        long deactivateRowsAfterFirstClick = auditLogRepository.findAll().stream()
                .filter(row -> "ADMIN_SUBSCRIPTION_PLAN_DEACTIVATE".equals(row.getActionType())
                        && "SubscriptionPlan".equals(row.getTargetType())
                        && planId.equals(row.getTargetId()))
                .count();

        mockMvc.perform(patch("/api/v1/admin/subscription-plans/" + planId + "/deactivate")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        long deactivateRowsAfterSecondClick = auditLogRepository.findAll().stream()
                .filter(row -> "ADMIN_SUBSCRIPTION_PLAN_DEACTIVATE".equals(row.getActionType())
                        && "SubscriptionPlan".equals(row.getTargetType())
                        && planId.equals(row.getTargetId()))
                .count();
        assertThat(deactivateRowsAfterSecondClick)
                .as("repeat deactivate must not write a second DEACTIVATE audit row")
                .isEqualTo(deactivateRowsAfterFirstClick);

        mockMvc.perform(put("/api/v1/admin/system-settings/commission")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("commissionRate", 0.25))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commissionRate").value(0.25));

        mockMvc.perform(get("/api/v1/admin/system-settings/commission")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/system-settings/commission")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicSubscriptionPlansEndpointReturnsActivePlansWithoutAuthentication() throws Exception {
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        String planName = "Public Plan " + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/admin/subscription-plans")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "name", planName,
                        "price", 19.90,
                        "benefits", "Public preview benefits",
                        "active", true))))
                .andExpect(status().isOk());

        MvcResult publicResult = mockMvc.perform(get("/api/v1/subscription-plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        String body = publicResult.getResponse().getContentAsString();
        assertThat(body).contains(planName);
    }

    @Test
    void paidLessonShowsLockedBeforePaymentAndRefundedPaymentUpdatesStatuses() throws Exception {
        String teacherEmail = uniqueEmail("lock-refund-t");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        Long lessonId = createPaidLesson(teacherToken, "Lock Then Refund Lesson");
        submitApproveAndPublishLesson(lessonId, teacherToken, adminToken);

        String studentToken = registerStudentAndLogin(uniqueEmail("lock-refund-s"));

        mockMvc.perform(get("/api/v1/students/lessons/" + lessonId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        MvcResult checkoutResult = mockMvc.perform(post("/api/v1/students/checkout/buy-now")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "itemType", "LESSON",
                        "itemId", lessonId))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode checkoutJson = objectMapper.readTree(checkoutResult.getResponse().getContentAsString()).path("data");
        Long orderId = checkoutJson.path("orderId").asLong();
        Long paymentId = checkoutJson.path("paymentId").asLong();

        mockMvc.perform(get("/api/v1/students/lessons/" + lessonId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locked").value(false));

        MvcResult refundResult = mockMvc.perform(post("/api/v1/students/refund-requests")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "orderId", orderId,
                        "reason", "Need a refund"))))
                .andExpect(status().isOk())
                .andReturn();

        Long refundId = objectMapper.readTree(refundResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/admin/refund-requests/" + refundId + "/approve")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/refund-requests/" + refundId + "/mark-refunded")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundStatus").value("REFUNDED"));

        mockMvc.perform(get("/api/v1/students/payments/" + paymentId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("REFUNDED"))
                // Wave-7 regression: SensitiveResponseHeadersConfig must add
                // Cache-Control: no-store on the student payment-detail
                // endpoint so the JSON body (PII: provider reference, amount,
                // student email/displayName) cannot be replayed from disk
                // cache or bfcache after the user logs out.
                .andExpect(header().string("Cache-Control",
                        org.hamcrest.Matchers.containsString("no-store")));

        mockMvc.perform(get("/api/v1/students/lessons/" + lessonId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    private void approveTeacherProfile(String teacherEmail) throws Exception {
        TeacherProfile profile = registerTeacherWithVerificationDocument(teacherEmail);
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        mockMvc.perform(post("/api/v1/admin/teachers/" + profile.getId() + "/approve")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("note", "Approved for commerce"))))
                .andExpect(status().isOk());

        TeacherProfile refreshed = teacherProfileRepository.findById(profile.getId()).orElseThrow();
        assertThat(refreshed.getTeacherStatus()).isEqualTo(TeacherStatus.APPROVED);
    }

    private TeacherProfile registerTeacherWithVerificationDocument(String email) throws Exception {
        registerTeacher(email);
        String teacherToken = loginAndExtractToken(email, "Password123!");
        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file",
                "qualification.pdf",
                "application/pdf",
                "verification".getBytes());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/teachers/verification-documents")
                .file(file)
                .param("documentType", "QUALIFICATION")
                .param("description", "Qualification proof")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());

        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        return teacherProfileRepository.findDetailedByUserId(user.getId()).orElseThrow();
    }

    private void registerTeacher(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/teacher")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "fullName", "Teacher " + email,
                        "email", email,
                        "password", "Password123!",
                        "confirmPassword", "Password123!",
                        "bio", "Teacher bio"))))
                .andExpect(status().isOk());
    }

    private String registerStudentAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/student")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "fullName", "Student " + email,
                        "email", email,
                        "password", "Password123!",
                        "confirmPassword", "Password123!",
                        "preferredLanguage", "en"))))
                .andExpect(status().isOk());

        return loginAndExtractToken(email, "Password123!");
    }

    private Long createPaidLesson(String teacherToken, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teachers/lessons")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", title,
                        "description", "Paid lesson",
                        "levelId", 1,
                        "categoryId", 3,
                        "learningType", "RECORDED",
                        "accessType", "PAID",
                        "price", 29.90,
                        "estimatedDurationMinutes", 20,
                        "basicContent", "Paid practice content"))))
                .andExpect(status().isOk())
                .andReturn();
        return extractId(result);
    }

    private void submitApproveAndPublishLesson(Long lessonId, String teacherToken, String adminToken) throws Exception {
        mockMvc.perform(post("/api/v1/teachers/lessons/" + lessonId + "/submit")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/content/LESSON/" + lessonId + "/approve")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("note", "Approved"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/content/LESSON/" + lessonId + "/publish")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("note", "Published"))))
                .andExpect(status().isOk());
    }

    private Long extractId(MvcResult result) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.path("data").path("id").asLong();
    }

    private String loginAndExtractToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", email,
                        "password", password))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
