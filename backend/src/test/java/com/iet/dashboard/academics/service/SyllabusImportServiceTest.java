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

    @org.mockito.Captor
    private org.mockito.ArgumentCaptor<Iterable<SyllabusTopic>> deleted;

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

    private static final String SHORT = "UNIT I - Introduction\n1.1 Database system concepts\n1.2 Data models\n";

    /** Real files, not pre-extracted text: an upload that extracts nothing can never be saved. */
    @Test
    void readsTheTextOfPlainTextPdfAndWordUploads() throws Exception {
        var txt = new org.springframework.mock.web.MockMultipartFile("file", "s.txt", "text/plain",
                SHORT.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        var pdfBytes = new java.io.ByteArrayOutputStream();
        try (var doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            var page = new org.apache.pdfbox.pdmodel.PDPage();
            doc.addPage(page);
            try (var out = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                out.beginText();
                out.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                        org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
                out.setLeading(14);
                out.newLineAtOffset(50, 700);
                for (String line : SHORT.split("\n")) {
                    out.showText(line);
                    out.newLine();
                }
                out.endText();
            }
            doc.save(pdfBytes);
        }
        var pdf = new org.springframework.mock.web.MockMultipartFile("file", "s.pdf", "application/pdf", pdfBytes.toByteArray());

        var docxBytes = new java.io.ByteArrayOutputStream();
        try (var doc = new org.apache.poi.xwpf.usermodel.XWPFDocument()) {
            for (String line : SHORT.split("\n")) {
                doc.createParagraph().createRun().setText(line);
            }
            doc.write(docxBytes);
        }
        var docx = new org.springframework.mock.web.MockMultipartFile("file", "s.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes.toByteArray());

        for (var file : List.of(txt, pdf, docx)) {
            ParsedSyllabusDto parsed = service.parse(file);
            assertEquals(1, parsed.units().size(), file.getOriginalFilename());
            assertEquals(List.of("Database system concepts", "Data models"),
                    parsed.units().get(0).topics().stream().map(ParsedSyllabusDto.Topic::topicName).toList(),
                    file.getOriginalFilename());
        }
    }

    private static org.springframework.mock.web.MockMultipartFile cloudMl() throws Exception {
        return new org.springframework.mock.web.MockMultipartFile("file", "Cloud ML Course Description.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                java.nio.file.Files.readAllBytes(java.nio.file.Path.of("src/test/resources/syllabus/cloud-ml-course-description.docx")));
    }

    /**
     * The institute's real course-description format: Word list paragraphs with no "1.1" or bullet
     * characters in the text, "Unit I — Title" headings, and an "Online resources" list at the end.
     * Every unit and topic must come out exactly as written, and nothing invented (no target weeks).
     */
    @Test
    void readsTheRealCourseDescriptionExactlyAsWritten() throws Exception {
        ParsedSyllabusDto parsed = service.parse(cloudMl());

        assertEquals(List.of("AI/ML in the Cloud", "Building Machine Learning Solutions on AWS",
                        "Cloud-Based AI Applications", "Generative AI and Foundation Models",
                        "Prompt Engineering and Generative AI Applications", "Responsible and Secure Generative AI"),
                parsed.units().stream().map(ParsedSyllabusDto.Unit::unitTitle).toList());
        assertEquals(List.of(1, 2, 3, 4, 5, 6), parsed.units().stream().map(ParsedSyllabusDto.Unit::unitNumber).toList());
        assertEquals(List.of(6, 9, 7, 9, 12, 12), parsed.units().stream().map(u -> u.topics().size()).toList());
        assertEquals(List.of("AI, ML, Deep Learning, and Generative AI", "Why cloud computing for AI/ML?",
                        "Cloud-based AI/ML workflows", "AI/ML applications and industry use cases",
                        "Overview of AWS AI/ML services", "Introduction to Amazon SageMaker"),
                parsed.units().get(0).topics().stream().map(ParsedSyllabusDto.Topic::topicName).toList());
        assertEquals("Evaluating GenAI applications", parsed.units().get(5).topics().get(11).topicName());
        assertEquals("Feature engineering — conceptual overview", parsed.units().get(1).topics().get(2).topicName());

        var all = parsed.units().stream().flatMap(u -> u.topics().stream()).toList();
        assertTrue(all.stream().noneMatch(t -> t.topicName().contains("http") || t.topicName().contains("Elements of AI")),
                "online resources are not topics");
        assertTrue(all.stream().allMatch(t -> t.targetWeek() == 0), "no invented target weeks");
        assertEquals("3-0-2", parsed.documentLtp());
        assertEquals("Cloud-Based Machine Learning", parsed.documentTitle());
    }

    /** Old Word 97 (.doc): the same document saved in the old format reads exactly like the .docx. */
    @Test
    void readsTheSameCourseDescriptionSavedAsAnOldWordDoc() throws Exception {
        var doc = new org.springframework.mock.web.MockMultipartFile("file", "Cloud ML Course Description.doc", "application/msword",
                java.nio.file.Files.readAllBytes(java.nio.file.Path.of("src/test/resources/syllabus/cloud-ml-course-description.doc")));
        ParsedSyllabusDto fromDoc = service.parse(doc);
        ParsedSyllabusDto fromDocx = service.parse(cloudMl());

        assertEquals(List.of(6, 9, 7, 9, 12, 12), fromDoc.units().stream().map(u -> u.topics().size()).toList());
        assertEquals(fromDocx.units(), fromDoc.units());
        assertEquals("3-0-2", fromDoc.documentLtp());
        assertEquals("Cloud-Based Machine Learning", fromDoc.documentTitle());
    }

    /** The document and the allocation sheet must agree; a difference is shown, never silently accepted. */
    @Test
    void namesEveryDifferenceBetweenTheDocumentAndTheAllocationSheet() throws Exception {
        when(courseRepository.findById(7L)).thenReturn(Optional.of(
                new Course("CS3201", "Cloud Based Machine Learning", "THEORY", 4, "3-1-2", 3, 1, 2, null)));
        List<String> mismatches = service.parse(cloudMl(), 7L).mismatches();
        assertEquals(2, mismatches.size(), mismatches.toString());
        assertTrue(mismatches.get(0).contains("3-0-2") && mismatches.get(0).contains("3-1-2"), mismatches.get(0));
        assertTrue(mismatches.get(1).contains("\"Cloud-Based Machine Learning\"")
                && mismatches.get(1).contains("\"Cloud Based Machine Learning\""), mismatches.get(1));

        when(courseRepository.findById(8L)).thenReturn(Optional.of(
                new Course("CS3201", "Cloud-Based Machine Learning", "THEORY", 4, "3-0-2", 3, 0, 2, null)));
        assertEquals(List.of(), service.parse(cloudMl(), 8L).mismatches());
    }

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
    void keepsDocumentOrderInventsNoWeeksAndDropsTextbookEntries() {
        ParsedSyllabusDto parsed = service.parseText(SAMPLE, "cs501.pdf");

        List<ParsedSyllabusDto.Topic> allTopics = parsed.units().stream()
                .flatMap(unit -> unit.topics().stream())
                .toList();

        // A course description states no week per topic, so none is made up (pacing uses the
        // timetable instead). Order is the document's.
        assertTrue(allTopics.stream().allMatch(topic -> topic.targetWeek() == 0));
        assertEquals("Introduction to networks and uses", allTopics.get(0).topicName());

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
        verify(syllabusTopicRepository, never()).deleteByCourseId(anyLong());
    }

    private static SyllabusTopic topic(Course course, long id, int unit, String name, int order) {
        SyllabusTopic t = new SyllabusTopic(course, unit, "Unit " + unit, name, 0, order);
        t.setId(id);
        return t;
    }

    private static ParsedSyllabusDto doc(List<String> unit1, List<String> unit2) {
        return new ParsedSyllabusDto("v2.docx", 1, List.of(
                new ParsedSyllabusDto.Unit(1, "Unit 1", unit1.stream().map(n -> new ParsedSyllabusDto.Topic(n, 0)).toList()),
                new ParsedSyllabusDto.Unit(2, "Unit 2", unit2.stream().map(n -> new ParsedSyllabusDto.Topic(n, 0)).toList())));
    }

    /**
     * Re-uploading a corrected syllabus must not erase which topics the logged classes covered
     * (session_topic_log cascades on delete). Same name, or same place in an unchanged-size unit,
     * keeps the topic; a topic with classes logged against it is never dropped silently.
     */
    @Test
    void reuploadingKeepsLoggedTopicsAndRefusesToDropOnesWithClasses() {
        Course course = new Course("CS501", "Networks", "THEORY");
        course.setId(7L);
        when(courseRepository.findById(7L)).thenReturn(Optional.of(course));
        when(syllabusTopicRepository.findByCourseIdOrderByLectureOrderAsc(7L)).thenReturn(List.of(
                topic(course, 11, 1, "OSI model", 1), topic(course, 12, 1, "TCP/IP stak", 2),
                topic(course, 13, 2, "Error detection", 3), topic(course, 14, 2, "Framing", 4)));
        when(syllabusTopicRepository.findCoveredTopicIds(7L)).thenReturn(java.util.Set.of(12L, 13L));
        when(syllabusTopicRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        // Typo fixed in unit 1 (same size: kept by place); unit 2 gains a topic and drops an untaught one.
        List<SyllabusTopicDto> saved = service.apply(7L, doc(
                List.of("OSI model", "TCP/IP stack"), List.of("Sliding window", "Error detection")));
        assertEquals(List.of("OSI model", "TCP/IP stack", "Sliding window", "Error detection"),
                saved.stream().map(SyllabusTopicDto::topicName).toList());
        assertEquals(11L, saved.get(0).id());
        assertEquals(12L, saved.get(1).id());
        assertEquals(null, saved.get(2).id());
        assertEquals(13L, saved.get(3).id());
        assertEquals(List.of(1, 2, 3, 4), saved.stream().map(SyllabusTopicDto::lectureOrder).toList());
        verify(syllabusTopicRepository).deleteAll(deleted.capture());
        assertEquals(List.of(14L), java.util.stream.StreamSupport.stream(deleted.getValue().spliterator(), false)
                .map(SyllabusTopic::getId).toList(), "only the untaught dropped topic is deleted");
        verify(syllabusTopicRepository, never()).deleteByCourseId(anyLong());

        // Dropping "Error detection", which classes were logged against, is refused by name.
        ValidationException refused = assertThrows(ValidationException.class, () -> service.apply(7L, doc(
                List.of("OSI model", "TCP/IP stack"), List.of("Sliding window", "Framing", "Flow control"))));
        assertTrue(refused.getMessage().contains("Error detection"), refused.getMessage());
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
