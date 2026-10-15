package com.ezaigc.hr.intelligence.matching.domain.service;

import java.util.Locale;
import java.util.regex.Pattern;

final class MatchingText {
    private MatchingText() {}

    /**
     * Matches an extracted value against a required value without treating one ASCII technical term as
     * an arbitrary substring of another. Chinese phrases retain containment because they do not have
     * whitespace word boundaries; ASCII terms require boundaries so Java does not match JavaScript.
     */
    static boolean similar(String actual, String expected) {
        String actualNormalized = normalize(actual), expectedNormalized = normalize(expected);
        if (actualNormalized.isEmpty() || expectedNormalized.isEmpty()) {
            return false;
        }
        if (actualNormalized.equals(expectedNormalized)) {
            return true;
        }
        if (isStandaloneAsciiTerm(expected)) {
            return containsStandaloneAsciiTerm(actual, expected.trim());
        }
        return actualNormalized.contains(expectedNormalized);
    }

    private static boolean isStandaloneAsciiTerm(String value) {
        return value != null && value.trim().matches("[A-Za-z0-9+#.\\-]+");
    }

    private static boolean containsStandaloneAsciiTerm(String value, String term) {
        if (value == null || term.isEmpty()) {
            return false;
        }
        Pattern termPattern = Pattern.compile(
                "(?<![A-Za-z0-9+#.\\-])" + Pattern.quote(term) + "(?![A-Za-z0-9+#.\\-])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return termPattern.matcher(value).find();
    }

    static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}+#]", "");
    }
}
