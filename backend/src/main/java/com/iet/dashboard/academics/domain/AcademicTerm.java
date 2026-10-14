package com.iet.dashboard.academics.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "academic_term")
public class AcademicTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", unique = true, nullable = false, length = 100)
    private String name;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "is_active", nullable = false)
    private boolean active = false;

    /** Continuous assessments each section must hold this semester. */
    @Column(name = "required_assessments", nullable = false)
    private int requiredAssessments = 3;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public AcademicTerm() {
    }

    public AcademicTerm(String name, String academicYear, LocalDate startDate, LocalDate endDate, boolean active) {
        this.name = name;
        this.academicYear = academicYear;
        this.startDate = startDate;
        this.endDate = endDate;
        this.active = active;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(String academicYear) {
        this.academicYear = academicYear;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public int getRequiredAssessments() {
        return requiredAssessments;
    }

    public void setRequiredAssessments(int requiredAssessments) {
        this.requiredAssessments = requiredAssessments;
    }
}
