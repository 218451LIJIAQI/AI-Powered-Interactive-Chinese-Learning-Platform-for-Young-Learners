package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.aichinese.support.TestUploadFixtures;
import com.example.aichinese.entity.Order;
import com.example.aichinese.entity.OrderItem;
import com.example.aichinese.entity.PaymentRecord;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.PaymentStatus;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.NotificationRepository;
import com.example.aichinese.repository.OrderItemRepository;
import com.example.aichinese.repository.OrderRepository;
import com.example.aichinese.repository.PaymentEntitlementRepository;
import com.example.aichinese.repository.PaymentRecordRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.service.PaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Comprehensive end-to-end MockMvc coverage of the commerce stack:
 * cart, checkout, payment provider redirect, webhook settlement,
 * entitlement creation, refund workflow, payout workflow, teacher
 * earnings ledger, and platform commission rate.
 *
 * <p>The longest integration test in the project; pins the financial
 * invariants the platform reconciles against:</p>
 * <ul>
 *   <li>Repurchase rejection via {@code EntitlementService}.</li>
 *   <li>Currency precision through {@code CommerceMoneyService}
 *       ({@code HALF_UP} / scale 2).</li>
 *   <li>Webhook idempotency under retry storms.</li>
 *   <li>Stripe webhook HMAC verification with constant-time compare.</li>
 *   <li>Refund approval revokes the underlying entitlement.</li>
 *   <li>Teacher earnings move PENDING → AVAILABLE → RESERVED → PAID
 *       in lock-step with the order / payout state machine.</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "DEFAULT_ADMIN_EMAIL=admin@example.com",
        "DEFAULT_ADMIN_PASSWORD=SuperSecure123!",
        "app.payment.provider=mock",
        "app.payment.mock-success-enabled=true",
        "app.payment.stripe-webhook-secret=whsec_integration_test"
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
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private NotificationRepository notificationRepository;

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
        Long lessonId = createPaidLesson(teacherToken, "Idempotency Lesson");

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
        Long lessonId = createPaidLesson(teacherToken, "Paid Cart Lesson");

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
    void premiumAiCannotBePurchasedUntilFeatureAccessIsImplemented() throws Exception {
        String studentToken = registerStudentAndLogin(uniqueEmail("premium-ai-student"));

        mockMvc.perform(post("/api/v1/students/cart/items")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "itemType", "PREMIUM_AI",
                        "itemId", 0,
                        "featureKey", "PREMIUM_AI_FEATURE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/v1/students/checkout/buy-now")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "itemType", "PREMIUM_AI",
                        "itemId", 0,
                        "featureKey", "PREMIUM_AI_FEATURE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void ownedPaidLessonCannotBePurchasedAgain() throws Exception {
        String teacherEmail = uniqueEmail("duplicate-lesson-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        Long lessonId = createPaidLesson(teacherToken, "Duplicate Purchase Lesson");

        String studentToken = registerStudentAndLogin(uniqueEmail("duplicate-lesson-student"));

        mockMvc.perform(post("/api/v1/students/checkout/buy-now")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "itemType", "LESSON",
                        "itemId", lessonId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"));

        mockMvc.perform(post("/api/v1/students/checkout/buy-now")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "itemType", "LESSON",
                        "itemId", lessonId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/v1/students/cart/items")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "itemType", "LESSON",
                        "itemId", lessonId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void packageContainingOwnedLessonCannotBePurchasedAgain() throws Exception {
        String teacherEmail = uniqueEmail("duplicate-package-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        Long moduleId = createModule(teacherToken, "Duplicate Package Module");
        Long lessonId = createPaidLessonInModule(teacherToken, moduleId, "Duplicate Package Lesson");
        Long packageId = createPaidPackage(teacherToken, "Duplicate Package Bundle");
        addPackageItem(teacherToken, packageId, "MODULE", moduleId);

        String studentToken = registerStudentAndLogin(uniqueEmail("duplicate-package-student"));

        mockMvc.perform(post("/api/v1/students/checkout/buy-now")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "itemType", "LESSON",
                        "itemId", lessonId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"));

        mockMvc.perform(post("/api/v1/students/checkout/buy-now")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "itemType", "PACKAGE",
                        "itemId", packageId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/v1/students/cart/items")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "itemType", "PACKAGE",
                        "itemId", packageId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void cartCheckoutCreatesPaymentAndUnlocksAccessInMockMode() throws Exception {
        String teacherEmail = uniqueEmail("commerce-checkout-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        Long lessonId = createPaidLesson(teacherToken, "Mock Paid Lesson");

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
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.data.studentId").doesNotExist())
                .andExpect(jsonPath("$.data.studentName").doesNotExist())
                .andExpect(jsonPath("$.data.studentEmail").doesNotExist());

        User studentUser = userRepository.findByEmailWithRoles(studentEmail).orElseThrow();
        Long studentId = studentProfileRepository.findByUserId(studentUser.getId()).orElseThrow().getId();

        assertThat(paymentRecordRepository.findById(paymentId)).isPresent();
        assertThat(paymentEntitlementRepository.existsByStudentIdAndItemTypeAndItemIdAndActiveTrue(studentId, "LESSON", lessonId))
                .isTrue();

        mockMvc.perform(get("/api/v1/students/lessons/" + lessonId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locked").value(false));

        mockMvc.perform(get("/api/v1/students/cart")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.empty").value(true));
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
    void commerceHistoryListsClampOversizedPaginationRequests() throws Exception {
        String teacherEmail = uniqueEmail("commerce-page-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        Long lessonId = createPaidLesson(teacherToken, "Paginated Commerce Lesson");

        String studentToken = registerStudentAndLogin(uniqueEmail("commerce-page-student"));
        MvcResult checkoutResult = mockMvc.perform(post("/api/v1/students/checkout/buy-now")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "itemType", "LESSON",
                        "itemId", lessonId))))
                .andExpect(status().isOk())
                .andReturn();

        Long orderId = objectMapper.readTree(checkoutResult.getResponse().getContentAsString())
                .path("data").path("orderId").asLong();

        mockMvc.perform(post("/api/v1/students/refund-requests")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "orderId", orderId,
                        "reason", "Pagination test refund"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/teachers/payout-requests")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "amount", 10.00,
                        "requestedNote", "Pagination test payout"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/students/payments")
                .param("size", "100000")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.items.length()").value(1));

        mockMvc.perform(get("/api/v1/students/refund-requests")
                .param("size", "100000")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].studentId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].studentName").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].studentEmail").doesNotExist());

        mockMvc.perform(get("/api/v1/teachers/earnings")
                .param("size", "100000")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.items.length()").value(1));

        mockMvc.perform(get("/api/v1/teachers/payout-requests")
                .param("size", "100000")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].teacherId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].teacherName").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].teacherEmail").doesNotExist());

        mockMvc.perform(get("/api/v1/admin/payments")
                .param("size", "100000")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.items.length()").isNumber());

        mockMvc.perform(get("/api/v1/admin/refund-requests")
                .param("size", "100000")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.items.length()").isNumber())
                .andExpect(jsonPath("$.data.items[0].studentId").exists())
                .andExpect(jsonPath("$.data.items[0].studentName").isString())
                .andExpect(jsonPath("$.data.items[0].studentEmail").isString());

        mockMvc.perform(get("/api/v1/admin/payout-requests")
                .param("size", "100000")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.items.length()").isNumber())
                .andExpect(jsonPath("$.data.items[0].teacherId").exists())
                .andExpect(jsonPath("$.data.items[0].teacherName").isString())
                .andExpect(jsonPath("$.data.items[0].teacherEmail").isString());
    }

    @Test
    void guestCannotAccessPaymentStatusAndOtherStudentCannotViewIt() throws Exception {
        String teacherEmail = uniqueEmail("pay-owner-t");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        Long lessonId = createPaidLesson(teacherToken, "Ownership Check Lesson");

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
    void signedStripeWebhookMarksMatchingPendingPaymentPaidAndIsReplaySafe() throws Exception {
        String teacherEmail = uniqueEmail("stripe-webhook-t");
        approveTeacherProfile(teacherEmail);
        User teacherUser = userRepository.findByEmailWithRoles(teacherEmail).orElseThrow();
        TeacherProfile teacherProfile = teacherProfileRepository.findDetailedByUserId(teacherUser.getId()).orElseThrow();
        String studentEmail = uniqueEmail("stripe-webhook-s");
        registerStudentAndLogin(studentEmail);
        User studentUser = userRepository.findByEmailWithRoles(studentEmail).orElseThrow();
        var student = studentProfileRepository.findByUserId(studentUser.getId()).orElseThrow();
        String sessionId = "cs_test_" + UUID.randomUUID().toString().replace("-", "");

        Order order = new Order();
        order.setStudent(student);
        order.setOrderNumber("ORD-WEBHOOK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        order.setTotalAmount(new BigDecimal("29.90"));
        order.setCurrencyCode("MYR");
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setCheckoutSource("BUY_NOW");
        order = orderRepository.save(order);

        OrderItem orderItem = new OrderItem();
        orderItem.setOrder(order);
        orderItem.setItemType("LESSON");
        orderItem.setItemId(900_000L + System.nanoTime() % 10_000);
        orderItem.setItemTitleSnapshot("Stripe Webhook Lesson");
        orderItem.setQuantity(1);
        orderItem.setUnitPrice(new BigDecimal("29.90"));
        orderItem.setLineTotal(new BigDecimal("29.90"));
        orderItem.setTeacher(teacherProfile);
        orderItem = orderItemRepository.save(orderItem);

        PaymentRecord paymentRecord = new PaymentRecord();
        paymentRecord.setOrder(order);
        paymentRecord.setStudent(student);
        paymentRecord.setProviderName("stripe");
        paymentRecord.setProviderReference(sessionId);
        paymentRecord.setAmount(new BigDecimal("29.90"));
        paymentRecord.setCurrencyCode("MYR");
        paymentRecord.setPaymentStatus(PaymentStatus.PENDING);
        paymentRecord.setProviderStatus("CREATED");
        paymentRecord = paymentRecordRepository.save(paymentRecord);

        String payload = """
                {"id":"evt_webhook_paid","type":"checkout.session.completed","data":{"object":{"id":"%s","object":"checkout.session","status":"complete","payment_status":"paid"}}}
                """.formatted(sessionId).trim();

        mockMvc.perform(post("/api/v1/payments/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", stripeSignature(payload))
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processed").value(true))
                .andExpect(jsonPath("$.data.paymentId").value(paymentRecord.getId()))
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"));

        PaymentRecord paidPayment = paymentRecordRepository.findById(paymentRecord.getId()).orElseThrow();
        assertThat(paidPayment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paidPayment.getPaymentMethod()).isEqualTo("STRIPE");
        assertThat(paidPayment.getPaidAt()).isNotNull();
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paymentEntitlementRepository.existsByStudentIdAndItemTypeAndItemIdAndActiveTrue(
                student.getId(),
                "LESSON",
                orderItem.getItemId()))
                .isTrue();

        mockMvc.perform(post("/api/v1/payments/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", stripeSignature(payload))
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processed").value(true))
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"));

        Long paymentRecordId = paymentRecord.getId();
        Long orderItemId = orderItem.getItemId();
        long entitlementCountAfterReplay = paymentEntitlementRepository.findAll().stream()
                .filter(entitlement -> entitlement.getPaymentRecord() != null
                        && entitlement.getPaymentRecord().getId().equals(paymentRecordId)
                        && entitlement.getItemId().equals(orderItemId))
                .count();
        assertThat(entitlementCountAfterReplay)
                .as("Stripe webhook replay must not duplicate entitlements for an already-paid payment")
                .isEqualTo(1L);
    }

    @Test
    void stripeWebhookRejectsUnsupportedContentTypeAndOversizedPayloadAtControllerBoundary() throws Exception {
        String payload = """
                {"id":"evt_boundary","type":"charge.succeeded","data":{"object":{"id":"ch_123"}}}
                """.trim();

        mockMvc.perform(post("/api/v1/payments/webhooks/stripe")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Stripe-Signature", stripeSignature(payload))
                .content(payload))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/v1/payments/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", stripeSignature(payload))
                .content("x".repeat(262_145)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void studentPaymentEndpointsHideStudentIdentityFieldsWhileAdminEndpointsRetainThem() throws Exception {
        String teacherEmail = uniqueEmail("pay-privacy-t");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        Long lessonId = createPaidLesson(teacherToken, "Privacy Boundary Lesson");

        String studentEmail = uniqueEmail("pay-privacy-s");
        String studentToken = registerStudentAndLogin(studentEmail);

        MvcResult paymentResult = mockMvc.perform(post("/api/v1/students/checkout/buy-now")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "itemType", "LESSON",
                        "itemId", lessonId))))
                .andExpect(status().isOk())
                .andReturn();

        Long paymentId = objectMapper.readTree(paymentResult.getResponse().getContentAsString())
                .path("data").path("paymentId").asLong();

        User studentUser = userRepository.findByEmailWithRoles(studentEmail).orElseThrow();
        Long studentId = studentProfileRepository.findByUserId(studentUser.getId()).orElseThrow().getId();

        MvcResult studentPaymentsResult = mockMvc.perform(get("/api/v1/students/payments")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode studentPaymentItem = findPaymentItem(studentPaymentsResult, paymentId);
        assertThat(studentPaymentItem.has("studentId")).isFalse();
        assertThat(studentPaymentItem.has("studentName")).isFalse();
        assertThat(studentPaymentItem.has("studentEmail")).isFalse();

        mockMvc.perform(get("/api/v1/students/payments/" + paymentId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentId").value(paymentId))
                .andExpect(jsonPath("$.data.studentId").doesNotExist())
                .andExpect(jsonPath("$.data.studentName").doesNotExist())
                .andExpect(jsonPath("$.data.studentEmail").doesNotExist());

        mockMvc.perform(get("/api/v1/payments/status/" + paymentId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentId").value(paymentId))
                .andExpect(jsonPath("$.data.studentId").doesNotExist())
                .andExpect(jsonPath("$.data.studentName").doesNotExist())
                .andExpect(jsonPath("$.data.studentEmail").doesNotExist());

        mockMvc.perform(get("/api/v1/admin/payments/" + paymentId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentId").value(paymentId))
                .andExpect(jsonPath("$.data.studentId").value(studentId))
                .andExpect(jsonPath("$.data.studentName").value(studentUser.getDisplayName()))
                .andExpect(jsonPath("$.data.studentEmail").value(studentEmail));
    }

    @Test
    void studentCanCreateRefundRequestAndAdminCanManageIt() throws Exception {
        String teacherEmail = uniqueEmail("commerce-refund-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        Long lessonId = createPaidLesson(teacherToken, "Refundable Lesson");

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
                .andExpect(jsonPath("$.data.studentId").doesNotExist())
                .andExpect(jsonPath("$.data.studentName").doesNotExist())
                .andExpect(jsonPath("$.data.studentEmail").doesNotExist())
                .andExpect(jsonPath("$.data.refundStatus").value("PENDING"))
                .andReturn();

        Long refundId = objectMapper.readTree(refundResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/admin/refund-requests/" + refundId + "/approve")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentId").exists())
                .andExpect(jsonPath("$.data.studentName").isString())
                .andExpect(jsonPath("$.data.studentEmail").isString())
                .andExpect(jsonPath("$.data.refundStatus").value("APPROVED"));

        mockMvc.perform(post("/api/v1/admin/refund-requests/" + refundId + "/mark-refunded")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentId").exists())
                .andExpect(jsonPath("$.data.studentName").isString())
                .andExpect(jsonPath("$.data.studentEmail").isString())
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
    void payoutAmountShouldRejectValuesOutsideDatabaseDecimalPrecision() throws Exception {
        String teacherEmail = uniqueEmail("payout-boundary-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");

        mockMvc.perform(post("/api/v1/teachers/payout-requests")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "amount", 100000000.00,
                        "requestedNote", "Should fail before persistence"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void teacherCanRequestPayoutAndAdminCanApproveRejectOrMarkPaid() throws Exception {
        String teacherEmail = uniqueEmail("commerce-payout-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        Long lessonId = createPaidLesson(teacherToken, "Teacher Earning Lesson");

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
                .andExpect(jsonPath("$.data.teacherId").doesNotExist())
                .andExpect(jsonPath("$.data.teacherName").doesNotExist())
                .andExpect(jsonPath("$.data.teacherEmail").doesNotExist())
                .andExpect(jsonPath("$.data.payoutStatus").value("PENDING"))
                .andReturn();

        Long payoutId = objectMapper.readTree(payoutResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        User teacherUser = userRepository.findByEmailWithRoles(teacherEmail).orElseThrow();
        Long teacherId = teacherProfileRepository.findByUserId(teacherUser.getId()).orElseThrow().getId();

        mockMvc.perform(post("/api/v1/admin/payout-requests/" + payoutId + "/approve")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teacherId").value(teacherId))
                .andExpect(jsonPath("$.data.teacherName").value(teacherUser.getDisplayName()))
                .andExpect(jsonPath("$.data.teacherEmail").value(teacherEmail))
                .andExpect(jsonPath("$.data.payoutStatus").value("APPROVED"));

        mockMvc.perform(post("/api/v1/admin/payout-requests/" + payoutId + "/mark-paid")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teacherId").value(teacherId))
                .andExpect(jsonPath("$.data.teacherName").value(teacherUser.getDisplayName()))
                .andExpect(jsonPath("$.data.teacherEmail").value(teacherEmail))
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
    void refundedEarningsCannotBeApprovedForPayout() throws Exception {
        String teacherEmail = uniqueEmail("refund-payout-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        Long lessonId = createPaidLesson(teacherToken, "Refunded Payout Lesson");

        String studentToken = registerStudentAndLogin(uniqueEmail("refund-payout-student"));
        MvcResult checkoutResult = mockMvc.perform(post("/api/v1/students/checkout/buy-now")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "itemType", "LESSON",
                        "itemId", lessonId))))
                .andExpect(status().isOk())
                .andReturn();

        Long orderId = objectMapper.readTree(checkoutResult.getResponse().getContentAsString())
                .path("data").path("orderId").asLong();

        MvcResult payoutResult = mockMvc.perform(post("/api/v1/teachers/payout-requests")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "amount", 10.00,
                        "requestedNote", "Refund-sensitive payout"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payoutStatus").value("PENDING"))
                .andReturn();

        Long payoutId = objectMapper.readTree(payoutResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        MvcResult refundResult = mockMvc.perform(post("/api/v1/students/refund-requests")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "orderId", orderId,
                        "reason", "Refund before payout approval"))))
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

        mockMvc.perform(post("/api/v1/admin/payout-requests/" + payoutId + "/approve")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void adminCanManageCommissionSetting() throws Exception {
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        String studentToken = registerStudentAndLogin(uniqueEmail("commerce-admin-student"));

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
    void adminCanManagePlatformSettings() throws Exception {
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        String studentToken = registerStudentAndLogin(uniqueEmail("platform-settings-student"));

        mockMvc.perform(put("/api/v1/admin/system-settings/platform")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "aiDisclaimerText", "Teacher guidance should still be reviewed.",
                        "emailNotifications", false,
                        "inAppNotifications", true,
                        "dailySummary", true,
                        "strictContentFiltering", false,
                        "sensitiveWordFilter", true,
                        "aiSafetyCheck", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiDisclaimerText").value("Teacher guidance should still be reviewed."))
                .andExpect(jsonPath("$.data.emailNotifications").value(false))
                .andExpect(jsonPath("$.data.inAppNotifications").value(true))
                .andExpect(jsonPath("$.data.dailySummary").value(true))
                .andExpect(jsonPath("$.data.strictContentFiltering").value(false))
                .andExpect(jsonPath("$.data.sensitiveWordFilter").value(true))
                .andExpect(jsonPath("$.data.aiSafetyCheck").value(false));

        mockMvc.perform(get("/api/v1/admin/system-settings/platform")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiDisclaimerText").value("Teacher guidance should still be reviewed."))
                .andExpect(jsonPath("$.data.dailySummary").value(true));

        mockMvc.perform(get("/api/v1/admin/system-settings/platform")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void paidLessonShowsLockedBeforePaymentAndRefundedPaymentUpdatesStatuses() throws Exception {
        String teacherEmail = uniqueEmail("lock-refund-t");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        Long lessonId = createPaidLesson(teacherToken, "Lock Then Refund Lesson");

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
                .andExpect(jsonPath("$.data.studentId").doesNotExist())
                .andExpect(jsonPath("$.data.studentName").doesNotExist())
                .andExpect(jsonPath("$.data.studentEmail").doesNotExist())
                // Wave-7 regression: SensitiveResponseHeadersConfig must add
                // Cache-Control: no-store on the student payment-detail
                // endpoint so the JSON body (amount, provider state, order
                // metadata) cannot be replayed from disk cache or bfcache
                // after the user logs out.
                .andExpect(header().string("Cache-Control",
                        containsString("no-store")));

        mockMvc.perform(get("/api/v1/students/lessons/" + lessonId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void refundedPaidLessonRevokesRecordedVideoProgressAndLiveClassAccess() throws Exception {
        String teacherEmail = uniqueEmail("refund-media-live-t");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        Long lessonId = createPaidLesson(teacherToken, "Refund Media Live Lesson");
        Long videoId = createUploadedRecordedVideo(teacherToken, lessonId, "Refunded Recording");
        Long liveClassId = createLiveClass(teacherToken, lessonId, "Refunded Live Class");
        String studentToken = registerStudentAndLogin(uniqueEmail("refund-media-live-s"));

        mockMvc.perform(get("/api/v1/students/lessons/" + lessonId + "/recorded-videos")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/students/live-classes/" + liveClassId + "/enrol")
                .with(csrf())
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
        Long orderId = objectMapper.readTree(checkoutResult.getResponse().getContentAsString())
                .path("data").path("orderId").asLong();

        mockMvc.perform(get("/api/v1/students/lessons/" + lessonId + "/recorded-videos")
                .param("size", "100000")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(videoId));
        mockMvc.perform(get("/api/v1/students/lessons/" + lessonId + "/recorded-videos/" + videoId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.streamUrl").isString());
        mockMvc.perform(get("/api/v1/students/recorded-videos/" + videoId + "/stream")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/students/video-progress/" + videoId)
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "watchedSeconds", 30,
                        "totalSeconds", 120,
                        "progressPercentage", 25,
                        "completed", false))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/students/live-classes/" + liveClassId + "/enrol")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enrolled").value(true));
        mockMvc.perform(get("/api/v1/students/live-classes/" + liveClassId + "/join")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meetingLink").isString());

        Long refundId = requestRefund(studentToken, orderId, "Refund media and live access");
        markRefunded(adminToken, refundId);

        mockMvc.perform(get("/api/v1/students/lessons/" + lessonId + "/recorded-videos")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/students/lessons/" + lessonId + "/recorded-videos/" + videoId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/students/recorded-videos/" + videoId + "/stream")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/students/video-progress/" + videoId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/students/video-progress/" + videoId + "/complete")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/students/live-classes/" + liveClassId + "/enrol")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/students/live-classes/" + liveClassId + "/join")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void refundedPaidQuizAttemptsDoNotExposeHistoricalAnswers() throws Exception {
        String teacherEmail = uniqueEmail("refund-quiz-t");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        Long lessonId = createPaidLesson(teacherToken, "Refund Quiz Lesson");
        Long quizId = createQuizWithQuestions(teacherToken, lessonId);

        String studentToken = registerStudentAndLogin(uniqueEmail("refund-quiz-s"));

        MvcResult checkoutResult = mockMvc.perform(post("/api/v1/students/checkout/buy-now")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "itemType", "LESSON",
                        "itemId", lessonId))))
                .andExpect(status().isOk())
                .andReturn();

        Long orderId = objectMapper.readTree(checkoutResult.getResponse().getContentAsString())
                .path("data").path("orderId").asLong();

        MvcResult loadQuizResult = mockMvc.perform(get("/api/v1/students/quizzes/" + quizId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode quizData = objectMapper.readTree(loadQuizResult.getResponse().getContentAsString()).path("data");
        Long questionId = quizData.path("questions").get(0).path("id").asLong();

        MvcResult submitResult = mockMvc.perform(post("/api/v1/students/quizzes/" + quizId + "/attempts")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "answers", List.of(Map.of("questionId", questionId, "answerText", "ma3"))))))
                .andExpect(status().isOk())
                .andReturn();

        Long attemptId = objectMapper.readTree(submitResult.getResponse().getContentAsString())
                .path("data").path("attemptId").asLong();

        mockMvc.perform(get("/api/v1/students/quiz-attempts/" + attemptId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answers[0].correctAnswer").value("ma1"));

        mockMvc.perform(get("/api/v1/students/wrong-answers")
                .param("size", "100000")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.items[0].correctAnswer").value("ma1"));

        MvcResult refundResult = mockMvc.perform(post("/api/v1/students/refund-requests")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "orderId", orderId,
                        "reason", "Refund quiz access"))))
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
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/students/quizzes/" + quizId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/students/quiz-attempts/" + attemptId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/students/quiz-attempts/" + attemptId + "/wrong-answers")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/students/wrong-answers")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void refundedPaidLessonHidesExistingFeedbackAndBlocksNewFeedback() throws Exception {
        String teacherEmail = uniqueEmail("refund-feedback-t");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        Long lessonId = createPaidLesson(teacherToken, "Refund Feedback Lesson");
        Long quizId = createQuizWithQuestions(teacherToken, lessonId);
        String studentEmail = uniqueEmail("refund-feedback-s");
        String studentToken = registerStudentAndLogin(studentEmail);
        Long studentId = findStudentProfile(studentEmail).getId();

        MvcResult checkoutResult = mockMvc.perform(post("/api/v1/students/checkout/buy-now")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "itemType", "LESSON",
                        "itemId", lessonId))))
                .andExpect(status().isOk())
                .andReturn();
        Long orderId = objectMapper.readTree(checkoutResult.getResponse().getContentAsString())
                .path("data").path("orderId").asLong();

        MvcResult loadQuizResult = mockMvc.perform(get("/api/v1/students/quizzes/" + quizId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();
        Long questionId = objectMapper.readTree(loadQuizResult.getResponse().getContentAsString())
                .path("data").path("questions").get(0).path("id").asLong();
        MvcResult submitResult = mockMvc.perform(post("/api/v1/students/quizzes/" + quizId + "/attempts")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "answers", List.of(Map.of("questionId", questionId, "answerText", "ma3"))))))
                .andExpect(status().isOk())
                .andReturn();
        Long attemptId = objectMapper.readTree(submitResult.getResponse().getContentAsString())
                .path("data").path("attemptId").asLong();

        MvcResult feedbackResult = mockMvc.perform(post("/api/v1/teachers/feedback")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "studentId", studentId,
                        "lessonId", lessonId,
                        "quizAttemptId", attemptId,
                        "feedbackType", "PROGRESS",
                        "message", "Review the paid feedback details while access is active."))))
                .andExpect(status().isOk())
                .andReturn();
        Long feedbackId = extractId(feedbackResult);

        mockMvc.perform(get("/api/v1/students/feedback/" + feedbackId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(feedbackId));

        Long refundId = requestRefund(studentToken, orderId, "Refund feedback access");
        markRefunded(adminToken, refundId);

        mockMvc.perform(get("/api/v1/students/feedback")
                .param("size", "100000")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
        mockMvc.perform(get("/api/v1/students/feedback/" + feedbackId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/teachers/feedback")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "studentId", studentId,
                        "lessonId", lessonId,
                        "quizAttemptId", attemptId,
                        "feedbackType", "PROGRESS",
                        "message", "Refunded students must not receive new paid feedback."))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/teachers/feedback")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "studentId", studentId,
                        "feedbackType", "GENERAL",
                        "message", "General feedback must also require current access."))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refundedPaidLessonRemovesPaidOnlyStudentFromTeacherRosterAndAnalytics() throws Exception {
        String teacherEmail = uniqueEmail("refund-roster-t");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        Long lessonId = createPaidLesson(teacherToken, "Refund Teacher Roster Lesson");
        Long quizId = createQuizWithQuestions(teacherToken, lessonId);
        Long videoId = createUploadedRecordedVideo(teacherToken, lessonId, "Refund Teacher Roster Video");
        Long liveClassId = createLiveClass(teacherToken, lessonId, "Refund Teacher Roster Live Class");
        String studentEmail = uniqueEmail("refund-roster-s");
        String studentToken = registerStudentAndLogin(studentEmail);
        Long studentId = findStudentProfile(studentEmail).getId();

        MvcResult checkoutResult = mockMvc.perform(post("/api/v1/students/checkout/buy-now")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "itemType", "LESSON",
                        "itemId", lessonId))))
                .andExpect(status().isOk())
                .andReturn();
        Long orderId = objectMapper.readTree(checkoutResult.getResponse().getContentAsString())
                .path("data").path("orderId").asLong();

        MvcResult loadQuizResult = mockMvc.perform(get("/api/v1/students/quizzes/" + quizId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();
        Long questionId = objectMapper.readTree(loadQuizResult.getResponse().getContentAsString())
                .path("data").path("questions").get(0).path("id").asLong();
        mockMvc.perform(post("/api/v1/students/quizzes/" + quizId + "/attempts")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "answers", List.of(Map.of("questionId", questionId, "answerText", "ma3"))))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/students/video-progress/" + videoId)
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "watchedSeconds", 120,
                        "totalSeconds", 120,
                        "progressPercentage", 100,
                        "completed", true))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/students/live-classes/" + liveClassId + "/enrol")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/teachers/students")
                .param("size", "100000")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].studentId").value(studentId))
                .andExpect(jsonPath("$.data.totalItems").value(1));
        mockMvc.perform(get("/api/v1/teachers/analytics")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overview.totalActiveStudents").value(1))
                .andExpect(jsonPath("$.data.quizScoreTrend.length()").value(1));
        mockMvc.perform(get("/api/v1/students/analytics")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overview.totalTrackedLessons").value(1))
                .andExpect(jsonPath("$.data.quizScoreTrend.length()").value(1))
                .andExpect(jsonPath("$.data.weakAreaBreakdown.length()").value(1))
                .andExpect(jsonPath("$.data.weakAreaBreakdown[0].label").value("CHOOSE_TONE"));
        mockMvc.perform(get("/api/v1/students/dashboard")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recentQuizResults[0].quizId").value(quizId))
                .andExpect(jsonPath("$.data.weakAreas.length()").value(1))
                .andExpect(jsonPath("$.data.weakAreas[0]").value("CHOOSE_TONE"));
        mockMvc.perform(get("/api/v1/students/progress")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalLessonsStarted").value(1))
                .andExpect(jsonPath("$.data.completedLessons").value(1))
                .andExpect(jsonPath("$.data.weakAreas.length()").value(1))
                .andExpect(jsonPath("$.data.weakAreas[0]").value("CHOOSE_TONE"))
                .andExpect(jsonPath("$.data.recentLessons[0].lessonId").value(lessonId));
        mockMvc.perform(get("/api/v1/teachers/dashboard")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalStudents").value(1))
                .andExpect(jsonPath("$.data.recentQuizResults.length()").value(1));
        mockMvc.perform(get("/api/v1/teachers/quizzes/" + quizId + "/attempts")
                .param("size", "100000")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].studentId").value(studentId));
        mockMvc.perform(get("/api/v1/teachers/lessons/" + lessonId + "/video-progress")
                .param("size", "100000")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].studentId").value(studentId));
        mockMvc.perform(get("/api/v1/teachers/live-classes/" + liveClassId + "/enrolments")
                .param("size", "100000")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].studentId").value(studentId));

        Long refundId = requestRefund(studentToken, orderId, "Refund teacher roster access");
        markRefunded(adminToken, refundId);

        mockMvc.perform(get("/api/v1/teachers/students")
                .param("size", "100000")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.totalItems").value(0));
        mockMvc.perform(get("/api/v1/teachers/analytics")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overview.totalActiveStudents").value(0))
                .andExpect(jsonPath("$.data.quizScoreTrend.length()").value(0));
        mockMvc.perform(get("/api/v1/students/analytics")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overview.totalTrackedLessons").value(0))
                .andExpect(jsonPath("$.data.overview.completedLessons").value(0))
                .andExpect(jsonPath("$.data.overview.averageQuizScore").value(0.0))
                .andExpect(jsonPath("$.data.quizScoreTrend.length()").value(0))
                .andExpect(jsonPath("$.data.weakAreaBreakdown.length()").value(0));
        mockMvc.perform(get("/api/v1/students/dashboard")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recentQuizResults.length()").value(0))
                .andExpect(jsonPath("$.data.weakAreas.length()").value(0))
                .andExpect(jsonPath("$.data.unfinishedLessons.length()").value(0));
        mockMvc.perform(get("/api/v1/students/progress")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalLessonsStarted").value(0))
                .andExpect(jsonPath("$.data.completedLessons").value(0))
                .andExpect(jsonPath("$.data.quizAverage").value(0))
                .andExpect(jsonPath("$.data.weakAreas.length()").value(0))
                .andExpect(jsonPath("$.data.recentLessons.length()").value(0));
        mockMvc.perform(get("/api/v1/teachers/dashboard")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalStudents").value(0))
                .andExpect(jsonPath("$.data.averageQuizScore").value(0.0))
                .andExpect(jsonPath("$.data.recentQuizResults.length()").value(0));
        mockMvc.perform(get("/api/v1/teachers/quizzes/" + quizId + "/attempts")
                .param("size", "100000")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.totalItems").value(0));
        mockMvc.perform(get("/api/v1/teachers/lessons/" + lessonId + "/video-progress")
                .param("size", "100000")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.totalItems").value(0));
        mockMvc.perform(get("/api/v1/teachers/live-classes/" + liveClassId + "/enrolments")
                .param("size", "100000")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.totalItems").value(0));
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
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "qualification.pdf",
                "application/pdf",
                TestUploadFixtures.minimalPdf());

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

    private com.example.aichinese.entity.StudentProfile findStudentProfile(String email) {
        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        return studentProfileRepository.findByUserId(user.getId()).orElseThrow();
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

    private Long createModule(String teacherToken, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teachers/modules")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", title,
                        "description", "Paid module",
                        "levelId", 1,
                        "categoryId", 3,
                        "accessType", "PAID",
                        "price", 49.90))))
                .andExpect(status().isOk())
                .andReturn();
        return extractId(result);
    }

    private Long createPaidLessonInModule(String teacherToken, Long moduleId, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teachers/lessons")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", title,
                        "description", "Paid lesson",
                        "moduleId", moduleId,
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

    private Long createPaidPackage(String teacherToken, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teachers/packages")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", title,
                        "description", "Paid package",
                        "levelId", 1,
                        "categoryId", 3,
                        "learningType", "RECORDED",
                        "accessType", "PAID",
                        "price", 59.90))))
                .andExpect(status().isOk())
                .andReturn();
        return extractId(result);
    }

    private void addPackageItem(String teacherToken, Long packageId, String itemType, Long itemId) throws Exception {
        mockMvc.perform(post("/api/v1/teachers/packages/" + packageId + "/items")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "itemType", itemType,
                        "itemId", itemId,
                        "displayOrder", 1))))
                .andExpect(status().isOk());
    }

    private Long createQuizWithQuestions(String teacherToken, Long lessonId) throws Exception {
        MvcResult quizResult = mockMvc.perform(post("/api/v1/teachers/lessons/" + lessonId + "/quizzes")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", "Paid tone quiz",
                        "description", "Checks paid lesson quiz access"))))
                .andExpect(status().isOk())
                .andReturn();

        Long quizId = extractId(quizResult);
        mockMvc.perform(post("/api/v1/teachers/quizzes/" + quizId + "/questions")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "questionType", "CHOOSE_TONE",
                        "questionText", "Which tone matches ma?",
                        "points", 5,
                        "displayOrder", 1,
                        "explanation", "ma1 is first tone.",
                        "options", List.of(
                                Map.of("optionText", "ma1", "correctOption", true, "displayOrder", 1),
                                Map.of("optionText", "ma3", "correctOption", false, "displayOrder", 2))))))
                .andExpect(status().isOk());

        return quizId;
    }

    private Long createUploadedRecordedVideo(String teacherToken, Long lessonId, String title) throws Exception {
        MockMultipartFile videoFile = new MockMultipartFile(
                "file",
                "lesson.mp4",
                "video/mp4",
                TestUploadFixtures.minimalMp4());
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/v1/teachers/lessons/" + lessonId + "/recorded-videos")
                .file(videoFile)
                .param("title", title)
                .param("description", "Recorded access refund coverage")
                .param("sourceType", "UPLOADED_FILE")
                .param("durationSeconds", "120")
                .param("sortOrder", "1")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andReturn();
        return extractId(result);
    }

    private Long createLiveClass(String teacherToken, Long lessonId, String title) throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(2);
        MvcResult result = mockMvc.perform(post("/api/v1/teachers/live-classes")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", title,
                        "description", "Live access refund coverage",
                        "lessonId", lessonId,
                        "startTime", start.toString(),
                        "endTime", start.plusMinutes(45).toString(),
                        "meetingLink", "https://meet.google.com/" + UUID.randomUUID(),
                        "meetingPlatform", "GOOGLE_MEET",
                        "status", "SCHEDULED"))))
                .andExpect(status().isOk())
                .andReturn();
        return extractId(result);
    }

    private Long requestRefund(String studentToken, Long orderId, String reason) throws Exception {
        MvcResult refundResult = mockMvc.perform(post("/api/v1/students/refund-requests")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "orderId", orderId,
                        "reason", reason))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(refundResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private void markRefunded(String adminToken, Long refundId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/refund-requests/" + refundId + "/approve")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/refund-requests/" + refundId + "/mark-refunded")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundStatus").value("REFUNDED"));
    }

    private Long extractId(MvcResult result) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.path("data").path("id").asLong();
    }

    private JsonNode findPaymentItem(MvcResult result, Long paymentId) throws Exception {
        JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("items");
        for (JsonNode item : items) {
            if (item.path("paymentId").asLong() == paymentId) {
                return item;
            }
        }
        throw new IllegalStateException("Payment " + paymentId + " was not found in paged response.");
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

    private String stripeSignature(String payload) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("whsec_integration_test".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of()
                .formatHex(mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
        return "t=" + timestamp + ",v1=" + signature;
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}

