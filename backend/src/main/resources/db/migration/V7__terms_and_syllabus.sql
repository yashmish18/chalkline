-- V7__terms_and_syllabus.sql
-- Academic Terms and Syllabus as Data (Units and Topics with Target Weeks)

CREATE TABLE academic_term (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL,      -- e.g. "Odd 2026-27"
    academic_year VARCHAR(20) NOT NULL,      -- "2026-2027"
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Add credits to course catalog
ALTER TABLE course ADD COLUMN IF NOT EXISTS credits INT NOT NULL DEFAULT 3;

-- Link offering to term
ALTER TABLE course_offering ADD COLUMN IF NOT EXISTS term_id BIGINT REFERENCES academic_term(id) ON DELETE CASCADE;

-- Insert active term
INSERT INTO academic_term (name, academic_year, start_date, end_date, is_active)
VALUES ('Odd 2026-27', '2026-2027', '2026-07-15', '2026-12-15', TRUE);

-- Associate existing offerings with the active term
UPDATE course_offering SET term_id = (SELECT id FROM academic_term WHERE is_active = TRUE LIMIT 1);
ALTER TABLE course_offering ALTER COLUMN term_id SET NOT NULL;

-- Syllabus Topics Table
CREATE TABLE syllabus_topic (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL REFERENCES course(id) ON DELETE CASCADE,
    unit_number INT NOT NULL CHECK (unit_number BETWEEN 1 AND 5),
    unit_title VARCHAR(255) NOT NULL,
    topic_name VARCHAR(255) NOT NULL,
    target_week INT NOT NULL CHECK (target_week BETWEEN 1 AND 20),
    lecture_order INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_course_lecture_order UNIQUE (course_id, lecture_order)
);

CREATE INDEX idx_syllabus_course ON syllabus_topic(course_id);
CREATE INDEX idx_syllabus_unit ON syllabus_topic(course_id, unit_number);

-- Seed Syllabus Topics for CS501 (Computer Networks)
INSERT INTO syllabus_topic (course_id, unit_number, unit_title, topic_name, target_week, lecture_order)
VALUES
    (1, 1, 'Unit 1: Fundamentals of Networking', 'Introduction to Computer Networks & Network Topologies', 1, 1),
    (1, 1, 'Unit 1: Fundamentals of Networking', 'OSI 7-Layer Reference Model & Responsibilities', 1, 2),
    (1, 1, 'Unit 1: Fundamentals of Networking', 'TCP/IP Protocol Suite & Architectural Comparison', 2, 3),
    (1, 2, 'Unit 2: Physical & Data Link Layer', 'Transmission Media: Guided vs Unguided Media', 3, 4),
    (1, 2, 'Unit 2: Physical & Data Link Layer', 'Data Link Framing, Flow Control & Stop-and-Wait ARQ', 3, 5),
    (1, 2, 'Unit 2: Physical & Data Link Layer', 'Error Detection: Parity, Checksum & Cyclic Redundancy Check (CRC)', 4, 6),
    (1, 3, 'Unit 3: Medium Access & Ethernet', 'MAC Sublayer: Pure & Slotted ALOHA Protocols', 5, 7),
    (1, 3, 'Unit 3: Medium Access & Ethernet', 'CSMA, CSMA/CD and CSMA/CA Protocols', 5, 8),
    (1, 3, 'Unit 3: Medium Access & Ethernet', 'Standard Ethernet Architecture and Switching Mechanisms', 6, 9),
    (1, 4, 'Unit 4: Network Layer & Routing', 'IPv4 Addressing, Subnet Masks and Classless Inter-Domain Routing (CIDR)', 7, 10),
    (1, 4, 'Unit 4: Network Layer & Routing', 'Distance Vector Routing Algorithm & Count to Infinity Problem', 8, 11),
    (1, 4, 'Unit 4: Network Layer & Routing', 'Link State Routing Algorithm (Dijkstra) & OSPF Overview', 9, 12),
    (1, 5, 'Unit 5: Transport & Application Layer', 'Transport Layer: TCP Connection Management & 3-Way Handshake', 10, 13),
    (1, 5, 'Unit 5: Transport & Application Layer', 'TCP Congestion Control: AIMD, Slow Start & Fast Recovery', 11, 14),
    (1, 5, 'Unit 5: Transport & Application Layer', 'Application Protocols: DNS Hierarchy, HTTP/1.1 vs HTTP/2 and SMTP', 12, 15);

-- Seed Syllabus Topics for CS502 (Database Management Systems)
INSERT INTO syllabus_topic (course_id, unit_number, unit_title, topic_name, target_week, lecture_order)
VALUES
    (2, 1, 'Unit 1: Database Architecture & ER Model', 'Database System Concepts, 3-Schema Architecture & Data Independence', 1, 1),
    (2, 1, 'Unit 1: Database Architecture & ER Model', 'Entity-Relationship (ER) Modeling: Entities, Attributes & Cardinality', 1, 2),
    (2, 1, 'Unit 1: Database Architecture & ER Model', 'Enhanced ER: Specialization, Generalization & Aggregation', 2, 3),
    (2, 2, 'Unit 2: Relational Model & SQL', 'Relational Model Constraints & Relational Algebra Operations', 3, 4),
    (2, 2, 'Unit 2: Relational Model & SQL', 'SQL DDL, DML and Complex Nested Subqueries', 3, 5),
    (2, 2, 'Unit 2: Relational Model & SQL', 'Join Algorithms: Nested Loop, Hash Join & Sort-Merge Join', 4, 6),
    (2, 3, 'Unit 3: Normalization & Functional Dependencies', 'Functional Dependencies, Inference Rules & Closure Computation', 5, 7),
    (2, 3, 'Unit 3: Normalization & Functional Dependencies', 'Normal Forms: 1NF, 2NF and 3NF Decomposition', 5, 8),
    (2, 3, 'Unit 3: Normalization & Functional Dependencies', 'Boyce-Codd Normal Form (BCNF) & Lossless Join Property', 6, 9),
    (2, 4, 'Unit 4: Transaction & Concurrency Control', 'Transaction Concepts, ACID Properties & State Transitions', 7, 10),
    (2, 4, 'Unit 4: Transaction & Concurrency Control', 'Serializability: Conflict vs View Serializability', 8, 11),
    (2, 4, 'Unit 4: Transaction & Concurrency Control', 'Two-Phase Locking (2PL) Protocol & Deadlock Prevention/Handling', 9, 12),
    (2, 5, 'Unit 5: Indexing & Query Processing', 'File Organization, Dense vs Sparse Indexing', 10, 13),
    (2, 5, 'Unit 5: Indexing & Query Processing', 'B-Tree and B+ Tree Indexing Structures', 11, 14),
    (2, 5, 'Unit 5: Indexing & Query Processing', 'Query Evaluation, Cost Estimation and Recovery Concepts', 12, 15);
