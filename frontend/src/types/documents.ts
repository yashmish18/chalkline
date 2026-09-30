export type AcademicDocumentCategory = 'MASTER_TIMETABLE' | 'ACADEMIC_CALENDAR' | 'COURSE_SYLLABUS';

export interface AcademicDocumentDto {
  id: number;
  category: AcademicDocumentCategory;
  title: string;
  description: string | null;
  fileName: string;
  fileType: string;
  fileSize: number;
  storageKey: string;
  termId: number | null;
  courseId: number | null;
  uploadedByFacultyId: number | null;
  uploadedByName: string;
  createdAt: string;
}
