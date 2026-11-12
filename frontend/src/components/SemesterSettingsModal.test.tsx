// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SemesterSettingsModal } from './SemesterSettingsModal';

const TERM = { name: 'Odd Semester 2026-27', startDate: '2026-07-15', endDate: '2026-12-15' };

function putBody(fetchMock: ReturnType<typeof vi.fn>) {
  const put = fetchMock.mock.calls.find(([, init]) => (init as RequestInit | undefined)?.method === 'PUT');
  return JSON.parse((put![1] as RequestInit).body as string);
}

describe('SemesterSettingsModal holidays', () => {
  it('loads the saved holidays and academic year, and saves an added holiday', async () => {
    const fetchMock = vi.fn(async (_url: string, init?: RequestInit) => init?.method === 'PUT'
      ? new Response('{}', { status: 200 })
      : new Response(JSON.stringify({ academicYear: '2026-2027', holidays: [{ date: '2026-10-02', name: 'Gandhi Jayanti' }] }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    const onSaved = vi.fn();
    render(<SemesterSettingsModal term={TERM} onClose={() => {}} onSaved={onSaved} />);

    expect(await screen.findByDisplayValue('Gandhi Jayanti')).toBeInTheDocument();
    expect(screen.getByDisplayValue('2026-2027')).toBeInTheDocument();   // read, not guessed from the name

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /add holiday/i }));
    await user.type(screen.getByLabelText('Holiday 2 date'), '2026-11-08');
    await user.type(screen.getByLabelText('Holiday 2 name'), 'Diwali');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(onSaved).toHaveBeenCalled());
    expect(putBody(fetchMock).holidays).toEqual([
      { date: '2026-10-02', name: 'Gandhi Jayanti' },
      { date: '2026-11-08', name: 'Diwali' },
    ]);
  });

  it('a save before the holidays have loaded leaves them untouched', async () => {
    const fetchMock = vi.fn((_url: string, init?: RequestInit) => init?.method === 'PUT'
      ? Promise.resolve(new Response('{}', { status: 200 }))
      : new Promise<Response>(() => {}));   // the GET never answers
    vi.stubGlobal('fetch', fetchMock);
    render(<SemesterSettingsModal term={TERM} onClose={() => {}} onSaved={() => {}} />);

    const user = userEvent.setup();
    await user.type(screen.getByPlaceholderText('2026-2027'), '2026-2027');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(fetchMock.mock.calls.some(([, i]) => (i as RequestInit | undefined)?.method === 'PUT')).toBe(true));
    expect(putBody(fetchMock)).not.toHaveProperty('holidays');
  });
});
