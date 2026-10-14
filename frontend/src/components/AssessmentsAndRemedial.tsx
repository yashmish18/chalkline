import { useCallback, useEffect, useState } from 'react';

interface Assessment {
  id: number;
  name: string;
  kind: string;
  weightPct: number;
  heldOn: string;
  studentsAssessed: number | null;
  averagePct: number | null;
  below40Count: number | null;
}

interface RemedialPlan {
  id: number;
  reason: string;
  laggingTopics: string | null;
  action: string;
  extraSessions: number;
  targetDate: string;
  status: string;
}

const KINDS: [string, string][] = [
  ['MID_TERM', 'Mid-term'],
  ['QUIZ', 'Quiz'],
  ['ASSIGNMENT', 'Assignment'],
  ['LAB', 'Lab evaluation'],
  ['PROJECT', 'Project'],
  ['OTHER', 'Other'],
];
const REASONS: [string, string][] = [
  ['BEHIND_SYLLABUS', 'Behind syllabus'],
  ['LOW_ASSESSMENT', 'Low assessment scores'],
  ['CANCELLED_CLASSES', 'Cancelled classes'],
  ['OTHER', 'Other'],
];

async function send(url: string, method: string, body?: unknown) {
  const res = await fetch(url, {
    method,
    credentials: 'include',
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.detail || err.title || `Request failed (${res.status})`);
  }
  return res.status === 204 ? null : res.json();
}

/**
 * Continuous assessments held for a section, and the remedial plan when the section needs one.
 * Shown to the faculty member who teaches the section.
 */
