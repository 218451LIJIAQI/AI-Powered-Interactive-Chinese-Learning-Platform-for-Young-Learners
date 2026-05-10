package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.PaymentEntitlementRepository;
import com.example.aichinese.repository.PaymentRecordRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;
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
                .andExpect(jsonPath("$.data.paymentStatus").value("REFUNDED"));

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
