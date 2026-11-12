import type { SyllabusTopicDto, UnitOptionDto } from '../types/academics';

/**
 * Lecture numbers come from the syllabus itself: topics are numbered 1…N in the order the course
 * description lists them, across all units (that number is the topic's lectureOrder). So
 * "Lecture 15" is the 15th topic of the document.
 */
export function topicForLecture(units: UnitOptionDto[] | undefined, lectureNumber: number): SyllabusTopicDto | null {
  for (const unit of units ?? []) {
    const topic = unit.topics.find((t) => t.lectureOrder === lectureNumber);
    if (topic) return topic;
  }
  return null;
}

/** How many lectures the dropdown offers: every syllabus topic, and at least ten past the next one. */
export function lectureCount(units: UnitOptionDto[] | undefined, nextLecture: number): number {
  const topics = (units ?? []).reduce((n, u) => n + u.topics.length, 0);
  return Math.max(topics, nextLecture + 9);
}

/** "Lecture 15 — Model deployment", or "Lecture 15" past the end of the syllabus. */
export function lectureOptionLabel(label: string, n: number, units: UnitOptionDto[] | undefined, next: number): string {
  const topic = label === 'Lecture' ? topicForLecture(units, n) : null;
  return `${label} ${n}${topic ? ` — ${topic.topicName}` : ''}${n === next ? ' (next)' : ''}`;
}
