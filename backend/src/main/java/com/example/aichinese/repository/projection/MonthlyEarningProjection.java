package com.example.aichinese.repository.projection;

import java.math.BigDecimal;

public interface MonthlyEarningProjection {
    int getYear();

    int getMonth();

    BigDecimal getAmount();
}
