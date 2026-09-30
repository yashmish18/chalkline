package com.iet.dashboard.academics.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "teaching_assignment", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"offering_id", "faculty_id", "component", "sub_section"})
})
public class TeachingAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offering_id", nullable = false)
    private CourseOffering offering;

    @Column(name = "faculty_id", nullable = false)
    private Long facultyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "component", nullable = false, length = 50)
    private CourseComponent component;

    @Column(name = "sub_section", nullable = false, length = 50)
    private String subSection = "ALL";

    public TeachingAssignment() {
    }

    public TeachingAssignment(CourseOffering offering, Long facultyId, CourseComponent component) {
        this(offering, facultyId, component, "ALL");
    }

    public TeachingAssignment(CourseOffering offering, Long facultyId, CourseComponent component, String subSection) {
        this.offering = offering;
        this.facultyId = facultyId;
        this.component = component;
        this.subSection = subSection != null ? subSection : "ALL";
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CourseOffering getOffering() {
        return offering;
    }

    public void setOffering(CourseOffering offering) {
        this.offering = offering;
    }

    public Long getFacultyId() {
        return facultyId;
    }

    public void setFacultyId(Long facultyId) {
        this.facultyId = facultyId;
    }

    public CourseComponent getComponent() {
        return component;
    }

    public void setComponent(CourseComponent component) {
        this.component = component;
    }

    public String getSubSection() {
        return subSection;
    }

    public void setSubSection(String subSection) {
        this.subSection = subSection;
    }
}
