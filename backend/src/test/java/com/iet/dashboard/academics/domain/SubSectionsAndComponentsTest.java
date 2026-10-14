package com.iet.dashboard.academics.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class SubSectionsAndComponentsTest {

    @ParameterizedTest(name = "\"{0}\" in section {1} -> {2}")
    @CsvSource({
            // The formatter's "Batch <section>" means the whole section's lab, not a split.
            "'Batch C',     C,   ALL",
            "'batch c',     C,   ALL",
            "C,             C,   ALL",
            "'',            C,   ALL",
            "ALL,           C,   ALL",
            // Real batches are kept as written, without the prefix.
            "'Batch C1',    C,   C1",
            "c2,            C,   C2",
            "'Batch A1',    A,   A1",
            // A bare number is a batch of the row's own section.
            "'Batch 1',     D,   D1",
            "2,             ALL, 2",
            // Anything else is a named batch, kept upper-cased.
            "'Batch Extra', ALL, EXTRA",
            "'Batch AI',    ALL, AI",
    })
    void normalisesBatchesReadFromAnUpload(String raw, String section, String expected) {
        assertThat(SubSections.normalise(raw, section)).isEqualTo(expected);
    }

    @Test
    void nullBatchIsTheWholeSection() {
        assertThat(SubSections.normalise(null, "C")).isEqualTo("ALL");
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            "LECTURE,   LECTURE",
            "theory,    LECTURE",
            "L,         LECTURE",
            "'',        LECTURE",
            "TUTORIAL,  TUTORIAL",
            "tut,       TUTORIAL",
            "T,         TUTORIAL",
            "PRACTICAL, PRACTICAL",
            "Lab,       PRACTICAL",
            "P,         PRACTICAL",
            "ALL,       ALL",
    })
    void parsesEveryComponentSpellingToOneVocabulary(String raw, CourseComponent expected) {
        assertThat(CourseComponent.parse(raw)).isEqualTo(expected);
    }

    @Test
    void strictParsingRejectsWordsThatAreNotAComponent() {
        assertThat(CourseComponent.parseStrict("theory")).isEqualTo(CourseComponent.LECTURE);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> CourseComponent.parseStrict("SEMINAR"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
