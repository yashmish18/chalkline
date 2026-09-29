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

export interface OverviewSummaryDto {
  kpis: {
    averageCoveragePercentage: number;
    averageConductRatePercentage: number;
    coursesBehindPacing: number;
    needsAttentionCount: number;
  };
  worstFirstCoverage: Array<{
    offeringId: number;
    courseCode: string;
    courseName: string;
    section: string;
    facultyName: string;
    coveredTopics: number;
    totalTopics: number;
    coveragePercentage: number;
    expectedPercentage: number;
    pacingStatus: 'BEHIND' | 'ON_TRACK' | 'AHEAD';
    topicDelta: number;
  }>;
  conductTrajectory: Array<{
    weekNumber: number;
    conductRate: number;
    heldCount: number;
    cancelledCount: number;
  }>;
  needsAttention: Array<{
    id: string;
    type: string;
    severity: 'CRITICAL' | 'WARNING' | 'INFO';
    title: string;
    subtitle: string;
    facultyId: number | null;
    facultyName: string;
    actionLabel: string;
  }>;
}

export interface ComplianceMatrixDto {
  checkpoints: Array<{
    key: string;
    name: string;
    description: string;
  }>;
  rows: Array<{
    offeringId: number;
    courseCode: string;
    courseName: string;
    section: string;
    facultyId: number | null;
    facultyName: string;
    cells: Array<{
      checkpointKey: string;
      status: 'DONE' | 'PENDING' | 'OVERDUE';
      note: string;
      completedDate: string | null;
    }>;
  }>;
}
