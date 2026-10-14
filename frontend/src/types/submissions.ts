export type SubmissionStatus = 'PENDING' | 'SUBMITTED' | 'LATE' | 'RETURNED' | 'ACCEPTED';

export interface SubmissionFileDto {
  id: number;
  submissionId: number;
  version: number;
  originalName: string;
  contentType: string;
  sizeBytes: number;
  sha256: string;
  uploadedAt: string;
}

export interface SubmissionDto {
  id: number;
  deadlineAssignmentId: number;
  status: SubmissionStatus;
  submittedAt: string | null;
  reviewerComment: string | null;
  updatedAt: string;
  latestFile: SubmissionFileDto | null;
  totalVersions: number;
  files: SubmissionFileDto[];
}

export interface ReviewRequest {
  action: 'ACCEPT' | 'RETURN';
  comment?: string;
}

/** Flat coordinator-facing row: one deadline assignment with its submission status. */
export interface SubmissionOverviewDto {
  submissionId: number | null;
  assignmentId: number;
  deadlineId: number;
  deadlineTitle: string;
  deadlineType: string;
  dueAt: string;
  isOverdue: boolean;
  facultyId: number;
  facultyName: string;
  status: SubmissionStatus;
  submittedAt: string | null;
  totalVersions: number;
  latestFileId: number | null;
  latestFileName: string | null;
  reviewerComment: string | null;
}
