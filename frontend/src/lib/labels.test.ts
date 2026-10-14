import { describe, it, expect } from 'vitest';
import { batchLabel, sectionLabel, componentLabel, componentShort, canonicalComponent, localDateString } from './labels';

describe('batchLabel', () => {
  it('shows nothing for the whole section', () => {
    expect(batchLabel('ALL')).toBe('');
    expect(batchLabel('')).toBe('');
    expect(batchLabel(null)).toBe('');
    expect(batchLabel(undefined)).toBe('');
  });

  it('never doubles the "Batch" prefix', () => {
    expect(batchLabel('C1')).toBe('Batch C1');
    expect(batchLabel('Batch C1')).toBe('Batch C1');
    expect(batchLabel('batch a2')).toBe('Batch A2');
  });
});

describe('sectionLabel', () => {
  it('reads "Sec X" whether or not the server already prefixed it', () => {
    expect(sectionLabel('F')).toBe('Sec F');
    expect(sectionLabel('Sec F')).toBe('Sec F');
  });

  it('names whole-cohort offerings instead of printing "Sec ALL"', () => {
    expect(sectionLabel('ALL')).toBe('All sections');
    expect(sectionLabel('Sec ALL')).toBe('All sections');
  });

  it('adds the semester when given', () => {
    expect(sectionLabel('F', 1)).toBe('Sem 1 · Sec F');
    expect(sectionLabel('ALL', 5)).toBe('Sem 5 · All sections');
  });

  it('appends a batch', () => {
    expect(sectionLabel('C', 1, 'C1')).toBe('Sem 1 · Sec C · Batch C1');
    expect(sectionLabel('C', 1, 'ALL')).toBe('Sem 1 · Sec C');
  });
});

describe('componentLabel', () => {
  it('maps legacy spellings onto L / T / P', () => {
    expect(componentLabel('THEORY')).toBe('Lecture');
    expect(componentLabel('LECTURE')).toBe('Lecture');
    expect(componentLabel('LAB')).toBe('Practical');
    expect(componentLabel('PRACTICAL')).toBe('Practical');
    expect(componentLabel('TUTORIAL')).toBe('Tutorial');
    expect(componentLabel('ALL')).toBe('Full course');
    expect(componentShort('LAB')).toBe('P');
    expect(componentShort('THEORY')).toBe('L');
  });
});

describe('label edge inputs', () => {
  it('trims and upper-cases batches, and treats "batch all" as the whole section', () => {
    expect(batchLabel('  c1  ')).toBe('Batch C1');
    expect(batchLabel('Batch ALL')).toBe('');
    expect(batchLabel('all')).toBe('');
    expect(batchLabel('   ')).toBe('');
    expect(batchLabel('BATCHC2')).toBe('Batch C2');
  });

  it('treats a missing or blank section as all sections and ignores semester 0', () => {
    expect(sectionLabel(null)).toBe('All sections');
    expect(sectionLabel(undefined, null, null)).toBe('All sections');
    expect(sectionLabel('  ', 0)).toBe('All sections');
    expect(sectionLabel(' sec  b ', 3)).toBe('Sem 3 · Sec B');
    expect(sectionLabel('all', 2, 'Batch A1')).toBe('Sem 2 · All sections · Batch A1');
  });

  it('reads components in any case and short spellings like the backend parser', () => {
    expect(canonicalComponent('p')).toBe('PRACTICAL');
    expect(canonicalComponent('t')).toBe('TUTORIAL');
    expect(canonicalComponent('l')).toBe('LECTURE');
    expect(canonicalComponent('Lab')).toBe('PRACTICAL');
    expect(canonicalComponent('tut')).toBe('TUTORIAL');
    expect(canonicalComponent('all')).toBe('ALL');
    expect(canonicalComponent('')).toBe('LECTURE');
    expect(canonicalComponent(null)).toBe('LECTURE');
    expect(canonicalComponent(undefined)).toBe('LECTURE');
    expect(componentShort('ALL')).toBe('LTP');
    expect(componentLabel('Seminar')).toBe('Lecture');
  });
});

describe('localDateString', () => {
  it('uses the local calendar day and pads month and day', () => {
    expect(localDateString(new Date(2026, 0, 5))).toBe('2026-01-05');
    expect(localDateString(new Date(2026, 11, 31, 23, 59))).toBe('2026-12-31');
    expect(localDateString(new Date(2026, 9, 14, 0, 1))).toBe('2026-10-14');
  });
});
