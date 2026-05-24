package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.commerce.CartItemRequest;
import com.example.aichinese.dto.commerce.CartItemResponse;
import com.example.aichinese.dto.commerce.CartResponse;
import com.example.aichinese.entity.Cart;
import com.example.aichinese.entity.CartItem;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.repository.CartItemRepository;
import com.example.aichinese.repository.CartRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.model.ResolvedPurchasableItem;

/**
 * Manages a student's shopping cart: add / remove items, recompute
 * prices, and persist line-items.
 *
 * <h2>Item identity</h2>
 * <p>Each cart line is identified by an
 * {@code (itemType, itemId)} pair resolved through
 * {@link PurchasableItemService}, which is the single source of truth
 * for what is sellable. Already-owned items are rejected at add time
 * via {@link EntitlementService} so a student cannot accidentally
 * pay twice for the same content.</p>
 *
 * <h2>Pricing</h2>
 * <p>Per-line and cart totals are computed via
 * {@link CommerceMoneyService} so currency precision and rounding
 * are uniform with the order / payment paths. The unit price is
 * captured at add-time, not look-up time, so a price change between
 * "add to cart" and "checkout" does not silently re-bill the student.</p>
 */
@Service
public class CartService {

    private final StudentLearningAccessService studentLearningAccessService;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final PurchasableItemService purchasableItemService;
    private final EntitlementService entitlementService;
    private final CommerceMoneyService commerceMoneyService;
    private final CommerceSettingsService commerceSettingsService;

    public CartService(
            StudentLearningAccessService studentLearningAccessService,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            PurchasableItemService purchasableItemService,
            EntitlementService entitlementService,
            CommerceMoneyService commerceMoneyService,
            CommerceSettingsService commerceSettingsService) {
        this.studentLearningAccessService = studentLearningAccessService;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.purchasableItemService = purchasableItemService;
        this.entitlementService = entitlementService;
        this.commerceMoneyService = commerceMoneyService;
        this.commerceSettingsService = commerceSettingsService;
    }

    @Transactional
    public CartResponse getCart(SecurityUserDetails currentUser) {
        StudentProfile student = studentLearningAccessService.requireLockedStudent(currentUser);
        Cart cart = cartRepository.findByStudentUserId(currentUser.getUserId())
                .orElseGet(() -> createCart(student));
        return buildCartResponse(cart, cartItemRepository.findByCartIdOrderByCreatedAtAsc(cart.getId()));
    }

    @Transactional
    public CartResponse addItem(SecurityUserDetails currentUser, CartItemRequest request) {
        StudentProfile student = studentLearningAccessService.requireLockedStudent(currentUser);
        Cart cart = cartRepository.findLockedByStudentUserId(currentUser.getUserId())
                .orElseGet(() -> createCart(student));

        ResolvedPurchasableItem item = purchasableItemService.resolve(request.itemType(), request.itemId(), request.featureKey());
        if (item.freeItem()) {
            throw new ResponseStatusException(BAD_REQUEST, "Free items do not need to be added to the paid cart.");
        }
        if (entitlementService.hasAccessToPurchasableItem(student, item)) {
            throw new ResponseStatusException(BAD_REQUEST, "You already have access to this item.");
        }

        cartItemRepository.findByCartIdAndItemTypeAndItemId(cart.getId(), item.itemType().name(), item.itemId())
                .ifPresent(existing -> {
                    throw new ResponseStatusException(BAD_REQUEST, "This item is already in your cart.");
                });

        CartItem cartItem = new CartItem();
        cartItem.setCart(cart);
        cartItem.setItemType(item.itemType().name());
        cartItem.setItemId(item.itemId());
        cartItem.setQuantity(1);
        cartItem.setUnitPrice(item.unitPrice());
        try {
            cartItemRepository.save(cartItem);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "This item is already in your cart.", exception);
        }

        return buildCartResponse(cart, cartItemRepository.findByCartIdOrderByCreatedAtAsc(cart.getId()));
    }

    @Transactional
    public CartResponse removeItem(SecurityUserDetails currentUser, Long cartItemId) {
        CartItem cartItem = cartItemRepository.findByIdAndCartStudentUserId(cartItemId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Cart item was not found."));
        Cart cart = cartItem.getCart();
        cartItemRepository.delete(cartItem);
        return buildCartResponse(cart, cartItemRepository.findByCartIdOrderByCreatedAtAsc(cart.getId()));
    }

    @Transactional
    public CartResponse clearCart(SecurityUserDetails currentUser) {
        Cart cart = cartRepository.findByStudentUserId(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Cart was not found."));
        cartItemRepository.deleteByCartId(cart.getId());
        return buildCartResponse(cart, List.of());
    }

    @Transactional
    public void clearCartIfExists(Long studentUserId) {
        cartRepository.findByStudentUserId(studentUserId)
                .ifPresent(cart -> cartItemRepository.deleteByCartId(cart.getId()));
    }

    @Transactional(readOnly = true)
    public List<CartItem> getCartItems(SecurityUserDetails currentUser) {
        Cart cart = cartRepository.findByStudentUserId(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Cart was not found."));
        return cartItemRepository.findByCartIdOrderByCreatedAtAsc(cart.getId());
    }

    private Cart createCart(StudentProfile student) {
        Cart cart = new Cart();
        cart.setStudent(student);
        cart.setActive(true);
        return cartRepository.save(cart);
    }

    private CartResponse buildCartResponse(Cart cart, List<CartItem> cartItems) {
        List<CartItemResponse> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        String currencyCode = commerceSettingsService.getDefaultCurrency();

        for (CartItem cartItem : cartItems) {
            ResolvedPurchasableItem resolved = purchasableItemService.resolve(cartItem.getItemType(), cartItem.getItemId(), null);
            BigDecimal subtotal = commerceMoneyService.multiply(resolved.unitPrice(), BigDecimal.valueOf(cartItem.getQuantity()));
            totalAmount = totalAmount.add(subtotal);
            items.add(new CartItemResponse(
                    cartItem.getId(),
                    resolved.itemType().name(),
                    resolved.itemId(),
                    resolved.title(),
                    resolved.unitPrice(),
                    currencyCode,
                    cartItem.getQuantity(),
                    subtotal,
                    resolved.teacher() != null));
        }

        return new CartResponse(
                cart.getId(),
                items,
                commerceMoneyService.normalize(totalAmount),
                currencyCode,
                items.isEmpty());
    }
}
