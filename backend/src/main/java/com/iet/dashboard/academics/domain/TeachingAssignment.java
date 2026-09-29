package com.iet.dashboard.academics.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "teaching_assignment", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"offering_id", "faculty_id", "component"})
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

    public TeachingAssignment() {
    }

    public TeachingAssignment(CourseOffering offering, Long facultyId, CourseComponent component) {
        this.offering = offering;
        this.facultyId = facultyId;
        this.component = component;
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
}
