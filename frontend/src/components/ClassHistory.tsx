import { useEffect, useState } from 'react';
import { TeachingSessionDto } from '../types/academics';
import { canonicalComponent, componentLabel } from '../lib/labels';

/** Every class logged for one section's component and batch: when, which lecture, what was covered. */
export function ClassHistory({ offeringId, component, subSection }: { offeringId: number; component: string; subSection?: string }) {
  const [sessions, setSessions] = useState<TeachingSessionDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setSessions(null);
    fetch(`/api/teaching-sessions/offering/${offeringId}`, { credentials: 'include' })
      .then((res) => {
        if (!res.ok) throw new Error(`Failed to load classes (${res.status})`);
        return res.json();
      })
      .then((data: TeachingSessionDto[]) => !cancelled && setSessions(data))
      .catch((err: Error) => !cancelled && setError(err.message));
    return () => {
      cancelled = true;
    };
  }, [offeringId]);

  if (error) return <p role="alert" className="text-xs text-rose-700">{error}</p>;
  if (!sessions) return <p className="text-xs text-slate-500">Loading…</p>;

  const batch = subSection ?? 'ALL';
  const mine = sessions.filter((s) => canonicalComponent(s.sessionType) === canonicalComponent(component)
    && (s.subSection ?? 'ALL') === batch);
  const held = mine.filter((s) => s.status === 'HELD').length;

  if (mine.length === 0) {
    return <p className="bg-white rounded-lg border border-border p-6 text-center text-xs text-slate-500">No {componentLabel(component).toLowerCase()} logged yet.</p>;
  }

  return (
    <div className="bg-white rounded-lg border border-border overflow-hidden">
      <p className="px-4 py-2.5 text-xs text-slate-600 border-b border-border">
        {held} held · {mine.length - held} not held
      </p>
      <div className="overflow-x-auto">
        <table className="min-w-full text-xs">
          <thead className="bg-slate-50 text-[11px] text-slate-600 text-left">
            <tr>
              <th className="px-4 py-2 font-semibold">Date</th>
              <th className="px-4 py-2 font-semibold">No.</th>
              <th className="px-4 py-2 font-semibold">Status</th>
              <th className="px-4 py-2 font-semibold">Unit</th>
              <th className="px-4 py-2 font-semibold">Topic covered / reason</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {mine.map((s) => (
              <tr key={s.id}>
                <td className="px-4 py-2 font-mono whitespace-nowrap">{s.sessionDate}</td>
                <td className="px-4 py-2 font-mono">{s.lectureNumber ?? '—'}</td>
                <td className="px-4 py-2">{s.status === 'HELD' ? 'Held' : s.status === 'CANCELLED' ? 'Not held' : 'Swapped'}</td>
                <td className="px-4 py-2">{s.status === 'HELD' && s.unitNumber ? `Unit ${s.unitNumber}` : '—'}</td>
                <td className="px-4 py-2">
                  {s.status === 'HELD'
                    ? [...s.topicsCovered.map((t) => t.topicName), s.customTopicName].filter(Boolean).join('; ') || '—'
                    : s.reasonNotHeld}
                  {s.delayReason && <span className="block text-[11px] text-amber-700">Logged late: {s.delayReason}</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
