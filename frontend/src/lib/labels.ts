/**
 * One way to print a section, batch or LTP component everywhere in the app.
 * Mirrors the backend's SubSections.normalise / CourseComponent.parse.
 */

const WHOLE = 'ALL';

/** "C1" → "Batch C1"; the whole section ("ALL", empty) prints nothing. */
export function batchLabel(subSection: string | null | undefined): string {
  const value = (subSection ?? '').trim().replace(/^batch\s*/i, '').toUpperCase();
  return value === '' || value === WHOLE ? '' : `Batch ${value}`;
}

/** "F" or "Sec F" → "Sec F", "ALL" → "All sections"; optional semester and batch. */
export function sectionLabel(section: string | null | undefined, semester?: number | null, subSection?: string | null): string {
  const key = (section ?? '').trim().replace(/^sec\s*/i, '').toUpperCase();
  const parts: string[] = [];
  if (semester) parts.push(`Sem ${semester}`);
  parts.push(key === '' || key === WHOLE ? 'All sections' : `Sec ${key}`);
  const batch = batchLabel(subSection);
  if (batch) parts.push(batch);
  return parts.join(' · ');
}

type Canonical = 'LECTURE' | 'TUTORIAL' | 'PRACTICAL' | 'ALL';

export function canonicalComponent(component: string | null | undefined): Canonical {
  const c = (component ?? '').toUpperCase();
  if (c === 'ALL') return 'ALL';
  if (c === 'P' || c.includes('PRACTICAL') || c.includes('LAB')) return 'PRACTICAL';
  if (c === 'T' || c.startsWith('TUT')) return 'TUTORIAL';
  return 'LECTURE';
}

const LONG: Record<Canonical, string> = {
  LECTURE: 'Lecture',
  TUTORIAL: 'Tutorial',
  PRACTICAL: 'Practical',
  ALL: 'Full course',
};

const SHORT: Record<Canonical, string> = { LECTURE: 'L', TUTORIAL: 'T', PRACTICAL: 'P', ALL: 'LTP' };

export const componentLabel = (component: string | null | undefined) => LONG[canonicalComponent(component)];
export const componentShort = (component: string | null | undefined) => SHORT[canonicalComponent(component)];

/** Today as YYYY-MM-DD in the viewer's time zone (toISOString() is UTC and is a day off after midnight IST). */
export function localDateString(date: Date = new Date()): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}
