import { useState } from 'react';
import { X } from 'lucide-react';

export interface TermSettings {
  name: string | null;
  startDate: string | null;
  endDate: string | null;
}

interface Props {
  term: TermSettings;
  onClose: () => void;
  onSaved: () => void;
}

/**
 * The semester's name and teaching dates. Pacing weeks and "classes due so far" both count from
 * the start date, so this is the first thing a coordinator sets each semester.
 */
export function SemesterSettingsModal({ term, onClose, onSaved }: Props) {
  const [name, setName] = useState(term.name ?? '');
  const [academicYear, setAcademicYear] = useState(() => {
    const match = term.name?.match(/\d{4}\s*[-–]\s*\d{2,4}/);
    return match ? match[0].replace(/\s/g, '') : '';
  });
  const [startDate, setStartDate] = useState(term.startDate ?? '');
  const [endDate, setEndDate] = useState(term.endDate ?? '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const res = await fetch('/api/terms/active', {
        method: 'PUT',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, academicYear, startDate, endDate }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body.detail || body.title || 'Could not save the semester.');
      }
      onSaved();
      onClose();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Could not save the semester.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4" role="dialog" aria-modal="true" aria-labelledby="semester-title">
      <form onSubmit={save} className="card w-full max-w-md p-6 shadow-lg space-y-5">
        <div className="flex items-start justify-between">
          <div>
            <h2 id="semester-title" className="font-display text-2xl font-medium">Semester</h2>
            <p className="mt-1 text-[13px] text-[var(--ink-subtle)]">
              Week numbers and classes due are counted from the start date.
            </p>
          </div>
          <button type="button" onClick={onClose} className="btn btn-quiet w-8 px-0" aria-label="Close">
            <X className="w-4 h-4" />
          </button>
        </div>

        <label className="block">
          <span className="text-[13px] font-medium">Name</span>
          <input className="field mt-1.5" value={name} onChange={(e) => setName(e.target.value)} placeholder="Odd Semester 2026–27" required />
        </label>
        <label className="block">
          <span className="text-[13px] font-medium">Academic year</span>
          <input className="field mt-1.5" value={academicYear} onChange={(e) => setAcademicYear(e.target.value)} placeholder="2026-2027" required />
        </label>
        <div className="grid grid-cols-2 gap-3">
          <label className="block">
            <span className="text-[13px] font-medium">Teaching starts</span>
            <input type="date" className="field mt-1.5" value={startDate} onChange={(e) => setStartDate(e.target.value)} required />
          </label>
          <label className="block">
            <span className="text-[13px] font-medium">Teaching ends</span>
            <input type="date" className="field mt-1.5" value={endDate} onChange={(e) => setEndDate(e.target.value)} required />
          </label>
        </div>

        {error && <p role="alert" className="text-[13px] text-[var(--danger-text)]">{error}</p>}

        <div className="flex justify-end gap-2 pt-1">
          <button type="button" onClick={onClose} className="btn">Cancel</button>
          <button type="submit" disabled={saving} className="btn btn-primary">{saving ? 'Saving…' : 'Save'}</button>
        </div>
      </form>
    </div>
  );
}
