package com.iet.dashboard.academics.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "syllabus_topic")
public class SyllabusTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "unit_number", nullable = false)
    private int unitNumber;

    @Column(name = "unit_title", nullable = false)
    private String unitTitle;

    @Column(name = "topic_name", nullable = false)
    private String topicName;

    @Column(name = "target_week", nullable = false)
    private int targetWeek;

    @Column(name = "lecture_order", nullable = false)
    private int lectureOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public SyllabusTopic() {
    }

    public SyllabusTopic(Course course, int unitNumber, String unitTitle, String topicName, int targetWeek, int lectureOrder) {
        this.course = course;
        this.unitNumber = unitNumber;
        this.unitTitle = unitTitle;
        this.topicName = topicName;
        this.targetWeek = targetWeek;
        this.lectureOrder = lectureOrder;
        this.createdAt = Instant.now();
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

    public int getUnitNumber() {
        return unitNumber;
    }

    public void setUnitNumber(int unitNumber) {
        this.unitNumber = unitNumber;
    }

    public String getUnitTitle() {
        return unitTitle;
    }

    public void setUnitTitle(String unitTitle) {
        this.unitTitle = unitTitle;
    }

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    public int getTargetWeek() {
        return targetWeek;
    }

    public void setTargetWeek(int targetWeek) {
        this.targetWeek = targetWeek;
    }

    public int getLectureOrder() {
        return lectureOrder;
    }

    public void setLectureOrder(int lectureOrder) {
        this.lectureOrder = lectureOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