export function AssessmentsAndRemedial({ offeringId, students, topicDelta }: { offeringId: number; students: number; topicDelta: number }) {
  const [assessments, setAssessments] = useState<Assessment[]>([]);
  const [plans, setPlans] = useState<RemedialPlan[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [filing, setFiling] = useState(false);

  const load = useCallback(async () => {
    try {
      const [a, p] = await Promise.all([
        send(`/api/assessments?offeringId=${offeringId}`, 'GET'),
        send(`/api/remedial-plans?offeringId=${offeringId}`, 'GET'),
      ]);
      setAssessments(a);
      setPlans(p);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Could not load assessments.');
    }
  }, [offeringId]);

  useEffect(() => {
    load();
  }, [load]);

  const latest = assessments[assessments.length - 1];
  const lowScores = latest?.below40Count != null && latest?.studentsAssessed ? latest.below40Count / latest.studentsAssessed > 0.15 : false;
  const needsPlan = (topicDelta <= -2 || lowScores) && !plans.some((p) => p.status === 'SUBMITTED');

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <section className="card p-5">
        <div className="flex items-baseline justify-between">
          <h3 className="font-display text-[20px] font-medium">Continuous assessment</h3>
          <button type="button" className="btn h-7 text-[12px]" onClick={() => setAdding((v) => !v)}>
            {adding ? 'Cancel' : 'Record assessment'}
          </button>
        </div>
        {error && <p className="mt-2 text-[13px] text-[var(--danger-text)]">{error}</p>}

        {adding && (
          <AssessmentForm
            students={students}
            onSave={async (body) => {
              await send('/api/assessments', 'POST', { offeringId, ...body });
              setAdding(false);
              load();
            }}
          />
        )}

        {assessments.length === 0 ? (
          <p className="mt-4 text-[13px] text-[var(--ink-subtle)]">No assessment recorded yet.</p>
        ) : (
          <table className="mt-4 w-full text-[13px]">
            <thead className="text-left text-[var(--ink-subtle)] border-b border-[var(--hairline)]">
              <tr>
                <th className="py-1.5 font-medium">Assessment</th>
                <th className="py-1.5 font-medium text-right">Weight</th>
                <th className="py-1.5 font-medium text-right">Average</th>
                <th className="py-1.5 font-medium text-right">Under 40%</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {assessments.map((a) => (
                <tr key={a.id} className="border-b border-[var(--hairline-soft)]">
                  <td className="py-2">
                    {a.name}
                    <div className="text-[12px] text-[var(--ink-subtle)]">{new Date(`${a.heldOn}T00:00:00`).toLocaleDateString(undefined, { day: 'numeric', month: 'short' })}</div>
                  </td>
                  <td className="py-2 text-right num">{a.weightPct}%</td>
                  <td className="py-2 text-right num">{a.averagePct ?? '—'}{a.averagePct != null && '%'}</td>
                  <td className="py-2 text-right num">{a.below40Count ?? '—'}</td>
                  <td className="py-2 text-right">
                    <button type="button" className="text-[12px] text-[var(--ink-tertiary)] hover:text-[var(--danger-text)]"
                      onClick={async () => { if (window.confirm(`Delete ${a.name}?`)) { await send(`/api/assessments/${a.id}`, 'DELETE'); load(); } }}>
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr className="text-[12px] text-[var(--ink-subtle)]">
                <td className="pt-2">Total weight so far</td>
                <td className="pt-2 text-right num">{assessments.reduce((n, a) => n + a.weightPct, 0)}%</td>
                <td colSpan={3} />
              </tr>
            </tfoot>
          </table>
        )}
      </section>

      <section className="card p-5">
        <div className="flex items-baseline justify-between">
          <h3 className="font-display text-[20px] font-medium">Remedial plan</h3>
          {!filing && (
            <button type="button" className={`btn h-7 text-[12px] ${needsPlan ? 'btn-primary' : ''}`} onClick={() => setFiling(true)}>
              File a plan
            </button>
          )}
        </div>
        {needsPlan && !filing && (
          <p className="mt-2 rounded-md bg-rose-50 border border-rose-200 px-3 py-2 text-[13px] text-rose-800">
            {topicDelta <= -2 ? `This section is ${-topicDelta} topics behind plan.` : 'More than 15% of students scored under 40% in the latest assessment.'} A remedial plan is required.
          </p>
        )}
        {filing && (
          <RemedialForm
            defaultReason={topicDelta <= -2 ? 'BEHIND_SYLLABUS' : lowScores ? 'LOW_ASSESSMENT' : 'OTHER'}
            onCancel={() => setFiling(false)}
            onSave={async (body) => {
              await send('/api/remedial-plans', 'POST', { offeringId, ...body });
              setFiling(false);
              load();
            }}
          />
        )}
        {plans.length === 0 ? (
          !filing && !needsPlan && <p className="mt-4 text-[13px] text-[var(--ink-subtle)]">Not needed at the moment.</p>
        ) : (
          <ul className="mt-4 space-y-3">
            {plans.map((p) => (
              <li key={p.id} className="rounded-md border border-[var(--hairline)] px-3 py-2.5 text-[13px]">
                <div className="flex items-center justify-between gap-3">
                  <span className="font-medium">{REASONS.find(([k]) => k === p.reason)?.[1] ?? p.reason}</span>
                  {p.status === 'COMPLETED' ? (
                    <span className="chip chip-ok">Completed</span>
                  ) : (
                    <div className="flex items-center gap-2">
                      <span className="chip chip-info">Submitted to coordinator</span>
                      <button type="button" className="btn h-7 text-[12px]" onClick={async () => { await send(`/api/remedial-plans/${p.id}/complete`, 'POST'); load(); }}>
                        Mark completed
                      </button>
                    </div>
                  )}
                </div>
                <p className="mt-1 text-[var(--ink-muted)]">{p.action}</p>
                <p className="mt-1 text-[12px] text-[var(--ink-subtle)] num">
                  {p.laggingTopics ? `${p.laggingTopics} · ` : ''}{p.extraSessions} extra sessions · by {new Date(`${p.targetDate}T00:00:00`).toLocaleDateString(undefined, { day: 'numeric', month: 'short' })}
                </p>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

function AssessmentForm({ students, onSave }: { students: number; onSave: (body: object) => Promise<void> }) {
  const [name, setName] = useState('CIE-1 Mid-term');
  const [kind, setKind] = useState('MID_TERM');
  const [weight, setWeight] = useState('30');
  const [heldOn, setHeldOn] = useState(() => new Date().toLocaleDateString('en-CA'));
  const [assessed, setAssessed] = useState(students ? String(students) : '');
  const [average, setAverage] = useState('');
  const [below, setBelow] = useState('');
  const [error, setError] = useState<string | null>(null);
  const num = (v: string) => (v.trim() === '' ? null : Number(v));

  return (
    <form
      className="mt-4 grid grid-cols-2 gap-3 rounded-lg bg-[var(--surface-3)] p-4"
      onSubmit={async (e) => {
        e.preventDefault();
        setError(null);
        try {
          await onSave({ name, kind, weightPct: Number(weight), heldOn, studentsAssessed: num(assessed), averagePct: num(average), below40Count: num(below) });
        } catch (err: unknown) {
          setError(err instanceof Error ? err.message : 'Could not save.');
        }
      }}
    >
      <label className="col-span-2 text-[12px]">Name<input className="field mt-1 h-9" value={name} onChange={(e) => setName(e.target.value)} required /></label>
      <label className="text-[12px]">Type
        <select className="field mt-1 h-9" value={kind} onChange={(e) => setKind(e.target.value)}>
          {KINDS.map(([k, l]) => <option key={k} value={k}>{l}</option>)}
        </select>
      </label>
      <label className="text-[12px]">Weight (%)<input type="number" min={0} max={100} className="field mt-1 h-9" value={weight} onChange={(e) => setWeight(e.target.value)} required /></label>
      <label className="text-[12px]">Held on<input type="date" className="field mt-1 h-9" value={heldOn} onChange={(e) => setHeldOn(e.target.value)} required /></label>
      <label className="text-[12px]">Students assessed<input type="number" min={0} className="field mt-1 h-9" value={assessed} onChange={(e) => setAssessed(e.target.value)} /></label>
      <label className="text-[12px]">Class average (%)<input type="number" min={0} max={100} className="field mt-1 h-9" value={average} onChange={(e) => setAverage(e.target.value)} placeholder="when marked" /></label>
      <label className="text-[12px]">Students under 40%<input type="number" min={0} className="field mt-1 h-9" value={below} onChange={(e) => setBelow(e.target.value)} placeholder="when marked" /></label>
      {error && <p className="col-span-2 text-[12px] text-[var(--danger-text)]">{error}</p>}
      <div className="col-span-2 flex justify-end"><button type="submit" className="btn btn-primary">Save</button></div>
    </form>
  );
}

function RemedialForm({ defaultReason, onSave, onCancel }: { defaultReason: string; onSave: (body: object) => Promise<void>; onCancel: () => void }) {
  const [reason, setReason] = useState(defaultReason);
  const [topics, setTopics] = useState('');
  const [action, setAction] = useState('');
  const [extra, setExtra] = useState('2');
  const [target, setTarget] = useState(() => {
    const d = new Date();
    d.setDate(d.getDate() + 21);
    return d.toLocaleDateString('en-CA');
  });
  const [error, setError] = useState<string | null>(null);

  return (
    <form
      className="mt-4 grid grid-cols-2 gap-3 rounded-lg bg-[var(--surface-3)] p-4"
      onSubmit={async (e) => {
        e.preventDefault();
        setError(null);
        try {
          await onSave({ reason, laggingTopics: topics || null, action, extraSessions: Number(extra), targetDate: target });
        } catch (err: unknown) {
          setError(err instanceof Error ? err.message : 'Could not save.');
        }
      }}
    >
      <label className="col-span-2 text-[12px]">Reason
        <select className="field mt-1 h-9" value={reason} onChange={(e) => setReason(e.target.value)}>
          {REASONS.map(([k, l]) => <option key={k} value={k}>{l}</option>)}
        </select>
      </label>
      <label className="col-span-2 text-[12px]">Topics or weak areas<input className="field mt-1 h-9" value={topics} onChange={(e) => setTopics(e.target.value)} placeholder="e.g. Unit 3: pointers and recursion" /></label>
      <label className="col-span-2 text-[12px]">What you will do
        <textarea className="field mt-1 h-20 py-2" value={action} onChange={(e) => setAction(e.target.value)} placeholder="e.g. Two extra tutorials on Saturdays, then a re-test" required />
      </label>
      <label className="text-[12px]">Extra sessions<input type="number" min={0} className="field mt-1 h-9" value={extra} onChange={(e) => setExtra(e.target.value)} /></label>
      <label className="text-[12px]">Done by<input type="date" className="field mt-1 h-9" value={target} onChange={(e) => setTarget(e.target.value)} required /></label>
      {error && <p className="col-span-2 text-[12px] text-[var(--danger-text)]">{error}</p>}
      <div className="col-span-2 flex justify-end gap-2">
        <button type="button" className="btn" onClick={onCancel}>Cancel</button>
        <button type="submit" className="btn btn-primary">File plan</button>
      </div>
    </form>
  );
}
