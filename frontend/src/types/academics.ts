export interface CourseDto {
  id: number;
  code: string;
  name: string;
  /** Course nature — always THEORY; practical work is expressed through the LTP plan. */
  type: string;
  /** CORE, DEPARTMENT_ELECTIVE or OPEN_ELECTIVE. */
  category?: string;
  credits?: number;
  ltp?: string;
  lectureHours?: number;
  tutorialHours?: number;
  practicalHours?: number;
  description?: string;
}

export interface CourseOfferingDto {
  id: number;
  course: CourseDto;
  academicYear: string;
  yearOfStudy: number;
  semester: number;
  section: string;
  termId?: number;
  termName?: string;
  /** Students enrolled in the section; 0 when not yet known. */
  studentCount: number;
}

export interface TeachingAssignmentDto {
  id: number;
  offering: CourseOfferingDto;
  facultyId: number;
  facultyName: string;
  component: 'ALL' | 'THEORY' | 'LECTURE' | 'TUTORIAL' | 'PRACTICAL' | 'LAB';
  subSection?: string;
  /** Weekly contact hours of this component that the assigned faculty owns. */
  contactHours: number;
  /** Students this allocation teaches: the batch size, else the section strength. */
  studentCount: number;
}

/** Computed weekly LTP teaching load per faculty — derived, never typed. */
export interface FacultyLtpLoadDto {
  facultyId: number;
  facultyName: string;
  lectureHours: number;
  tutorialHours: number;
  practicalHours: number;
  pblHours?: number;
  totalHours: number;
  assignmentCount: number;
  /** The same hours, one line per course / section / component / batch. */
  lines?: FacultyLoadLine[];
}

export interface FacultyLoadLine {
  courseCode: string;
  courseName: string;
  ltp: string;
  semester: number;
  section: string;
  component: string;
  batch: string;
  students: number | null;
  hours: number;
}

export interface SyllabusTopicDto {
  id: number;
  courseId: number;
  unitNumber: number;
  unitTitle: string;
  topicName: string;
  targetWeek: number;
  lectureOrder: number;
}

export interface UnitOptionDto {
  unitNumber: number;
  unitTitle: string;
  topics: SyllabusTopicDto[];
}

/** Preview returned by the syllabus PDF parser; edited client-side, then confirmed. */
export interface ParsedSyllabusTopic {
  topicName: string;
  targetWeek: number;
}

export interface ParsedSyllabusUnit {
  unitNumber: number;
  unitTitle: string;
  topics: ParsedSyllabusTopic[];
}

export interface ParsedSyllabusDto {
  sourceFileName: string;
  extractedCharacters: number;
  units: ParsedSyllabusUnit[];
  /** As the document writes them, or null when it doesn't say. */
  documentTitle?: string | null;
  documentLtp?: string | null;
  /** Every difference between the document and the allocation sheet, in words. */
  mismatches?: string[];
}

export interface TimetableSlotDto {
  id: number;
  offeringId: number;
  courseCode: string;
  courseName: string;
  courseDescription: string;
  ltp: string;
  section: string;
  subSection: string;
  component: string;
  dayOfWeek: string;
  startTime: string;
  endTime: string;
  room: string;
  facultyId: number;
}

export interface TodayClassDto {
  offeringId: number;
  courseId?: number;
  courseCode: string;
  courseName: string;
  courseDescription?: string;
  ltp?: string;
  section: string;
  subSection?: string;
  component: string;
  timeSlot: string;
  room?: string;
  currentUnitNumber?: number;
  nextTopicId: number | null;
  nextTopic: string;
  unitTitle: string;
  targetWeek: number;
  totalPlanned: number;
  completedSoFar: number;
  isLogged: boolean;
  availableUnits?: UnitOptionDto[];
  /** true when the class comes from the uploaded timetable */
  scheduled?: boolean;
  /** One past the highest lecture number already logged for this section/batch and component. */
  nextLectureNumber?: number;
}

export interface TeachingSessionDto {
  id: number;
  offeringId: number;
  courseCode: string;
  courseName: string;
  section: string;
  subSection?: string;
  facultyId: number;
  sessionDate: string;
  sessionType: string;
  status: 'HELD' | 'CANCELLED' | 'SWAPPED';
  unitNumber?: number;
  customTopicName?: string;
  lecturesTaken?: number;
  note?: string;
  reasonNotHeld?: string;
  topicsCovered: SyllabusTopicDto[];
  createdAt: string;
  lectureNumber?: number | null;
  delayReason?: string | null;
}

export interface SyllabusTopicProgressDto {
  id: number;
  courseId: number;
  unitNumber: number;
  unitTitle: string;
  topicName: string;
  targetWeek: number;
  lectureOrder: number;
  isCovered: boolean;
}

export interface CourseProgressDto {
  offeringId: number;
  courseId: number;
  courseCode: string;
  courseName: string;
  section: string;
  component: string;
  credits: number;
  termName: string;
  currentWeek: number;
  totalTopics: number;
  coveredTopics: number;
  coveragePercentage: number;
  expectedTopicsByCurrentWeek: number;
  topicDelta: number;
  /** NO_SYLLABUS / NOT_MEASURED: pace can't be measured (no syllabus, no timetabled lectures, no term dates). */
  pacingStatus: 'ON_TRACK' | 'AHEAD' | 'BEHIND' | 'NO_SYLLABUS' | 'NOT_MEASURED';
  pacingLabel: string;
  totalSessions: number;
  heldSessions: number;
  conductRatePercentage: number;
  topics: SyllabusTopicProgressDto[];
  ltp?: string;
  subSection?: string;
  courseDescription?: string;
}
