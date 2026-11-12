-- Holidays the coordinator enters for a semester. A holiday's timetabled classes are not owed:
-- they are never "not logged", and pace does not expect them.
CREATE TABLE term_holiday (
    term_id BIGINT NOT NULL REFERENCES academic_term(id) ON DELETE CASCADE,
    holiday_date DATE NOT NULL,
    name VARCHAR(200) NOT NULL,
    PRIMARY KEY (term_id, holiday_date)
);
