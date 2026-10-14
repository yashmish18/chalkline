package com.iet.dashboard.academics.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/** One continuous internal evaluation (CIE) actually held for a section. */
@Entity
@Table(name = "assessment")
public class Assessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offering_id", nullable = false)
    private CourseOffering offering;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** MID_TERM, QUIZ, ASSIGNMENT, LAB, PROJECT, OTHER */
    @Column(name = "kind", nullable = false, length = 30)
    private String kind;

    @Column(name = "weight_pct", nullable = false)
    private int weightPct;

    @Column(name = "held_on", nullable = false)
    private LocalDate heldOn;

    @Column(name = "students_assessed")
    private Integer studentsAssessed;

    /** Class average as a percentage; null until marks are entered. */
    @Column(name = "average_pct")
    private Integer averagePct;

    @Column(name = "below_40_count")
    private Integer below40Count;

    @Column(name = "recorded_by_faculty_id")
    private Long recordedByFacultyId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Assessment() {
    }

    public Assessment(CourseOffering offering, String name, String kind, int weightPct, LocalDate heldOn,
                      Integer studentsAssessed, Integer averagePct, Integer below40Count, Long recordedByFacultyId) {
        this.offering = offering;
        this.name = name;
        this.kind = kind;
        this.weightPct = weightPct;
        this.heldOn = heldOn;
        this.studentsAssessed = studentsAssessed;
        this.averagePct = averagePct;
        this.below40Count = below40Count;
        this.recordedByFacultyId = recordedByFacultyId;
    }

    public Long getId() { return id; }
    public CourseOffering getOffering() { return offering; }
    public String getName() { return name; }
    public String getKind() { return kind; }
    public int getWeightPct() { return weightPct; }
    public LocalDate getHeldOn() { return heldOn; }
    public Integer getStudentsAssessed() { return studentsAssessed; }
    public Integer getAveragePct() { return averagePct; }
    public Integer getBelow40Count() { return below40Count; }
    public Long getRecordedByFacultyId() { return recordedByFacultyId; }
    public Instant getCreatedAt() { return createdAt; }
}
