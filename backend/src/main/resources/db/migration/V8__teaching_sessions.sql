-- V8__teaching_sessions.sql
-- Daily Faculty Teaching Sessions and Syllabus Coverage Topic Logs

CREATE TABLE teaching_session (
    id BIGSERIAL PRIMARY KEY,
    offering_id BIGINT NOT NULL REFERENCES course_offering(id) ON DELETE CASCADE,
    faculty_id BIGINT NOT NULL REFERENCES faculty(id) ON DELETE CASCADE,
    session_date DATE NOT NULL,
    session_type VARCHAR(50) NOT NULL DEFAULT 'LECTURE', -- LECTURE, LAB, TUTORIAL
    status VARCHAR(50) NOT NULL DEFAULT 'HELD',          -- HELD, CANCELLED, SWAPPED
    note TEXT,
    reason_not_held TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE session_topic_log (
    session_id BIGINT NOT NULL REFERENCES teaching_session(id) ON DELETE CASCADE,
    topic_id BIGINT NOT NULL REFERENCES syllabus_topic(id) ON DELETE CASCADE,
    PRIMARY KEY (session_id, topic_id)
);

CREATE INDEX idx_teaching_session_offering_date ON teaching_session(offering_id, session_date);
CREATE INDEX idx_teaching_session_faculty_date ON teaching_session(faculty_id, session_date);
CREATE INDEX idx_session_topic_topic ON session_topic_log(topic_id);

-- Seed some historical sessions for CS501 and CS502 (Dr. Amit Verma) to give immediate rich data
INSERT INTO teaching_session (id, offering_id, faculty_id, session_date, session_type, status, note)
VALUES
    (1, 1, 2, '2026-08-03', 'LECTURE', 'HELD', 'Course introduction and basics'),
    (2, 1, 2, '2026-08-05', 'LECTURE', 'HELD', 'OSI architecture overview'),
    (3, 1, 2, '2026-08-10', 'LECTURE', 'HELD', 'TCP/IP comparisons'),
    (4, 1, 2, '2026-08-17', 'LECTURE', 'HELD', 'Physical media properties'),
    (5, 1, 2, '2026-08-24', 'LECTURE', 'HELD', 'Data link framing methods'),
    (6, 1, 2, '2026-08-31', 'LECTURE', 'HELD', 'CRC polynomial calculations'),
    (7, 1, 2, '2026-09-07', 'LECTURE', 'HELD', 'ALOHA throughput analysis'),
    (8, 1, 2, '2026-09-14', 'LECTURE', 'HELD', 'CSMA/CD collision detection'),
    (9, 1, 2, '2026-09-21', 'LECTURE', 'HELD', 'Gigabit Ethernet switching'),
    (10, 1, 2, '2026-09-28', 'LECTURE', 'HELD', 'IPv4 addressing and subnetting');

SELECT setval('teaching_session_id_seq', 10);

INSERT INTO session_topic_log (session_id, topic_id)
VALUES
    (1, 1), (2, 2), (3, 3), (4, 4), (5, 5),
    (6, 6), (7, 7), (8, 8), (9, 9), (10, 10);
