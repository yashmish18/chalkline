package com.iet.dashboard.academics.web;

import com.iet.dashboard.academics.domain.Course;

public record CourseDto(
        Long id,
        String code,
        String name,
        String type
) {
    public static CourseDto fromEntity(Course course) {
        return new CourseDto(course.getId(), course.getCode(), course.getName(), course.getType());
    }
}
