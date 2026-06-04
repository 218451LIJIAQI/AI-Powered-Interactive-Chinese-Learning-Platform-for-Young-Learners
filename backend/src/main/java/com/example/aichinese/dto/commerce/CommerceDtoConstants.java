package com.example.aichinese.dto.commerce;

public final class CommerceDtoConstants {

    public static final int ITEM_TYPE_MAX_LENGTH = 30;

    public static final int FEATURE_KEY_MAX_LENGTH = 100;

    public static final int REFUND_REASON_MAX_LENGTH = 1000;

    public static final int DECISION_REASON_MAX_LENGTH = 1000;

    public static final int PAYOUT_NOTE_MAX_LENGTH = 1000;

    public static final int MONEY_INTEGER_DIGITS = 8;

    public static final int MONEY_FRACTION_DIGITS = 2;

    public static final String COMMISSION_RATE_MIN = "0.00";

    public static final String COMMISSION_RATE_MAX = "1.00";

    public static final int COMMISSION_RATE_INTEGER_DIGITS = 1;

    public static final int COMMISSION_RATE_FRACTION_DIGITS = 4;

    public static final String CURRENCY_CODE_PATTERN = "^[A-Z]{3}$";

    private CommerceDtoConstants() {
        throw new UnsupportedOperationException("No instances");
    }
}
