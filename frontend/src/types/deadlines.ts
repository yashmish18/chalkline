export type DeadlineType = 'COURSE_FILE' | 'EVALUATION' | 'MARKS_UPLOAD' | 'SYLLABUS_REPORT' | 'OTHER';

export interface DeadlineDto {
  id: number;
  title: string;
  description: string;
  type: DeadlineType;
  dueAt: string;
  createdBy: number;
  createdAt: string;
  isOverdue: boolean;
  totalAssignmentsCount: number;
}

export interface DeadlineAssignmentDto {
  id: number;
  deadlineId: number;
  title: string;
  description: string;
  type: DeadlineType;
  dueAt: string;
  facultyId: number;
  facultyName: string;
  offeringId: number | null;
  isOverdue: boolean;
}
