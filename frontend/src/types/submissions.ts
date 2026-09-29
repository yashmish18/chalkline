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
