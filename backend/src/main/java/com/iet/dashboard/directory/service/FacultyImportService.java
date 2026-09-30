package com.iet.dashboard.directory.service;

import com.iet.dashboard.directory.web.ImportResultDto;
import org.springframework.web.multipart.MultipartFile;

public interface FacultyImportService {

    ImportResultDto importFacultyAndAllocations(MultipartFile file);

    byte[] generateTemplateCsv();
}
