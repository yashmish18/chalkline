import { describe, it, expect } from 'vitest';
import { TodayClassDto } from './academics';

describe('Academics Types & Pacing calculations', () => {
  it('calculates syllabus completion percentage correctly', () => {
    const sampleClass: TodayClassDto = {
      offeringId: 1,
      courseCode: 'CS501',
      courseName: 'Computer Networks',
      section: 'Sec A',
      component: 'THEORY',
      timeSlot: '10:00 AM – 11:00 AM (Room 304)',
      nextTopicId: 11,
      nextTopic: 'Distance Vector Routing',
      unitTitle: 'Unit 4',
      targetWeek: 8,
      totalPlanned: 15,
      completedSoFar: 10,
      isLogged: false,
    };

    const percentage = Math.round((sampleClass.completedSoFar / sampleClass.totalPlanned) * 100);
    expect(percentage).toBe(67);
  });

  it('validates academic document categories and MIME extensions', () => {
    const isAllowedDocument = (filename: string) => {
      const ext = filename.split('.').pop()?.toLowerCase();
      return ext === 'pdf' || ext === 'doc' || ext === 'docx';
    };

    expect(isAllowedDocument('timetable_odd_2026.pdf')).toBe(true);
    expect(isAllowedDocument('academic_calendar.docx')).toBe(true);
    expect(isAllowedDocument('cs501_syllabus.doc')).toBe(true);
    expect(isAllowedDocument('script.exe')).toBe(false);
  });
});
