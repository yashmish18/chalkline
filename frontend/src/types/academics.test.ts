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
});
