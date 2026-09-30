export interface CourseDto {
  id: number;
  code: string;
  name: string;
  type: string;
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
}

export interface TeachingAssignmentDto {
  id: number;
  offering: CourseOfferingDto;
  facultyId: number;
  facultyName: string;
  component: 'THEORY' | 'LAB' | 'LECTURE' | 'PRACTICAL' | 'TUTORIAL';
  subSection?: string;
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
  pacingStatus: 'ON_TRACK' | 'AHEAD' | 'BEHIND';
  pacingLabel: string;
  totalSessions: number;
  heldSessions: number;
  conductRatePercentage: number;
  topics: SyllabusTopicProgressDto[];
  ltp?: string;
  subSection?: string;
  courseDescription?: string;
}
