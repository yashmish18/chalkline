package com.iet.dashboard.academics.domain;

import java.util.Locale;

/**
 * The one reading of a batch (sub-section) value, shared by every write path.
 *
 * <p>The load sheet writes "(Batch C,F)" for the lab of sections C and F, and the formatter turns
 * that into {@code "Batch C"} — the whole section, not a split. Only values that name something
 * finer than the section (C1, C2, EXTRA) are batches; nothing is ever invented.
 */
public final class SubSections {

    public static final String WHOLE_SECTION = "ALL";

    private SubSections() {
    }

    public static String normalise(String raw, String section) {
        String value = raw == null ? "" : raw.replace(' ', ' ').trim();
        value = value.replaceFirst("(?i)^batch\\b\\s*", "").trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty() || value.equals(WHOLE_SECTION)) {
            return WHOLE_SECTION;
        }
        String sectionKey = section == null ? "" : section.trim().toUpperCase(Locale.ROOT);
        if (value.equals(sectionKey)) {
            return WHOLE_SECTION;
        }
        if (value.chars().allMatch(Character::isDigit) && !sectionKey.isEmpty() && !sectionKey.equals(WHOLE_SECTION)) {
            return sectionKey + value;
        }
        return value;
    }
}
