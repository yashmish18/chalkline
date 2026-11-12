package com.iet.dashboard.academics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.LocalDate;

/** A day of the semester with no classes, as the coordinator typed it. */
@Embeddable
public class TermHoliday {

    @Column(name = "holiday_date", nullable = false)
    private LocalDate date;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    protected TermHoliday() {
    }

    public TermHoliday(LocalDate date, String name) {
        this.date = date;
        this.name = name;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getName() {
        return name;
    }
}
