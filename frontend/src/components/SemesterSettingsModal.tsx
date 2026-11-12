import { useEffect, useState } from 'react';
import { Plus, Trash2, X } from 'lucide-react';

export interface Holiday {
  date: string;
  name: string;
}

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
  const [academicYear, setAcademicYear] = useState('');
  /** null until the saved semester has loaded: a save before then leaves the holidays as they are. */
  const [holidays, setHolidays] = useState<Holiday[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetch('/api/terms/active', { credentials: 'include' })
      .then((res) => (res.ok ? res.json() : null))
      .then((saved: { academicYear?: string; holidays?: Holiday[] } | null) => {
        if (cancelled) return;
        setAcademicYear((current) => current || saved?.academicYear || '');
        setHolidays(saved?.holidays ?? []);
      })
      .catch(() => {
        if (!cancelled) setHolidays([]);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const updateHoliday = (index: number, change: Partial<Holiday>) =>
    setHolidays((list) => (list ?? []).map((h, i) => (i === index ? { ...h, ...change } : h)));
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
        body: JSON.stringify({
          name,
          academicYear,
          startDate,
          endDate,
          ...(holidays !== null ? { holidays: holidays.filter((h) => h.date || h.name.trim()) } : {}),
        }),
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
      <form onSubmit={save} className="card w-full max-w-md max-h-[90vh] overflow-y-auto p-6 shadow-lg space-y-5">
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

        <fieldset>
          <legend className="text-[13px] font-medium">Holidays</legend>
          <p className="text-[12px] text-[var(--ink-subtle)]">No classes are owed on these days: they are never “not logged” and pace doesn’t expect them.</p>
          {holidays === null ? (
            <p className="mt-2 text-[12px] text-[var(--ink-subtle)]">Loading…</p>
          ) : (
            <ul className="mt-2 space-y-2">
              {holidays.map((h, i) => (
                <li key={i} className="flex items-center gap-2">
                  <input type="date" className="field" aria-label={`Holiday ${i + 1} date`} value={h.date}
                    min={startDate || undefined} max={endDate || undefined}
                    onChange={(e) => updateHoliday(i, { date: e.target.value })} required />
                  <input className="field flex-1" aria-label={`Holiday ${i + 1} name`} value={h.name} placeholder="Diwali"
                    onChange={(e) => updateHoliday(i, { name: e.target.value })} required />
                  <button type="button" className="btn btn-quiet w-8 px-0" aria-label={`Remove holiday ${i + 1}`}
                    onClick={() => setHolidays((list) => (list ?? []).filter((_, j) => j !== i))}>
                    <Trash2 className="w-4 h-4" />
                  </button>
                </li>
              ))}
            </ul>
          )}
          <button type="button" className="btn mt-2" disabled={holidays === null}
            onClick={() => setHolidays((list) => [...(list ?? []), { date: '', name: '' }])}>
            <Plus className="w-4 h-4" /> Add holiday
          </button>
        </fieldset>

        {error && <p role="alert" className="text-[13px] text-[var(--danger-text)]">{error}</p>}

        <div className="flex justify-end gap-2 pt-1">
          <button type="button" onClick={onClose} className="btn">Cancel</button>
          <button type="submit" disabled={saving} className="btn btn-primary">{saving ? 'Saving…' : 'Save'}</button>
        </div>
      </form>
    </div>
  );
}
