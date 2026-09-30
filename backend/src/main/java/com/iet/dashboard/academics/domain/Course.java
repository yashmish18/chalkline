package com.iet.dashboard.academics.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "course")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", unique = true, nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "type", nullable = false, length = 50)
    private String type = "THEORY";

    @Column(name = "credits", nullable = false)
    private int credits = 3;

    @Column(name = "ltp", length = 10, nullable = false)
    private String ltp = "3-0-0";

    @Column(name = "lecture_hours", nullable = false)
    private int lectureHours = 3;

    @Column(name = "tutorial_hours", nullable = false)
    private int tutorialHours = 0;

    @Column(name = "practical_hours", nullable = false)
    private int practicalHours = 0;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    public Course() {
    }

    public Course(String code, String name, String type) {
        this(code, name, type, 3, "3-0-0", 3, 0, 0, null);
    }

    public Course(String code, String name, String type, int credits) {
        this(code, name, type, credits, "3-0-0", 3, 0, 0, null);
    }

    public Course(String code, String name, String type, int credits, String ltp, int lectureHours, int tutorialHours, int practicalHours, String description) {
        this.code = code;
        this.name = name;
        this.type = type != null ? type : "THEORY";
        this.credits = credits;
        this.ltp = ltp != null ? ltp : "3-0-0";
        this.lectureHours = lectureHours;
        this.tutorialHours = tutorialHours;
        this.practicalHours = practicalHours;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getCredits() {
        return credits;
    }

    public void setCredits(int credits) {
        this.credits = credits;
    }

    public String getLtp() {
        return ltp;
    }

    public void setLtp(String ltp) {
        this.ltp = ltp;
    }

    public int getLectureHours() {
        return lectureHours;
    }

    public void setLectureHours(int lectureHours) {
        this.lectureHours = lectureHours;
    }

    public int getTutorialHours() {
        return tutorialHours;
    }

    public void setTutorialHours(int tutorialHours) {
        this.tutorialHours = tutorialHours;
    }

    public int getPracticalHours() {
        return practicalHours;
    }

    public void setPracticalHours(int practicalHours) {
        this.practicalHours = practicalHours;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
