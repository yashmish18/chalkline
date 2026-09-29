package com.iet.dashboard.teaching.domain;

import com.iet.dashboard.academics.domain.CourseOffering;
import com.iet.dashboard.academics.domain.SyllabusTopic;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "teaching_session")
public class TeachingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offering_id", nullable = false)
    private CourseOffering offering;

    @Column(name = "faculty_id", nullable = false)
    private Long facultyId;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "session_type", nullable = false, length = 50)
    private String sessionType = "LECTURE";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private SessionStatus status = SessionStatus.HELD;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "reason_not_held", columnDefinition = "TEXT")
    private String reasonNotHeld;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "session_topic_log",
            joinColumns = @JoinColumn(name = "session_id"),
            inverseJoinColumns = @JoinColumn(name = "topic_id")
    )
    private Set<SyllabusTopic> topicsCovered = new HashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public TeachingSession() {
    }

    public TeachingSession(
            CourseOffering offering,
            Long facultyId,
            LocalDate sessionDate,
            String sessionType,
            SessionStatus status,
            String note,
            String reasonNotHeld
    ) {
        this.offering = offering;
        this.facultyId = facultyId;
        this.sessionDate = sessionDate;
        this.sessionType = sessionType != null ? sessionType : "LECTURE";
        this.status = status != null ? status : SessionStatus.HELD;
        this.note = note;
        this.reasonNotHeld = reasonNotHeld;
        this.createdAt = Instant.now();
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

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public void setSessionDate(LocalDate sessionDate) {
        this.sessionDate = sessionDate;
    }

    public String getSessionType() {
        return sessionType;
    }

    public void setSessionType(String sessionType) {
        this.sessionType = sessionType;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getReasonNotHeld() {
        return reasonNotHeld;
    }

    public void setReasonNotHeld(String reasonNotHeld) {
        this.reasonNotHeld = reasonNotHeld;
    }

    public Set<SyllabusTopic> getTopicsCovered() {
        return topicsCovered;
    }

    public void setTopicsCovered(Set<SyllabusTopic> topicsCovered) {
        this.topicsCovered = topicsCovered;
    }

    public void addTopic(SyllabusTopic topic) {
        this.topicsCovered.add(topic);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
