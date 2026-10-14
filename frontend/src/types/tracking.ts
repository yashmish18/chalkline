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

export type FacultyPerformanceStatus = 'NOT_STARTED' | 'NO_TIMETABLE' | 'NOT_LOGGING' | 'BEHIND' | 'ON_TRACK' | 'NO_DATA';

export interface OverviewSummaryDto {
  term: {
    name: string | null;
    startDate: string | null;
    endDate: string | null;
    currentWeek: number | null;
    totalWeeks: number | null;
    started: boolean;
  };
  kpis: {
    /** null when no section has a syllabus to measure against */
    averageCoveragePercentage: number | null;
    /** null when no class has been logged yet */
    averageConductRatePercentage: number | null;
    coursesBehindPacing: number;
    needsAttentionCount: number;
    offeringsWithoutSyllabus: number;
    facultyNotLogging: number;
    /** classes logged / classes the timetable scheduled so far; null before anything is due */
    loggingRatePercentage: number | null;
    facultyWithoutTimetable: number;
    totalStudents: number;
  };
  /** Furthest behind the planned syllabus position first; sections without a syllabus last. */
  worstFirstCoverage: Array<{
    offeringId: number;
    courseCode: string;
    courseName: string;
    semester: number;
    section: string;
    facultyName: string;
    coveredTopics: number;
    totalTopics: number;
    coveragePercentage: number;
    expectedPercentage: number;
    pacingStatus: 'BEHIND' | 'ON_TRACK' | 'AHEAD';
    topicDelta: number;
    syllabusConfigured: boolean;
    lastSessionDate: string | null;
    yearOfStudy?: number;
    studentCount?: number;
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
  facultyPerformance: Array<{
    facultyId: number;
    facultyName: string;
    offerings: number;
    weeklyHours: number;
    pblHours: number;
    sessionsHeld: number;
    sessionsCancelled: number;
    lastSessionDate: string | null;
    daysSinceLastLog: number | null;
    offeringsBehind: number;
    averageCoverage: number | null;
    scheduledSoFar: number;
    loggingRate: number | null;
    status: FacultyPerformanceStatus;
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

export interface IrregularityAuditDto {
  totalIssues: number;
  criticalCount: number;
  warningCount: number;
  items: Array<{
    offeringId: number;
    courseCode: string;
    courseName: string;
    section: string;
    ltp: string;
    issueType: string;
    severity: 'CRITICAL' | 'WARNING' | 'INFO';
    description: string;
    assignedFaculty: string;
  }>;
}

