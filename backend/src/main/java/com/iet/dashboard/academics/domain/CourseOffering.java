package com.iet.dashboard.academics.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "course_offering", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"course_id", "academic_year", "year_of_study", "semester", "section"})
})
public class CourseOffering {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @Column(name = "year_of_study", nullable = false)
    private int yearOfStudy;

    @Column(name = "semester", nullable = false)
    private int semester;

    @Column(name = "section", nullable = false, length = 10)
    private String section;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id")
    private AcademicTerm term;

    public CourseOffering() {
    }

    public CourseOffering(Course course, String academicYear, int yearOfStudy, int semester, String section) {
        this(course, academicYear, yearOfStudy, semester, section, null);
    }

    public CourseOffering(Course course, String academicYear, int yearOfStudy, int semester, String section, AcademicTerm term) {
        this.course = course;
        this.academicYear = academicYear;
        this.yearOfStudy = yearOfStudy;
        this.semester = semester;
        this.section = section;
        this.term = term;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(String academicYear) {
        this.academicYear = academicYear;
    }

    public int getYearOfStudy() {
        return yearOfStudy;
    }

    public void setYearOfStudy(int yearOfStudy) {
        this.yearOfStudy = yearOfStudy;
    }

    public int getSemester() {
        return semester;
    }

    public void setSemester(int semester) {
        this.semester = semester;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public AcademicTerm getTerm() {
        return term;
    }

    public void setTerm(AcademicTerm term) {
        this.term = term;
    }
}
