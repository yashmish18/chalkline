export interface CourseDto {
  id: number;
  code: string;
  name: string;
  type: string;
  credits?: number;
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
  component: 'THEORY' | 'LAB';
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

export interface TodayClassDto {
  offeringId: number;
  courseCode: string;
  courseName: string;
  section: string;
  component: string;
  timeSlot: string;
  nextTopicId: number | null;
  nextTopic: string;
  unitTitle: string;
  targetWeek: number;
  totalPlanned: number;
  completedSoFar: number;
  isLogged: boolean;
}

export interface TeachingSessionDto {
  id: number;
  offeringId: number;
  courseCode: string;
  courseName: string;
  section: string;
  facultyId: number;
  sessionDate: string;
  sessionType: string;
  status: 'HELD' | 'CANCELLED' | 'SWAPPED';
  note?: string;
  reasonNotHeld?: string;
  topicsCovered: SyllabusTopicDto[];
  createdAt: string;
}
