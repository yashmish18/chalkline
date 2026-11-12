package com.iet.dashboard.architecture;

import com.iet.dashboard.academics.domain.Course;
import com.iet.dashboard.academics.repo.CourseRepository;
import com.iet.dashboard.academics.repo.SyllabusTopicRepository;
import com.iet.dashboard.academics.service.SyllabusImportServiceImpl;
import com.iet.dashboard.academics.web.ParsedSyllabusDto;
import com.iet.dashboard.academics.web.SyllabusTopicDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every other test builds its schema from the entities on H2, so the real Flyway migrations never
 * ran in a test and entity/migration drift (a 255-character column the code writes 500 into) only
 * showed up in production. Here the migrations build a real PostgreSQL 16 schema, Hibernate
 * validates every entity against it, and SQL that only Postgres runs (native queries, the unique
 * lecture order) is exercised.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class PostgresMigrationContractTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired CourseRepository courses;
    @Autowired SyllabusTopicRepository topics;

    private static ParsedSyllabusDto doc(String... unitOne) {
        return new ParsedSyllabusDto("cd.docx", 1, List.of(new ParsedSyllabusDto.Unit(1, "Introduction",
                java.util.Arrays.stream(unitOne).map(n -> new ParsedSyllabusDto.Topic(n, 0)).toList())));
    }

    /** Reaching this test at all means every migration applied and every entity validated. */
    @Test
    void migrationsBuildTheSchemaTheEntitiesExpectAndSyllabusReuploadKeepsTopicIds() {
        Course course = courses.save(new Course("CS3201", "Cloud-Based Machine Learning", "THEORY", 4, "3-0-2", 3, 0, 2, null));
        var service = new SyllabusImportServiceImpl(courses, topics);
        String longTopic = "A".repeat(480);

        List<SyllabusTopicDto> first = service.apply(course.getId(), doc("Why cloud for AI/ML?", "SageMaker", longTopic));
        // Typo fix + reorder of a same-size unit: renumbering must not trip uq_course_lecture_order.
        List<SyllabusTopicDto> second = service.apply(course.getId(), doc("SageMaker", "Why cloud computing for AI/ML?", longTopic));

        assertThat(second).extracting(SyllabusTopicDto::topicName)
                .containsExactly("SageMaker", "Why cloud computing for AI/ML?", longTopic);
        assertThat(second.get(0).id()).isEqualTo(first.get(1).id());   // same words: same topic, so its logs survive
        assertThat(second.get(2).id()).isEqualTo(first.get(2).id());
        assertThat(topics.findCoveredTopicIds(course.getId())).isEmpty();
    }
}
