export interface CourseDto {
  id: number;
  code: string;
  name: string;
  type: string;
}

export interface CourseOfferingDto {
  id: number;
  course: CourseDto;
  academicYear: string;
  yearOfStudy: number;
  semester: number;
  section: string;
}

export interface TeachingAssignmentDto {
  id: number;
  offering: CourseOfferingDto;
  facultyId: number;
  facultyName: string;
  component: 'THEORY' | 'LAB';
}
