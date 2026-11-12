// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SyllabusImportModal, lectureNumberOf } from './SyllabusImportModal';
import type { ParsedSyllabusDto } from '../types/academics';

const PREVIEW: ParsedSyllabusDto = {
  sourceFileName: 'Cloud ML Course Description.docx',
  extractedCharacters: 4727,
  units: [
    { unitNumber: 1, unitTitle: 'AI/ML in the Cloud', topics: [
      { topicName: 'AI, ML, Deep Learning, and Generative AI', targetWeek: 0 },
      { topicName: 'Why cloud computing for AI/ML?', targetWeek: 0 }] },
    { unitNumber: 2, unitTitle: 'Building Machine Learning Solutions on AWS', topics: [
      { topicName: 'Overview of the machine learning workflow', targetWeek: 0 }] },
  ],
  documentTitle: 'Cloud-Based Machine Learning',
  documentLtp: '3-0-2',
  mismatches: [
    "The document's L-T-P is 3-0-2, but the allocation sheet has 3-1-2 for CS2201. Correct whichever is wrong.",
    'The document calls the course "Cloud-Based Machine Learning", but the allocation sheet calls CS2201 "Computer Networks".',
  ],
};

function renderWithPreview() {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify(PREVIEW), { status: 200 })));
  const file = new File(['x'], 'Cloud ML Course Description.docx');
  render(
    <QueryClientProvider client={new QueryClient()}>
      <SyllabusImportModal courseId={7} courseCode="CS2201" initialFile={file} onClose={() => {}} onConfirmed={() => {}} />
    </QueryClientProvider>,
  );
}

describe('SyllabusImportModal preview', () => {
  it('names every difference between the document and the allocation sheet', async () => {
    renderWithPreview();
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('3-0-2, but the allocation sheet has 3-1-2');
    expect(alert).toHaveTextContent('"Computer Networks"');
  });

  it('numbers topics as lectures, running on across units', async () => {
    renderWithPreview();
    expect(await screen.findByText('L1')).toBeInTheDocument();
    expect(screen.getByText('L3')).toBeInTheDocument();
    expect(lectureNumberOf(PREVIEW, 1, 0)).toBe(3);
  });
});
