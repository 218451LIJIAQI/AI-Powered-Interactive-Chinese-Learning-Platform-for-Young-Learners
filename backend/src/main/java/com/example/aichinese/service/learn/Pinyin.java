package com.example.aichinese.service.learn;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Pinyin as it is typed: without tone marks, spaces or apostrophes, with {@code ü} written {@code v} (the keyboard
 * convention, so 女 is {@code nv}). Tone numbers ({@code xie4xie5}) are dropped as well.
 */
final class Pinyin {

    private static final Pattern UMLAUT = Pattern.compile("[üǖǘǚǜ]");
    private static final Pattern NON_ASCII_LOWER = Pattern.compile("[^a-z]");
    private static final Pattern SYLLABLE_DELIMITERS = Pattern.compile("(?U)[\\s'’0-5-]+");
    private static final String[] TWO_CHAR_INITIALS = { "zh", "ch", "sh" };

    private Pinyin() {
    }

    /**
     * "Xièxie" and "xie xie" both type as "xiexie"; "lǜ" as "lv". The text is composed first, so a "ü" written as "u"
     * with a combining diaeresis (as some keyboards and pasted text give it) is still "v".
     */
    static String typed(String value) {
        if (value == null) {
            return "";
        }
        String lower = Normalizer.normalize(value, Normalizer.Form.NFC).toLowerCase(Locale.ROOT).replace("u:", "v");
        lower = UMLAUT.matcher(lower).replaceAll("v");
        return NON_ASCII_LOWER.matcher(Normalizer.normalize(lower, Normalizer.Form.NFD)).replaceAll("");
    }

    /**
     * Tone numbers and word separators give explicit boundaries. Inside an unseparated word, estimate the
     * syllables from its consonants and vowels for hints such as "two syllables" for "xièxie".
     */
    static List<String> syllables(String value) {
        if (value == null) {
            return List.of();
        }
        return Arrays.stream(SYLLABLE_DELIMITERS.split(value.strip()))
                .map(Pinyin::typed)
                .filter(typed -> !typed.isEmpty())
                .flatMap(typed -> splitWord(typed).stream())
                .toList();
    }

    /** The initial of a typed syllable ("zh", "x", or "" for a syllable that starts with a vowel). */
    static String initial(String typedSyllable) {
        for (String two : TWO_CHAR_INITIALS) {
            if (typedSyllable.startsWith(two)) {
                return two;
            }
        }
        if (!typedSyllable.isEmpty() && "bpmfdtnlgkhjqxrzcsyw".indexOf(typedSyllable.charAt(0)) >= 0) {
            return typedSyllable.substring(0, 1);
        }
        return "";
    }

    /** A greedy split of one typed word into syllables: an initial, then a final up to the next initial. */
    private static List<String> splitWord(String typed) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        int index = 0;
        boolean seenVowel = false;
        while (index < typed.length()) {
            char letter = typed.charAt(index);
            boolean vowel = "aeiouv".indexOf(letter) >= 0;
            if (vowel) {
                seenVowel = true;
            } else if (seenVowel && startsSyllable(typed, index)) {
                parts.add(typed.substring(start, index));
                start = index;
                seenVowel = false;
            }
            index++;
        }
        parts.add(typed.substring(start));
        return parts;
    }

    /** True when a consonant at {@code index} begins a new syllable rather than ending this one (n, ng, r). */
    private static boolean startsSyllable(String typed, int index) {
        char letter = typed.charAt(index);
        boolean nextIsVowel = index + 1 < typed.length() && "aeiouv".indexOf(typed.charAt(index + 1)) >= 0;
        if (letter == 'n' || letter == 'r') {
            return nextIsVowel;
        }
        if (letter == 'g' && index > 0 && typed.charAt(index - 1) == 'n') {
            return nextIsVowel;
        }
        return true;
    }
}
