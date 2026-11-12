import { useEffect, useMemo, useState } from 'react';
import { componentLabel, sectionLabel } from '../lib/labels';

export interface PendingClassDto {
  date: string;
  offeringId: number;
  courseCode: string;
  courseName: string;
  section: string;
  subSection: string;
  component: string;
  timeSlot: string;
  room: string;
}

/** Monday of the date's week, as YYYY-MM-DD. */
function weekOf(date: string): string {
  const d = new Date(`${date}T00:00:00`);
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7));
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function heading(key: string, by: 'week' | 'month'): string {
  const d = new Date(`${by === 'month' ? `${key}-01` : key}T00:00:00`);
  return by === 'month'
    ? d.toLocaleDateString(undefined, { month: 'long', year: 'numeric' })
    : `Week of ${d.toLocaleDateString(undefined, { day: 'numeric', month: 'short' })}`;
}

/**
 * Timetabled classes that passed with nothing logged. Each one is logged (held or not held, with a
 * reason) from its own day, so "Open" jumps there.
 */
export function PendingClasses({ refreshKey, onOpenDay }: { refreshKey: unknown; onOpenDay: (date: string) => void }) {
  const [pending, setPending] = useState<PendingClassDto[] | null>(null);
  const [by, setBy] = useState<'week' | 'month'>('week');

  useEffect(() => {
    let cancelled = false;
    fetch('/api/teaching-sessions/pending', { credentials: 'include' })
      .then((res) => (res.ok ? res.json() : []))
      .then((data: PendingClassDto[]) => !cancelled && setPending(data))
      .catch(() => !cancelled && setPending([]));
    return () => {
      cancelled = true;
    };
  }, [refreshKey]);

  const groups = useMemo(() => {
    const map = new Map<string, PendingClassDto[]>();
    for (const p of [...(pending ?? [])].sort((a, b) => b.date.localeCompare(a.date))) {
      const key = by === 'month' ? p.date.slice(0, 7) : weekOf(p.date);
      map.set(key, [...(map.get(key) ?? []), p]);
    }
    return [...map.entries()];
  }, [pending, by]);

  if (!pending || pending.length === 0) return null;

  return (
    <section className="card p-4" aria-labelledby="pending-title">
      <div className="flex items-center justify-between gap-3">
        <h3 id="pending-title" className="font-display text-[18px] font-medium">
          Classes not logged yet <span className="num text-[var(--warn-text)]">({pending.length})</span>
        </h3>
        <div className="flex rounded-md border border-[var(--hairline)] p-0.5 text-[12px]" role="group" aria-label="Group by">
          {(['week', 'month'] as const).map((option) => (
            <button
              key={option}
              type="button"
              aria-pressed={by === option}
              onClick={() => setBy(option)}
              className={`px-2.5 py-1 rounded ${by === option ? 'bg-[var(--accent)] text-white' : 'text-[var(--ink-muted)]'}`}
            >
              {option === 'week' ? 'Week' : 'Month'}
            </button>
          ))}
        </div>
      </div>
      <div className="mt-3 max-h-72 overflow-y-auto space-y-3">
        {groups.map(([key, items]) => (
          <div key={key}>
            <p className="text-[11px] font-semibold uppercase tracking-wider text-[var(--ink-subtle)]">{heading(key, by)}</p>
            <ul className="mt-1 divide-y divide-[var(--hairline-soft)]">
              {items.map((p) => (
                <li key={`${p.date}-${p.offeringId}-${p.component}-${p.subSection}-${p.timeSlot}`} className="flex items-center justify-between gap-3 py-2 text-[13px]">
                  <span>
                    <span className="num">{p.date}</span> · {p.timeSlot} ·{' '}
                    <span className="font-mono">{p.courseCode}</span> {componentLabel(p.component)} · {sectionLabel(p.section, null, p.subSection)}
                  </span>
                  <button type="button" className="btn h-7 text-[12px] shrink-0" onClick={() => onOpenDay(p.date)}>
                    Open
                  </button>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
    </section>
  );
}
