package com.iet.dashboard.submissions.service;

import com.iet.dashboard.submissions.web.ReviewRequest;
import com.iet.dashboard.submissions.web.SubmissionDto;
import com.iet.dashboard.submissions.web.SubmissionFileDto;
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
}
