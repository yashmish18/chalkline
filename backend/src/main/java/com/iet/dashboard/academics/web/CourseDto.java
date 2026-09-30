package com.iet.dashboard.academics.web;

import com.iet.dashboard.academics.domain.Course;

public record CourseDto(
        Long id,
        String code,
        String name,
        String type,
        int credits,
        String ltp,
        int lectureHours,
        int tutorialHours,
        int practicalHours,
        String description
) {
    public CourseDto(Long id, String code, String name, String type, int credits) {
        this(id, code, name, type, credits, "3-0-0", 3, 0, 0, null);
    }

    public CourseDto(Long id, String code, String name, String type) {
        this(id, code, name, type, 3, "3-0-0", 3, 0, 0, null);
    }

    public static CourseDto fromEntity(Course course) {
        return new CourseDto(
                course.getId(),
                course.getCode(),
                course.getName(),
                course.getType(),
                course.getCredits(),
                course.getLtp(),
                course.getLectureHours(),
                course.getTutorialHours(),
                course.getPracticalHours(),
                course.getDescription()
        );
    }
}
