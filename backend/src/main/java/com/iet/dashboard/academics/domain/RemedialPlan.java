package com.iet.dashboard.academics.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/** A faculty member's commitment to recover a section that fell behind or scored poorly. */
@Entity
@Table(name = "remedial_plan")
public class RemedialPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offering_id", nullable = false)
    private CourseOffering offering;

    @Column(name = "faculty_id", nullable = false)
    private Long facultyId;

    /** BEHIND_SYLLABUS, LOW_ASSESSMENT, CANCELLED_CLASSES, OTHER */
    @Column(name = "reason", nullable = false, length = 30)
    private String reason;

    @Column(name = "lagging_topics")
    private String laggingTopics;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "extra_sessions", nullable = false)
    private int extraSessions;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    /** SUBMITTED, COMPLETED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "SUBMITTED";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    protected RemedialPlan() {
    }

    public RemedialPlan(CourseOffering offering, Long facultyId, String reason, String laggingTopics, String action,
                        int extraSessions, LocalDate targetDate) {
        this.offering = offering;
        this.facultyId = facultyId;
        this.reason = reason;
        this.laggingTopics = laggingTopics;
        this.action = action;
        this.extraSessions = extraSessions;
        this.targetDate = targetDate;
    }

    public void complete() {
        this.status = "COMPLETED";
        this.completedAt = Instant.now();
    }

    public Long getId() { return id; }
    public CourseOffering getOffering() { return offering; }
    public Long getFacultyId() { return facultyId; }
    public String getReason() { return reason; }
    public String getLaggingTopics() { return laggingTopics; }
    public String getAction() { return action; }
    public int getExtraSessions() { return extraSessions; }
    public LocalDate getTargetDate() { return targetDate; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}
