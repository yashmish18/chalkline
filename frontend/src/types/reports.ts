/** Mirrors com.iet.dashboard.tracking.web.ReportDtos. */

export interface ReportMeta {
  termName: string | null;
  termStart: string | null;
  termEnd: string | null;
  currentWeek: number | null;
  totalWeeks: number | null;
  requiredAssessments: number;
  generatedAt: string;
}

export type ScoreStatus = 'OK' | 'WATCH' | 'CRITICAL' | 'NO_DATA';

export interface ScoreLine {
  metric: string;
  target: string;
  actual: string;
  status: ScoreStatus;
  note: string;
}

export interface CriticalCase {
  ref: string;
  severity: 'CRITICAL' | 'WARNING';
  category: string;
  entity: string;
  facultyId: number | null;
  facultyName: string;
  studentsAffected: number;
  description: string;
  requiredAction: string;
}

export type MismatchType = 'MISSING_OWNER' | 'HOURS_SHORT' | 'OVER_ALLOCATION' | 'BATCH_COVERAGE_GAP';

export interface Mismatch {
  offeringId: number;
  courseCode: string;
  courseName: string;
  yearOfStudy: number;
  semester: number;
  section: string;
  ltp: string;
  component: string;
  plannedHours: number;
  assignedHours: number;
  type: MismatchType;
  detail: string;
  faculty: string;
}

export type RemedialStatus = 'NOT_REQUIRED' | 'REQUIRED' | 'FILED' | 'COMPLETED';

export interface SectionPerformance {
  offeringId: number;
  courseCode: string;
  courseName: string;
  yearOfStudy: number;
  semester: number;
  section: string;
  students: number;
  components: string;
  faculty: string;
  coveredTopics: number;
  totalTopics: number;
  coveragePct: number;
  pacingStatus: 'AHEAD' | 'ON_TRACK' | 'BEHIND' | 'NO_SYLLABUS';
  topicDelta: number;
  held: number;
  cancelled: number;
  scheduled: number;
  assessmentsHeld: number;
  latestAveragePct: number | null;
  remedialStatus: RemedialStatus;
}

export interface TeachingLine {
  offeringId: number;
  yearOfStudy: number;
  courseCode: string;
  courseName: string;
  section: string;
  component: string;
  weeklyHours: number;
  students: number;
  held: number;
  scheduled: number;
  coveragePct: number;
  pacingStatus: string;
  assessmentsHeld: number;
  remedialStatus: RemedialStatus;
}

export interface FacultyBreakdown {
  facultyId: number;
  name: string;
  designation: string;
  department: string;
  lectureHours: number;
  tutorialHours: number;
  practicalHours: number;
  pblHours: number;
  totalHours: number;
  studentsTaught: number;
  years: number[];
  held: number;
  cancelled: number;
  scheduled: number;
  loggingRate: number | null;
  status: string;
  lines: TeachingLine[];
}

export interface AssessmentProgress {
  index: number;
  sectionsDone: number;
  sectionsTotal: number;
  averagePct: number | null;
}

export interface RemedialPlanLine {
  id: number;
  courseCode: string;
  section: string;
  facultyName: string;
  reason: string;
  action: string;
  extraSessions: number;
  targetDate: string;
  status: string;
}

export interface DataSources {
  faculty: number;
  allocations: number;
  sections: number;
  sessionsLogged: number;
  timetableSlots: number;
  syllabusTopics: number;
  assessments: number;
  remedialPlans: number;
}

export interface SemesterReport {
  meta: ReportMeta;
  scorecard: ScoreLine[];
  criticalCases: CriticalCase[];
  mismatches: Mismatch[];
  sections: SectionPerformance[];
  faculty: FacultyBreakdown[];
  assessments: AssessmentProgress[];
  remedialPlans: RemedialPlanLine[];
  dataSources: DataSources;
}

export interface LogEntry {
  date: string;
  courseCode: string;
  section: string;
  subSection: string;
  sessionType: string;
  status: string;
  hours: number;
  topics: string;
  note: string | null;
}

export interface FacultyReport {
  meta: ReportMeta;
  summary: FacultyBreakdown;
  cases: CriticalCase[];
  recentLogs: LogEntry[];
}

export interface CourseReport {
  meta: ReportMeta;
  courseCode: string;
  courseName: string;
  ltp: string;
  totalStudents: number;
  sections: SectionPerformance[];
  mismatches: Mismatch[];
}

export const YEAR_LABEL: Record<number, string> = { 1: 'First year', 2: 'Second year', 3: 'Third year', 4: 'Fourth year' };

export const REMEDIAL_LABEL: Record<RemedialStatus, string> = {
  NOT_REQUIRED: 'Not needed',
  REQUIRED: 'Plan needed',
  FILED: 'Plan filed',
  COMPLETED: 'Completed',
};

export const MISMATCH_LABEL: Record<MismatchType, string> = {
  MISSING_OWNER: 'No owner',
  HOURS_SHORT: 'Hours short',
  OVER_ALLOCATION: 'Over-allocated',
  BATCH_COVERAGE_GAP: 'Batch gap',
};
