export interface DepartmentMetricsDto {
  totalAssignments: number;
  totalSubmissions: number;
  submissionRate: number;
  onTimeSubmissions: number;
  onTimeRate: number;
  overdueCount: number;
  criticalFacultyCount: number;
  totalFaculty: number;
}

export interface DeadlineTrackingRowDto {
  deadlineId: number;
  title: string;
  type: string;
  dueAt: string;
  totalAssigned: number;
  submittedCount: number;
  acceptedCount: number;
  overdueCount: number;
  complianceRate: number;
}

export interface FacultyComplianceRowDto {
  facultyId: number;
  facultyName: string;
  designation: string;
  department: string;
  totalAssigned: number;
  submittedCount: number;
  acceptedCount: number;
  pendingCount: number;
  complianceRate: number;
  complianceStatus: 'CRITICAL' | 'WATCH' | 'GOOD';
}

export interface AuditLogDto {
  id: number;
  userId: number | null;
  userName: string;
  action: string;
  targetEntity: string;
  targetId: number | null;
  details: string;
  createdAt: string;
}
