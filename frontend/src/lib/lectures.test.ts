import { describe, expect, it } from 'vitest';
import { lectureCount, lectureOptionLabel, topicForLecture } from './lectures';
import type { UnitOptionDto } from '../types/academics';

const topic = (id: number, unit: number, order: number, name: string) =>
  ({ id, courseId: 1, unitNumber: unit, unitTitle: `Unit ${unit}`, topicName: name, targetWeek: 0, lectureOrder: order });

/** Two units of the real Cloud ML description: lecture numbers run on across units. */
const units: UnitOptionDto[] = [
  { unitNumber: 1, unitTitle: 'AI/ML in the Cloud', topics: [topic(11, 1, 1, 'AI, ML, Deep Learning, and Generative AI'), topic(12, 1, 2, 'Why cloud computing for AI/ML?')] },
  { unitNumber: 2, unitTitle: 'Building ML Solutions on AWS', topics: [topic(21, 2, 3, 'Overview of the machine learning workflow')] },
];

describe('lecture numbers come from the syllabus order', () => {
  it('Lecture N is the Nth topic of the document, across units', () => {
    expect(topicForLecture(units, 1)?.id).toBe(11);
    expect(topicForLecture(units, 3)?.topicName).toBe('Overview of the machine learning workflow');
    expect(topicForLecture(units, 3)?.unitNumber).toBe(2);
  });

  it('past the end of the syllabus there is no topic, and nothing is made up', () => {
    expect(topicForLecture(units, 4)).toBeNull();
    expect(topicForLecture(undefined, 1)).toBeNull();
    expect(lectureOptionLabel('Lecture', 4, units, 2)).toBe('Lecture 4');
  });

  it('labels a lecture with its topic and marks the next one', () => {
    expect(lectureOptionLabel('Lecture', 2, units, 2)).toBe('Lecture 2 — Why cloud computing for AI/ML? (next)');
    // Practicals are numbered on their own and are not syllabus topics.
    expect(lectureOptionLabel('Practical', 2, units, 1)).toBe('Practical 2');
  });

  it('offers every syllabus lecture and at least ten past the next one', () => {
    const fiftyFive: UnitOptionDto[] = [{ unitNumber: 1, unitTitle: 'U', topics: Array.from({ length: 55 }, (_, i) => topic(i, 1, i + 1, `T${i}`)) }];
    expect(lectureCount(fiftyFive, 3)).toBe(55);
    expect(lectureCount(units, 3)).toBe(12);
  });
});
