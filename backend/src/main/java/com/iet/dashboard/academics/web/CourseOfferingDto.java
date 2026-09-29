package com.iet.dashboard.academics.web;

import com.iet.dashboard.academics.domain.CourseOffering;

public record CourseOfferingDto(
        Long id,
        CourseDto course,
        String academicYear,
        int yearOfStudy,
        int semester,
        String section,
        Long termId,
        String termName
) {
    public CourseOfferingDto(Long id, CourseDto course, String academicYear, int yearOfStudy, int semester, String section) {
        this(id, course, academicYear, yearOfStudy, semester, section, null, null);
    }

    public static CourseOfferingDto fromEntity(CourseOffering offering) {
        Long termId = offering.getTerm() != null ? offering.getTerm().getId() : null;
        String termName = offering.getTerm() != null ? offering.getTerm().getName() : null;
        return new CourseOfferingDto(
                offering.getId(),
                CourseDto.fromEntity(offering.getCourse()),
                offering.getAcademicYear(),
                offering.getYearOfStudy(),
                offering.getSemester(),
                offering.getSection(),
                termId,
                termName
        );
    }
}
