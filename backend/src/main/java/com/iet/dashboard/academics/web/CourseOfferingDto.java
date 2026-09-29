package com.iet.dashboard.academics.web;

import com.iet.dashboard.academics.domain.CourseOffering;

public record CourseOfferingDto(
        Long id,
        CourseDto course,
        String academicYear,
        int yearOfStudy,
        int semester,
        String section
) {
    public static CourseOfferingDto fromEntity(CourseOffering offering) {
        return new CourseOfferingDto(
                offering.getId(),
                CourseDto.fromEntity(offering.getCourse()),
                offering.getAcademicYear(),
                offering.getYearOfStudy(),
                offering.getSemester(),
                offering.getSection()
        );
    }
}
