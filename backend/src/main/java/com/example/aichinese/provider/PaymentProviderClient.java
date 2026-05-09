package com.example.aichinese.provider;

import com.example.aichinese.dto.commerce.PaymentProviderCheckoutResult;
import com.example.aichinese.dto.commerce.PaymentProviderStatusResult;
import com.example.aichinese.service.model.CommerceCheckout;

public interface PaymentProviderClient {

    String PAYMENT_UNAVAILABLE_MESSAGE = "Payment service is currently unavailable. Please try again later.";

    String providerName();

    PaymentProviderCheckoutResult createCheckoutSession(CommerceCheckout checkout);

    PaymentProviderStatusResult getPaymentStatus(String providerReference);
}
