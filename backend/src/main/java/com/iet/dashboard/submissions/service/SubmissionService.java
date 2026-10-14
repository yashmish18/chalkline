package com.iet.dashboard.submissions.service;

import com.iet.dashboard.submissions.web.ReviewRequest;
import com.iet.dashboard.submissions.web.SubmissionDto;
import com.iet.dashboard.submissions.web.SubmissionFileDto;
import com.iet.dashboard.submissions.web.SubmissionOverviewDto;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SubmissionService {

    SubmissionDto getSubmissionByAssignmentId(Long assignmentId);

    SubmissionDto submitFile(Long assignmentId, MultipartFile file);

    SubmissionDto reviewSubmission(Long submissionId, ReviewRequest request);

    Resource downloadFile(Long fileId);

    SubmissionFileDto getFileMetadata(Long fileId);

    List<SubmissionFileDto> getFileHistory(Long submissionId);

    List<SubmissionDto> getSubmissionsForDeadline(Long deadlineId);

    /**
     * Status of every assignment of a deadline (PENDING when nothing was submitted), for reporting.
     * Exposes no files or comments, so it carries no role check; callers aggregate before returning data.
     */
    java.util.Map<Long, String> getSubmissionStatusesForDeadline(Long deadlineId);

    /**
     * Flat, coordinator-facing view of every deadline assignment across the department, so the
     * board can render in one request. Optionally filtered by submission status (e.g. SUBMITTED).
     */
    List<SubmissionOverviewDto> getAllSubmissions(String statusFilter);
}
