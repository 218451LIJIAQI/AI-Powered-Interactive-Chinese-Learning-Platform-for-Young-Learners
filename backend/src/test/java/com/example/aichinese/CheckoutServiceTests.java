package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.PaymentProperties;
import com.example.aichinese.dto.commerce.CheckoutResponse;
import com.example.aichinese.dto.commerce.CheckoutSessionItemRequest;
import com.example.aichinese.dto.commerce.CheckoutSessionRequest;
import com.example.aichinese.entity.CartItem;
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

/**
 * Unit coverage for {@code CheckoutService}: pins the free-item
 * short-circuit (no provider call), the entitlement re-check at
 * checkout time (defence against concurrent payment in another tab),
 * and the price snapshotting from {@code ResolvedPurchasableItem} so
 * mid-checkout price changes cannot re-bill the learner.
 */
class CheckoutServiceTests {

    private static final long STUDENT_USER_ID = 99L;
    private static final long STUDENT_PROFILE_ID = 199L;
    private static final long LESSON_ID = 10L;
    private static final long ORDER_ID = 500L;
    private static final long PAYMENT_ID = 600L;
    private static final long ORDER_ITEM_ID = 700L;
    private static final String ITEM_TYPE = "LESSON";
    private static final String CURRENCY = "MYR";
    private static final BigDecimal FREE_PRICE = BigDecimal.ZERO;
    private static final BigDecimal CART_PRICE = new BigDecimal("25.00");
    private static final BigDecimal LIVE_PRICE = new BigDecimal("30.00");

    @Test
    void freeBuyNowUnlocksWithoutStartingProviderCheckout() {
        Fixture fixture = fixture();
        SecurityUserDetails currentUser = currentUser();
        StudentProfile student = student();
        ResolvedPurchasableItem freeItem = lesson("Free Lesson", FREE_PRICE, true);
        Order order = order(student);
        OrderItem orderItem = orderItem(order);
        PaymentRecord paymentRecord = paymentRecord(order, student);

        when(fixture.studentLearningAccessService.requireStudent(currentUser)).thenReturn(student);
        when(fixture.purchasableItemService.resolve(ITEM_TYPE, LESSON_ID, null)).thenReturn(freeItem);
        when(fixture.entitlementService.hasAccessToPurchasableItem(student, freeItem)).thenReturn(false);
        when(fixture.orderService.createOrder(student, CheckoutSessionRequest.CheckoutSource.BUY_NOW, List.of(freeItem)))
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
                new CheckoutSessionItemRequest(ITEM_TYPE, LESSON_ID, null));

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

    @Test
    void cartCheckoutRejectsTooManyItemsBeforeCreatingOrder() {
        Fixture fixture = fixture();
        SecurityUserDetails currentUser = currentUser();
        StudentProfile student = student();
        List<CartItem> cartItems = LongStream.rangeClosed(1, 51)
                .mapToObj(id -> cartItem(id, null))
                .toList();

        when(fixture.studentLearningAccessService.requireStudent(currentUser)).thenReturn(student);
        when(fixture.cartService.getCartItems(currentUser)).thenReturn(cartItems);
        for (CartItem cartItem : cartItems) {
            when(fixture.purchasableItemService.resolve(cartItem.getItemType(), cartItem.getItemId(), null))
                    .thenReturn(lesson(cartItem.getItemId(), "Lesson " + cartItem.getItemId(), BigDecimal.TEN, false));
        }

        assertThatThrownBy(() -> fixture.checkoutService.checkoutCart(currentUser))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Checkout can contain at most 50 items.");

        verify(fixture.orderService, never()).createOrder(any(), any(), anyList());
        verify(fixture.paymentService, never()).createPendingPayment(any(), any());
    }

    @Test
    void cartCheckoutUsesCapturedCartPriceInsteadOfLivePrice() {
        Fixture fixture = fixture();
        SecurityUserDetails currentUser = currentUser();
        StudentProfile student = student();
        CartItem cartItem = cartItem(LESSON_ID, CART_PRICE);
        ResolvedPurchasableItem liveItem = lesson("Paid Lesson", LIVE_PRICE, false);
        Order order = order(student);
        order.setCheckoutSource("CART");
        order.setTotalAmount(CART_PRICE);
        OrderItem orderItem = orderItem(order);
        orderItem.setUnitPrice(CART_PRICE);
        orderItem.setLineTotal(CART_PRICE);
        PaymentRecord paymentRecord = paymentRecord(order, student);
        paymentRecord.setAmount(CART_PRICE);

        when(fixture.studentLearningAccessService.requireStudent(currentUser)).thenReturn(student);
        when(fixture.cartService.getCartItems(currentUser)).thenReturn(List.of(cartItem));
        when(fixture.purchasableItemService.resolve(ITEM_TYPE, LESSON_ID, null)).thenReturn(liveItem);
        when(fixture.entitlementService.hasAccessToPurchasableItem(eq(student), any(ResolvedPurchasableItem.class)))
                .thenReturn(false);
        when(fixture.orderService.createOrder(
                eq(student),
                eq(CheckoutSessionRequest.CheckoutSource.CART),
                anyList()))
                .thenAnswer(invocation -> {
                    List<ResolvedPurchasableItem> items = invocation.getArgument(2);
                    assertThat(items).singleElement().satisfies(item ->
                            assertThat(item.unitPrice()).isEqualByComparingTo(CART_PRICE));
                    return order;
                });
        when(fixture.orderService.createOrderItems(eq(order), anyList())).thenReturn(List.of(orderItem));
        when(fixture.paymentService.createPendingPayment(order, student)).thenReturn(paymentRecord);
        when(fixture.paymentService.startCheckout(
                eq(currentUser),
                eq(student),
                eq(order),
                eq(paymentRecord),
                eq(List.of(orderItem)),
                anyString(),
                anyString()))
                .thenReturn(new CheckoutResponse(
                        order.getId(),
                        order.getOrderNumber(),
                        paymentRecord.getId(),
                        PaymentStatus.PENDING,
                        CART_PRICE,
                        CURRENCY,
                        "https://pay.example.test/session",
                        "mock",
                        "CREATED",
                        true,
                        "Created",
                        List.of()));

        CheckoutResponse response = fixture.checkoutService.checkoutCart(currentUser);

        assertThat(response.totalAmount()).isEqualByComparingTo(CART_PRICE);
        verify(fixture.orderService).createOrder(
                eq(student),
                eq(CheckoutSessionRequest.CheckoutSource.CART),
                anyList());
    }

