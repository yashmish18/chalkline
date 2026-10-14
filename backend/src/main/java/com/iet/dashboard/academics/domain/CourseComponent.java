package com.iet.dashboard.academics.domain;

import java.util.Locale;

/**
 * LTP component that a teaching assignment owns: {@code LECTURE}, {@code TUTORIAL} or
 * {@code PRACTICAL}, or {@code ALL} for the whole L+T+P plan.
 *
 * <p>{@code THEORY} and {@code LAB} are legacy spellings kept only so rows written before
 * V2 still load; {@link #parse} never produces them and V2 rewrites them.
 */
public enum CourseComponent {
    /** Whole course: lectures, tutorials and practicals. */
    ALL,
    /** Legacy alias of {@link #LECTURE}. */
    THEORY,
    /** Legacy alias of {@link #PRACTICAL}. */
    LAB,
    LECTURE,
    TUTORIAL,
    PRACTICAL;

    /** The single mapping from any spelling in a sheet, form or request to a component. */
    public static CourseComponent parse(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (value.equals("ALL")) {
            return ALL;
        }
        if (value.equals("P") || value.contains("PRACTICAL") || value.contains("LAB")) {
            return PRACTICAL;
        }
        if (value.equals("T") || value.startsWith("TUT")) {
            return TUTORIAL;
        }
        return LECTURE;
    }

    /** Like {@link #parse} for typed input, but an unrecognised word is an error, not a lecture. */
    public static CourseComponent parseStrict(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("ALL|L|T|P|LECTURE|THEORY|TUT|TUTORIAL|PRACTICAL|LAB")) {
            throw new IllegalArgumentException("Unknown component: " + raw
                    + " (expected ALL, LECTURE, TUTORIAL or PRACTICAL)");
        }
        return parse(value);
    }
}
