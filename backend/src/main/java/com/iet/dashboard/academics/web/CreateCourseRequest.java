package com.iet.dashboard.academics.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Course catalog creation payload. The LTP plan is the authority for the course's weekly
 * contact hours: {@code lecture_hours}, {@code tutorial_hours} and {@code practical_hours}
 * are derived from it rather than entered separately.
 */
public record CreateCourseRequest(
        @NotBlank(message = "Course code is required")
        String code,

        @NotBlank(message = "Course name is required")
        String name,

        String type,

        /** CORE, DEPARTMENT_ELECTIVE or OPEN_ELECTIVE. Defaults to CORE. */
        String category,

        Integer credits,

        /** Lecture-Tutorial-Practical plan, e.g. {@code 3-1-2}. Defaults to {@code 3-0-0}. */
        String ltp,

        String description
) {
    public CreateCourseRequest(String code, String name, String type) {
        this(code, name, type, null, null, null, null);
    }

    public CreateCourseRequest(String code, String name, String type, Integer credits, String ltp, String description) {
        this(code, name, type, null, credits, ltp, description);
    }

    /** Normalises coordinator input ({@code de}, {@code open elective}, …) to a stored category. */
    public String normalizedCategory() {
        if (category == null || category.isBlank()) {
            return "CORE";
        }
        String value = category.trim().toUpperCase().replace('-', ' ').replace('_', ' ');
        if (value.equals("OE") || value.contains("OPEN")) {
            return "OPEN_ELECTIVE";
        }
        if (value.equals("DE") || value.contains("ELECTIVE")) {
            return "DEPARTMENT_ELECTIVE";
        }
        if (value.contains("CORE")) {
            return "CORE";
        }
        throw new IllegalArgumentException(
                "Course category must be CORE, DEPARTMENT_ELECTIVE or OPEN_ELECTIVE (got: " + category + ")");
    }

    /** Parses the LTP plan into {@code [lectures, tutorials, practicals]}. */
    public int[] ltpHours() {
        String raw = (ltp == null || ltp.isBlank()) ? "3-0-0" : ltp.trim().replace(" ", "");
        String[] parts = raw.split("-");
        if (parts.length != 3) {
            throw new IllegalArgumentException(ltpError());
        }

        int[] hours = new int[3];
        for (int i = 0; i < 3; i++) {
            try {
                hours[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(ltpError());
            }
            if (hours[i] < 0 || hours[i] > 20) {
                throw new IllegalArgumentException("LTP hours must be between 0 and 20 (got: " + ltp + ")");
            }
        }
        return hours;
    }

    public String normalizedLtp() {
        int[] hours = ltpHours();
        return hours[0] + "-" + hours[1] + "-" + hours[2];
    }

    private String ltpError() {
        return "LTP must be three hyphen-separated non-negative numbers, e.g. 3-1-2 (got: " + ltp + ")";
    }
}
