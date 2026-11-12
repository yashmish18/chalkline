// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PacingBadge } from './PacingBadge';

describe('PacingBadge', () => {
  it('a section with nothing to measure is neutral, never painted "behind"', () => {
    render(<PacingBadge status="NO_SYLLABUS" label="No syllabus uploaded" />);
    const badge = screen.getByText('No syllabus uploaded');
    expect(badge.className).toContain('bg-slate-50');
    expect(badge.className).not.toContain('amber');
  });

  it('shows the facts behind a measured pace', () => {
    render(<PacingBadge status="BEHIND" label="Behind by 3 topics (2 of 10 covered; 12 of 24 timetabled lectures are past)" />);
    const badge = screen.getByText(/Behind by 3 topics/);
    expect(badge.className).toContain('amber');
  });
});
