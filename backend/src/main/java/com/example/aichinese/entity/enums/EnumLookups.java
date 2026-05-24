package com.example.aichinese.entity.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Internal helpers that consolidate the case- and whitespace-insensitive
 * name &rarr; constant lookup machinery shared by every {@code enum}
 * declared in this package.
 *
 * <p>Every domain enum exposes the same canonical factory pair:</p>
 * <ul>
 *   <li>{@code Optional<E> tryFrom(String)} &mdash; tolerant lookup that
 *       returns {@link Optional#empty()} for {@code null}, blank, or
 *       unknown input;</li>
 *   <li>{@code E fromValue(String)} &mdash; strict lookup that throws
 *       {@link IllegalArgumentException} with a stable, caller-visible
 *       message on missing or unknown input.</li>
 * </ul>
 *
 * <p>Centralising the implementation here removes ~30 copies of the same
 * boilerplate, guarantees consistent {@code null} / blank handling
 * across every enum, and gives the codebase a single seam to apply
 * future improvements (e.g. metrics, alias maps, telemetry on
 * unrecognised values without changing every call site).</p>
 *
 * <p>This class is intentionally <em>package-private</em>: it is an
 * implementation detail of the enums it supports. Production code must
 * always go through the public {@code tryFrom} / {@code fromValue}
 * factories on each enum.</p>
 */
final class EnumLookups {

    private EnumLookups() {
        throw new AssertionError("EnumLookups is a utility class and must not be instantiated.");
    }

    /**
     * Build an immutable, upper-cased {@code name() &rarr; constant}
     * index for the supplied enum class. Intended to be invoked exactly
     * once at class-load time and stored in a {@code private static
     * final Map<String, E>} field on the owning enum.
     *
     * <p>Keys are normalised with {@link Locale#ROOT} to make the
     * lookup deterministic across JVM locales and to neutralise the
     * Turkish-locale {@code I &rarr; ı} hazard.</p>
     *
     * @param type concrete enum class, never {@code null}
     * @param <E>  the enum type
     * @return immutable index of upper-cased names to enum constants
     * @throws NullPointerException if {@code type} is {@code null}
     */
    static <E extends Enum<E>> Map<String, E> indexByName(Class<E> type) {
        Objects.requireNonNull(type, "enum type must not be null");
        return Arrays.stream(type.getEnumConstants())
                .collect(Collectors.toUnmodifiableMap(
                        value -> value.name().toUpperCase(Locale.ROOT),
                        Function.identity()));
    }

    /**
     * Tolerant case- and whitespace-insensitive lookup against an index
     * produced by {@link #indexByName(Class)}.
     *
     * @param lookup pre-built index, never {@code null}
     * @param value  raw textual representation; may be {@code null} or blank
     * @param <E>    the enum type
     * @return matching constant, or {@link Optional#empty()} when the
     *         input is {@code null}, blank, or does not match any
     *         constant after stripping and upper-casing
     */
    static <E extends Enum<E>> Optional<E> tryFrom(Map<String, E> lookup, String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(lookup.get(value.strip().toUpperCase(Locale.ROOT)));
    }

    /**
     * Strict counterpart of {@link #tryFrom(Map, String)} that throws
     * {@link IllegalArgumentException} with a caller-supplied message
     * for both the "missing" and "invalid" cases.
     *
     * <p>The {@code invalidMessage} supplier is intentionally lazy so
     * that the (often verbose) "Allowed values are &hellip;" string is
     * only built when the input does not match a known constant.</p>
     *
     * @param lookup         pre-built index produced by {@link #indexByName(Class)}
     * @param value          raw textual representation; may be {@code null} or blank
     * @param missingMessage error text for {@code null} or blank input
     * @param invalidMessage lazy error text for input that does not match any constant
     * @param <E>            the enum type
     * @return the matching enum constant
     * @throws IllegalArgumentException when {@code value} is {@code null}, blank,
     *                                  or does not name a known constant
     */
    static <E extends Enum<E>> E fromValue(
            Map<String, E> lookup,
            String value,
            String missingMessage,
            Supplier<String> invalidMessage) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(missingMessage);
        }
        return tryFrom(lookup, value)
                .orElseThrow(() -> new IllegalArgumentException(invalidMessage.get()));
    }
}
