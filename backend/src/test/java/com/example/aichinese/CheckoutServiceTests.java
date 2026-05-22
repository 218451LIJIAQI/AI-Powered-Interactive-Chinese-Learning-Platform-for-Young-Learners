package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.aichinese.config.PaymentProperties;
import com.example.aichinese.dto.commerce.CheckoutResponse;
import com.example.aichinese.dto.commerce.CheckoutSessionItemRequest;
import com.example.aichinese.entity.Order;
import com.example.aichinese.entity.OrderItem;
import com.example.aichinese.entity.PaymentRecord;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.PaymentStatus;
import com.example.aichinese.entity.enums.PurchasableItemType;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.CartService;
import com.example.aichinese.service.CheckoutService;
import com.example.aichinese.service.EntitlementService;
import com.example.aichinese.service.OrderService;
import com.example.aichinese.service.PaymentService;
import com.example.aichinese.service.PurchasableItemService;
import com.example.aichinese.service.StudentLearningAccessService;
import com.example.aichinese.service.model.ResolvedPurchasableItem;

class CheckoutServiceTests {

    @Test
    void freeBuyNowUnlocksWithoutStartingProviderCheckout() {
        Fixture fixture = fixture();
        SecurityUserDetails currentUser = currentUser();
        StudentProfile student = student();
        ResolvedPurchasableItem freeItem = new ResolvedPurchasableItem(
                PurchasableItemType.LESSON,
                10L,
                "Free Lesson",
                BigDecimal.ZERO,
                "MYR",
                null,
                true,
                null);
        Order order = order(student);
        OrderItem orderItem = orderItem(order);
        PaymentRecord paymentRecord = paymentRecord(order, student);

        when(fixture.studentLearningAccessService.requireStudent(currentUser)).thenReturn(student);
        when(fixture.purchasableItemService.resolve("LESSON", 10L, null)).thenReturn(freeItem);
        when(fixture.entitlementService.hasAccessToPurchasableItem(student, freeItem)).thenReturn(false);
        when(fixture.orderService.createOrder(student, com.example.aichinese.dto.commerce.CheckoutSessionRequest.CheckoutSource.BUY_NOW, List.of(freeItem)))
                .thenReturn(order);
        when(fixture.orderService.createOrderItems(order, List.of(freeItem))).thenReturn(List.of(orderItem));
        when(fixture.paymentService.createPendingPayment(order, student)).thenReturn(paymentRecord);
        doAnswer(invocation -> {
            PaymentRecord record = invocation.getArgument(0);
            record.setPaymentStatus(PaymentStatus.PAID);
            record.setPaymentMethod(invocation.getArgument(1));
            record.setProviderStatus(invocation.getArgument(2));
            return null;
        }).when(fixture.paymentService).markPaymentPaid(paymentRecord, "FREE", "FREE_UNLOCK");

        CheckoutResponse response = fixture.checkoutService.buyNow(
                currentUser,
                new CheckoutSessionItemRequest("LESSON", 10L, null));

        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(response.checkoutUrl()).isNull();
        assertThat(response.providerStatus()).isEqualTo("FREE_UNLOCK");
        assertThat(response.paymentAvailable()).isTrue();
        assertThat(response.items()).hasSize(1);
        verify(fixture.paymentService).markPaymentPaid(paymentRecord, "FREE", "FREE_UNLOCK");
        verify(fixture.paymentService, never()).startCheckout(
                any(SecurityUserDetails.class),
                any(StudentProfile.class),
                any(Order.class),
                any(PaymentRecord.class),
                anyList(),
                anyString(),
                anyString());
    }

    private Fixture fixture() {
        StudentLearningAccessService studentLearningAccessService = mock(StudentLearningAccessService.class);
        CartService cartService = mock(CartService.class);
        PurchasableItemService purchasableItemService = mock(PurchasableItemService.class);
        EntitlementService entitlementService = mock(EntitlementService.class);
        OrderService orderService = mock(OrderService.class);
        PaymentService paymentService = mock(PaymentService.class);
        CheckoutService checkoutService = new CheckoutService(
                studentLearningAccessService,
                cartService,
                purchasableItemService,
                entitlementService,
                orderService,
                paymentService,
                new PaymentProperties(
                        "http://localhost:5173/payment/success",
                        "http://localhost:5173/payment/cancel",
                        false,
                        "no-op",
                        "https://api.stripe.com",
                        "",
                        ""));
        return new Fixture(
                checkoutService,
                studentLearningAccessService,
                purchasableItemService,
                entitlementService,
                orderService,
                paymentService);
    }

    private SecurityUserDetails currentUser() {
        User user = new User();
        user.setId(99L);
        user.setEmail("student@example.com");
        user.setDisplayName("Student Example");
        user.setPasswordHash("password-hash");
        user.setActive(true);
        return new SecurityUserDetails(user);
    }

    private StudentProfile student() {
        User user = new User();
        user.setId(99L);
        user.setEmail("student@example.com");
        user.setDisplayName("Student Example");

        StudentProfile student = new StudentProfile();
        student.setId(199L);
        student.setUser(user);
        return student;
    }

    private Order order(StudentProfile student) {
        Order order = new Order();
        order.setId(500L);
        order.setStudent(student);
        order.setOrderNumber("ORD-FREE");
        order.setTotalAmount(BigDecimal.ZERO);
        order.setCurrencyCode("MYR");
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setCheckoutSource("BUY_NOW");
        return order;
    }

    private OrderItem orderItem(Order order) {
        OrderItem item = new OrderItem();
        item.setId(700L);
        item.setOrder(order);
        item.setItemType("LESSON");
        item.setItemId(10L);
        item.setItemTitleSnapshot("Free Lesson");
        item.setQuantity(1);
        item.setUnitPrice(BigDecimal.ZERO);
        item.setLineTotal(BigDecimal.ZERO);
        return item;
    }

    private PaymentRecord paymentRecord(Order order, StudentProfile student) {
        PaymentRecord paymentRecord = new PaymentRecord();
        paymentRecord.setId(600L);
        paymentRecord.setOrder(order);
        paymentRecord.setStudent(student);
        paymentRecord.setProviderName("no-op");
        paymentRecord.setAmount(BigDecimal.ZERO);
        paymentRecord.setCurrencyCode("MYR");
        paymentRecord.setPaymentStatus(PaymentStatus.PENDING);
        return paymentRecord;
    }

    private record Fixture(
            CheckoutService checkoutService,
            StudentLearningAccessService studentLearningAccessService,
            PurchasableItemService purchasableItemService,
            EntitlementService entitlementService,
            OrderService orderService,
            PaymentService paymentService) {
    }
}
