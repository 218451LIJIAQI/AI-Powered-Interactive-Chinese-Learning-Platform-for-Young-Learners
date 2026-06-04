package com.example.aichinese.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CommerceMoneyService {

    public static final int MONEY_SCALE = 2;
    public static final int RATE_SCALE = 4;
    public static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    private static final int MONEY_PRECISION = 10;
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING);
    private static final BigDecimal ZERO_RATE = BigDecimal.ZERO.setScale(RATE_SCALE, MONEY_ROUNDING);

    public BigDecimal normalize(BigDecimal value) {
        return value == null ? ZERO_MONEY : value.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    public BigDecimal add(BigDecimal left, BigDecimal right) {
        return normalize(normalize(left).add(normalize(right)));
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
        return value == null ? ZERO_RATE : value.setScale(RATE_SCALE, MONEY_ROUNDING);
    }

    public BigDecimal normalizePersistableAmount(BigDecimal value, String message) {
        BigDecimal amount = normalize(value);
        if (amount.precision() - amount.scale() > MONEY_PRECISION - MONEY_SCALE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return amount;
    }
}
