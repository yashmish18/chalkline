package com.iet.dashboard.deadlines.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "deadline_assignment", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"deadline_id", "faculty_id", "offering_id"})
})
public class DeadlineAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deadline_id", nullable = false)
    private Deadline deadline;

    @Column(name = "faculty_id", nullable = false)
    private Long facultyId;

    @Column(name = "offering_id")
    private Long offeringId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public DeadlineAssignment() {
    }

    public DeadlineAssignment(Deadline deadline, Long facultyId, Long offeringId) {
        this.deadline = deadline;
        this.facultyId = facultyId;
        this.offeringId = offeringId;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Deadline getDeadline() {
        return deadline;
    }

    public void setDeadline(Deadline deadline) {
        this.deadline = deadline;
    }

    public Long getFacultyId() {
        return facultyId;
    }

    public void setFacultyId(Long facultyId) {
        this.facultyId = facultyId;
    }

    public Long getOfferingId() {
        return offeringId;
    }

    public void setOfferingId(Long offeringId) {
        this.offeringId = offeringId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
