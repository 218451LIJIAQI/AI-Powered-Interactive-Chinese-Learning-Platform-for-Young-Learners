package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.aichinese.entity.Cart;
import com.example.aichinese.repository.CartItemRepository;
import com.example.aichinese.repository.CartRepository;
import com.example.aichinese.service.CartService;
import com.example.aichinese.service.CommerceMoneyService;
import com.example.aichinese.service.CommerceSettingsService;
import com.example.aichinese.service.EntitlementService;
import com.example.aichinese.service.PurchasableItemService;
import com.example.aichinese.service.StudentLearningAccessService;

/**
 * Unit coverage for {@code CartService}'s idempotency edges —
 * notably that {@code clearCartIfExists} is a no-op when no cart
 * row exists, so an early-pay flow can call it unconditionally
 * without 404-ing.
 */
class CartServiceTests {

    @Test
    void clearCartIfExistsDoesNotFailWhenCartIsMissing() {
        CartRepository cartRepository = mock(CartRepository.class);
        CartItemRepository cartItemRepository = mock(CartItemRepository.class);
        when(cartRepository.findByStudentUserId(99L)).thenReturn(Optional.empty());

        CartService cartService = service(cartRepository, cartItemRepository);

        assertThatCode(() -> cartService.clearCartIfExists(99L)).doesNotThrowAnyException();
        verify(cartRepository).findByStudentUserId(99L);
        verifyNoInteractions(cartItemRepository);
    }

    @Test
    void clearCartIfExistsDeletesItemsWhenCartExists() {
        CartRepository cartRepository = mock(CartRepository.class);
        CartItemRepository cartItemRepository = mock(CartItemRepository.class);
        Cart cart = new Cart();
        cart.setId(123L);
        when(cartRepository.findByStudentUserId(99L)).thenReturn(Optional.of(cart));

        CartService cartService = service(cartRepository, cartItemRepository);

        cartService.clearCartIfExists(99L);

        verify(cartRepository).findByStudentUserId(99L);
        verify(cartItemRepository).deleteByCartId(123L);
    }

    private CartService service(CartRepository cartRepository, CartItemRepository cartItemRepository) {
        return new CartService(
                mock(StudentLearningAccessService.class),
                cartRepository,
                cartItemRepository,
                mock(PurchasableItemService.class),
                mock(EntitlementService.class),
                mock(CommerceMoneyService.class),
                mock(CommerceSettingsService.class));
    }
}
