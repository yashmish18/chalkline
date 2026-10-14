export interface SkillDto {
  id: number;
  name: string;
  category: string;
}

/** Department reference data for filters and pickers. */
export interface DepartmentDto {
  id: number;
  code: string;
  name: string;
}

export interface FacultyDto {
  id: number;
  userId: number;
  name: string;
  designation: string;
  qualifications: string;
  department: string;
  skills: SkillDto[];
  pblHours?: number;
  /** The account email this faculty member signs in with; "" when unresolved. */
  email?: string;
}

/** One teaching assignment owned by a faculty member. */
export interface FacultyCourseAssignmentDto {
  assignmentId: number;
  offeringId: number;
  courseCode: string;
  courseName: string;
  category?: string;
  ltp?: string;
  type?: string;
  academicYear: string;
  yearOfStudy: number;
  semester: number;
  section: string;
  component: string;
  subSection?: string;
  contactHours: number;
}

/** One deadline this faculty member was targeted for, with submission state. */
export interface FacultySubmissionRowDto {
  assignmentId: number;
  deadlineTitle: string;
  deadlineType: string;
  dueAt: string;
  status: string;
  overdue: boolean;
  totalVersions: number;
  submittedAt: string | null;
}

export interface FacultyLtpLoadDto {
  facultyId: number;
  facultyName: string;
  lectureHours: number;
  tutorialHours: number;
  practicalHours: number;
  pblHours?: number;
  totalHours: number;
  assignmentCount: number;
}

export interface FacultyComplianceDto {
  facultyId: number;
  facultyName: string;
  designation: string;
  department: string;
  totalAssigned: number;
  submittedCount: number;
  acceptedCount: number;
  pendingCount: number;
  complianceRate: number;
  complianceStatus: string;
}

/** Assembled coordinator-facing faculty profile. */
export interface FacultyProfileDto {
  id: number;
  name: string;
  email: string | null;
  designation: string;
  qualification: string;
  department: string;
  skills: SkillDto[];
  courses: FacultyCourseAssignmentDto[];
  load: FacultyLtpLoadDto;
  compliance: FacultyComplianceDto | null;
  submissions: FacultySubmissionRowDto[];
}
