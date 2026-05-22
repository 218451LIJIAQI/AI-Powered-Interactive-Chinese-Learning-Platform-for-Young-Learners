package com.example.aichinese.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;

@Service
public class CommerceMoneyService {

    public static final int MONEY_SCALE = 2;
    public static final int RATE_SCALE = 4;
    public static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    public BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING);
        }

        return value.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    public BigDecimal multiply(BigDecimal left, BigDecimal right) {
        return normalize(normalize(left).multiply(normalize(right)));
    }

    public BigDecimal subtract(BigDecimal left, BigDecimal right) {
        return normalize(normalize(left).subtract(normalize(right)));
    }

    public BigDecimal percentage(BigDecimal amount, BigDecimal rateDecimal) {
        return normalize(normalize(amount).multiply(normalizeRate(rateDecimal)));
    }

    public BigDecimal normalizeRate(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(RATE_SCALE, MONEY_ROUNDING);
        }

        return value.setScale(RATE_SCALE, MONEY_ROUNDING);
    }
}
