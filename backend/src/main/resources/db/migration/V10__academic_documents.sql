-- V10__academic_documents.sql
-- Support uploading and downloading official institutional documents: Master Timetable, Academic Calendar, and Course Syllabus/Description (PDF, Word DOC/DOCX)

CREATE TABLE academic_document (
    id BIGSERIAL PRIMARY KEY,
    category VARCHAR(50) NOT NULL, -- 'MASTER_TIMETABLE', 'ACADEMIC_CALENDAR', 'COURSE_SYLLABUS'
    title VARCHAR(255) NOT NULL,
    description TEXT,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_key VARCHAR(255) NOT NULL UNIQUE,
    term_id BIGINT REFERENCES academic_term(id) ON DELETE SET NULL,
    course_id BIGINT REFERENCES course(id) ON DELETE SET NULL,
    uploaded_by_faculty_id BIGINT REFERENCES faculty(id) ON DELETE SET NULL,
    uploaded_by_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_academic_doc_category ON academic_document(category);
CREATE INDEX idx_academic_doc_term ON academic_document(term_id);
CREATE INDEX idx_academic_doc_course ON academic_document(course_id);
