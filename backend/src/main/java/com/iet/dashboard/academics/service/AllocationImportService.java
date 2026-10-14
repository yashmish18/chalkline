package com.iet.dashboard.academics.service;

import com.iet.dashboard.academics.web.AllocationImportResultDto;
import org.springframework.web.multipart.MultipartFile;

/** Imports the semester's teaching allocations: one row per (faculty, course, section, component, batch). */
public interface AllocationImportService {

    AllocationImportResultDto importAllocations(MultipartFile file);

    byte[] templateCsv();
}