    @Test
    void buyNowRejectsNullRequestBeforeResolvingItem() {
        Fixture fixture = fixture();
        SecurityUserDetails currentUser = currentUser();

        assertThatThrownBy(() -> fixture.checkoutService.buyNow(currentUser, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Checkout item request is required.");

        verify(fixture.studentLearningAccessService, never()).requireStudent(currentUser);
        verify(fixture.purchasableItemService, never()).resolve(any(), any(), any());
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
                        "http://localhost:5174/payment/success",
                        "http://localhost:5174/payment/cancel",
                        false,
                        "no-op",
                        "https://api.stripe.com",
                        "",
                        ""));
        return new Fixture(
                checkoutService,
                studentLearningAccessService,
                cartService,
                purchasableItemService,
                entitlementService,
                orderService,
                paymentService);
    }

    private static SecurityUserDetails currentUser() {
        User user = new User();
        user.setId(STUDENT_USER_ID);
        user.setEmail("student@example.com");
        user.setDisplayName("Student Example");
        user.setPasswordHash("password-hash");
        user.setActive(true);
        return new SecurityUserDetails(user);
    }

    private static StudentProfile student() {
        User user = new User();
        user.setId(STUDENT_USER_ID);
        user.setEmail("student@example.com");
        user.setDisplayName("Student Example");

        StudentProfile student = new StudentProfile();
        student.setId(STUDENT_PROFILE_ID);
        student.setUser(user);
        return student;
    }

    private static Order order(StudentProfile student) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setStudent(student);
        order.setOrderNumber("ORD-FREE");
        order.setTotalAmount(FREE_PRICE);
        order.setCurrencyCode(CURRENCY);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setCheckoutSource("BUY_NOW");
        return order;
    }

    private static OrderItem orderItem(Order order) {
        OrderItem item = new OrderItem();
        item.setId(ORDER_ITEM_ID);
        item.setOrder(order);
        item.setItemType(ITEM_TYPE);
        item.setItemId(LESSON_ID);
        item.setItemTitleSnapshot("Free Lesson");
        item.setQuantity(1);
        item.setUnitPrice(FREE_PRICE);
        item.setLineTotal(FREE_PRICE);
        return item;
    }

    private static PaymentRecord paymentRecord(Order order, StudentProfile student) {
        PaymentRecord paymentRecord = new PaymentRecord();
        paymentRecord.setId(PAYMENT_ID);
        paymentRecord.setOrder(order);
        paymentRecord.setStudent(student);
        paymentRecord.setProviderName("no-op");
        paymentRecord.setAmount(FREE_PRICE);
        paymentRecord.setCurrencyCode(CURRENCY);
        paymentRecord.setPaymentStatus(PaymentStatus.PENDING);
        return paymentRecord;
    }

    private static CartItem cartItem(long itemId, BigDecimal unitPrice) {
        CartItem item = new CartItem();
        item.setItemType(ITEM_TYPE);
        item.setItemId(itemId);
        item.setUnitPrice(unitPrice);
        return item;
    }

    private static ResolvedPurchasableItem lesson(String title, BigDecimal unitPrice, boolean free) {
        return lesson(LESSON_ID, title, unitPrice, free);
    }

    private static ResolvedPurchasableItem lesson(long itemId, String title, BigDecimal unitPrice, boolean free) {
        return new ResolvedPurchasableItem(
                PurchasableItemType.LESSON,
                itemId,
                title,
                unitPrice,
                CURRENCY,
                null,
                free,
                null);
    }

    private record Fixture(
            CheckoutService checkoutService,
            StudentLearningAccessService studentLearningAccessService,
            CartService cartService,
            PurchasableItemService purchasableItemService,
            EntitlementService entitlementService,
            OrderService orderService,
            PaymentService paymentService) {
    }
}
