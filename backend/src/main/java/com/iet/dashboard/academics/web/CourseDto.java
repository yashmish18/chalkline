package com.iet.dashboard.academics.web;

import com.iet.dashboard.academics.domain.Course;

public record CourseDto(
        Long id,
        String code,
        String name,
        String type,
        int credits
) {
    public CourseDto(Long id, String code, String name, String type) {
        this(id, code, name, type, 3);
    }

    public static CourseDto fromEntity(Course course) {
        return new CourseDto(course.getId(), course.getCode(), course.getName(), course.getType(), course.getCredits());
    }
}
