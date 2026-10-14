package com.iet.dashboard.academics.service;

import com.iet.dashboard.academics.web.TimetableImportResultDto;
import org.springframework.web.multipart.MultipartFile;

/** Imports the weekly timetable and maps every slot onto a faculty member's allocation. */
public interface TimetableImportService {

    /**
     * @param onlyFacultyId null for the coordinator's master timetable (replaces every slot);
     *                      a faculty id for a personal upload (every row is theirs, and only their
     *                      slots are replaced)
     */
    TimetableImportResultDto importTimetable(MultipartFile file, Long onlyFacultyId);

    byte[] templateCsv();
}
