package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

public enum AgeBand {

    KINDERGARTEN,

    LOWER_PRIMARY,

    UPPER_PRIMARY,

    ALL_AGES;

    private static final Map<String, AgeBand> LOOKUP = EnumLookups.indexByName(AgeBand.class);

    public static Optional<AgeBand> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static AgeBand fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Age band is required.",
                () -> "Invalid age band. Allowed values are KINDERGARTEN, LOWER_PRIMARY, "
                        + "UPPER_PRIMARY, or ALL_AGES.");
    }

    public boolean isAllAges() {
        return this == ALL_AGES;
    }

    public boolean matches(AgeBand learnerBand) {
        return learnerBand == null
                || this == ALL_AGES
                || learnerBand == ALL_AGES
                || this == learnerBand;
    }
}
