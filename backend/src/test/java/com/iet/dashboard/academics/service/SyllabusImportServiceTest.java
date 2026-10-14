package com.iet.dashboard.academics.service;

import com.iet.dashboard.academics.domain.Course;
import com.iet.dashboard.academics.domain.SyllabusTopic;
import com.iet.dashboard.academics.repo.CourseRepository;
import com.iet.dashboard.academics.repo.SyllabusTopicRepository;
import com.iet.dashboard.academics.web.ParsedSyllabusDto;
import com.iet.dashboard.academics.web.SyllabusTopicDto;
import com.iet.dashboard.shared.error.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyllabusImportServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private SyllabusTopicRepository syllabusTopicRepository;

    @InjectMocks
    private SyllabusImportServiceImpl service;

    private static final String SAMPLE = """
            CS501 Computer Networks                                                    Page 1
            Course Description
            This course introduces the layered architecture of computer networks.

            UNIT I - Introduction and Physical Layer
            1.1 Introduction to networks and uses
            1.2 Network hardware and software
            1.3 OSI reference model and TCP/IP stack

            UNIT II: Data Link Layer
            2.1 Error detection and correction
            2.2 Multiple access protocols
            2.3 Ethernet and wireless LANs

            UNIT III - Network Layer
            3.1 IPv4 addressing and subnetting
            3.2 Routing algorithms
            3.3 Congestion control

            Text Books
            1. Tanenbaum, Computer Networks, Pearson
            2. Kurose, Computer Networking, Pearson
            """;

    @Test
    void parsesUnitsTopicsAndSkipsNoiseSections() {
        ParsedSyllabusDto parsed = service.parseText(SAMPLE, "cs501.pdf");

        assertEquals("cs501.pdf", parsed.sourceFileName());
        assertEquals(3, parsed.units().size());
        assertEquals(9, parsed.topicCount());

        ParsedSyllabusDto.Unit first = parsed.units().get(0);
        assertEquals(1, first.unitNumber());
        assertEquals("Introduction and Physical Layer", first.unitTitle());
        assertEquals(3, first.topics().size());
        assertEquals("Introduction to networks and uses", first.topics().get(0).topicName());

        ParsedSyllabusDto.Unit second = parsed.units().get(1);
        assertEquals(2, second.unitNumber());
        assertEquals("Data Link Layer", second.unitTitle());
    }

    @Test
    void spreadsTopicsAcrossTheTermAndDropsTextbookEntries() {
        ParsedSyllabusDto parsed = service.parseText(SAMPLE, "cs501.pdf");

        List<ParsedSyllabusDto.Topic> allTopics = parsed.units().stream()
                .flatMap(unit -> unit.topics().stream())
                .toList();

        // Topics are spread with ceil(n * weeks / total): the 1st of 9 topics over 11 weeks
        // lands on week 2, and the last lands on the final week.
        assertEquals(2, allTopics.get(0).targetWeek());
        assertEquals(11, allTopics.get(allTopics.size() - 1).targetWeek());
        assertTrue(allTopics.stream().allMatch(topic -> topic.targetWeek() >= 1 && topic.targetWeek() <= 11));
        // Weeks must be non-decreasing down the syllabus.
        for (int i = 1; i < allTopics.size(); i++) {
            assertTrue(allTopics.get(i).targetWeek() >= allTopics.get(i - 1).targetWeek());
        }

        // "Text Books" and its entries are not syllabus topics.
        assertTrue(allTopics.stream().noneMatch(topic -> topic.topicName().contains("Tanenbaum")));
        assertTrue(allTopics.stream().noneMatch(topic -> topic.topicName().contains("Kurose")));
    }

    @Test
    void handlesRomanNumeralUnitsAndBlankInput() {
        ParsedSyllabusDto roman = service.parseText("""
                UNIT-IV: Transport Layer
                1. UDP and TCP
                2. Flow control
                """, "unit4.txt");

        assertEquals(1, roman.units().size());
        assertEquals(4, roman.units().get(0).unitNumber());
        assertEquals(2, roman.topicCount());

        ParsedSyllabusDto empty = service.parseText("   ", "blank.pdf");
        assertTrue(empty.units().isEmpty());
        assertEquals(0, empty.topicCount());
    }

    @Test
    void applyReplacesExistingSyllabus() {
        Course course = new Course("CS501", "Networks", "THEORY", 4, "3-0-2", 3, 0, 2, null);
        course.setId(7L);
        when(courseRepository.findById(7L)).thenReturn(Optional.of(course));
        when(syllabusTopicRepository.saveAll(any())).thenAnswer(inv -> {
            List<SyllabusTopic> saved = inv.getArgument(0);
            for (int i = 0; i < saved.size(); i++) {
                saved.get(i).setId((long) (i + 1));
            }
            return saved;
        });

        List<SyllabusTopicDto> result = service.apply(7L, service.parseText(SAMPLE, "cs501.pdf"));

        assertEquals(9, result.size());
        assertEquals(1, result.get(0).unitNumber());
        assertEquals(9, result.get(8).lectureOrder());
        verify(syllabusTopicRepository).deleteByCourseId(7L);
    }

    @Test
    void applyRejectsAnEmptyPreview() {
        Course course = new Course("CS501", "Networks", "THEORY");
        course.setId(7L);
        when(courseRepository.findById(7L)).thenReturn(Optional.of(course));

        ParsedSyllabusDto empty = new ParsedSyllabusDto("blank.pdf", 0, List.of());

        assertThrows(ValidationException.class, () -> service.apply(7L, empty));
        verify(syllabusTopicRepository, never()).deleteByCourseId(anyLong());
    }
}
