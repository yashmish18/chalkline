import type {
  CriticalCase,
  FacultyBreakdown,
  Mismatch,
  ScoreLine,
  SectionPerformance,
  LogEntry,
  RemedialStatus,
} from '../types/reports';
import { MISMATCH_LABEL, REMEDIAL_LABEL, YEAR_LABEL } from '../types/reports';
import { batchLabel, componentLabel, sectionLabel } from '../lib/labels';

/** Report building blocks shared by the printable report, the dashboard and the drill-down pages. */

const STATUS_CHIP: Record<string, string> = {
  OK: 'chip chip-ok',
  WATCH: 'chip chip-warn',
  CRITICAL: 'chip chip-bad',
  NO_DATA: 'chip',
};
const STATUS_TEXT: Record<string, string> = { OK: 'OK', WATCH: 'Watch', CRITICAL: 'Critical', NO_DATA: 'No data' };

export function Scorecard({ lines }: { lines: ScoreLine[] }) {
  return (
    <table className="w-full text-[12.5px]">
      <thead className="text-left text-[var(--ink-subtle)] border-b border-[var(--ink)]">
        <tr>
          <th className="py-2 pr-3 font-medium">Measure</th>
          <th className="py-2 pr-3 font-medium">Target</th>
          <th className="py-2 pr-3 font-medium">Actual</th>
          <th className="py-2 pr-3 font-medium">Status</th>
          <th className="py-2 font-medium">Note</th>
        </tr>
      </thead>
      <tbody>
        {lines.map((l) => (
          <tr key={l.metric} className="border-b border-[var(--hairline-soft)] align-top break-inside-avoid">
            <td className="py-2 pr-3 font-medium">{l.metric}</td>
            <td className="py-2 pr-3 text-[var(--ink-muted)]">{l.target}</td>
            <td className="py-2 pr-3 num font-medium">{l.actual}</td>
            <td className="py-2 pr-3"><span className={STATUS_CHIP[l.status]}>{STATUS_TEXT[l.status]}</span></td>
            <td className="py-2 text-[var(--ink-subtle)]">{l.note}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export function CriticalCasesTable({ cases, onFaculty, onRemind, busyRef, compact = false }: {
  cases: CriticalCase[];
  onFaculty?: (id: number) => void;
  onRemind?: (c: CriticalCase) => void;
  busyRef?: string | null;
  compact?: boolean;
}) {
  if (cases.length === 0) return <p className="text-[13px] text-[var(--ink-subtle)]">No open cases.</p>;
  return (
    <table className="w-full text-[12.5px]">
      <thead className="text-left text-[var(--ink-subtle)] border-b border-[var(--ink)]">
        <tr>
          <th className="py-2 pr-3 font-medium w-14">Ref</th>
          <th className="py-2 pr-3 font-medium">Case</th>
          {!compact && <th className="py-2 pr-3 font-medium w-[150px]">Faculty</th>}
          <th className="py-2 pr-3 font-medium text-right w-[64px]">Students</th>
          <th className="py-2 font-medium w-[170px]">Required action</th>
          {onRemind && <th className="py-2 print:hidden" />}
        </tr>
      </thead>
      <tbody>
        {cases.map((c) => (
          <tr key={c.ref} className="border-b border-[var(--hairline-soft)] align-top break-inside-avoid">
            <td className="py-2 pr-3">
              <span className={`font-mono text-[11.5px] ${c.severity === 'CRITICAL' ? 'text-[var(--danger-text)]' : 'text-[var(--warn-text)]'}`}>{c.ref}</span>
            </td>
            <td className="py-2 pr-3">
              <div className="font-medium">{c.category}<span className="font-normal text-[var(--ink-subtle)]"> · {c.entity}</span></div>
              <div className="text-[var(--ink-muted)]">{c.description}</div>
            </td>
            {!compact && (
              <td className="py-2 pr-3">
                {c.facultyId && onFaculty ? (
                  <button type="button" className="hover:underline hover:text-[var(--accent)] text-left" onClick={() => onFaculty(c.facultyId!)}>
                    {c.facultyName}
                  </button>
                ) : (
                  c.facultyName
                )}
              </td>
            )}
            <td className="py-2 pr-3 text-right num">{c.studentsAffected || '—'}</td>
            <td className="py-2 text-[var(--ink-muted)]">{c.requiredAction}</td>
            {onRemind && (
              <td className="py-2 pl-3 text-right print:hidden">
                {c.facultyId && (
                  <button type="button" className="btn h-7 text-[12px]" disabled={busyRef === c.ref} onClick={() => onRemind(c)}>
                    {busyRef === c.ref ? 'Sending…' : 'Remind'}
                  </button>
                )}
              </td>
            )}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export function MismatchTable({ items }: { items: Mismatch[] }) {
  if (items.length === 0) return <p className="text-[13px] text-[var(--ink-subtle)]">Every section's allocation matches its L-T-P plan.</p>;
  return (
    <table className="w-full text-[12.5px]">
      <thead className="text-left text-[var(--ink-subtle)] border-b border-[var(--ink)]">
        <tr>
          <th className="py-2 pr-3 font-medium">Section</th>
          <th className="py-2 pr-3 font-medium whitespace-nowrap">L-T-P</th>
          <th className="py-2 pr-3 font-medium">Component</th>
          <th className="py-2 pr-3 font-medium text-right">Planned</th>
          <th className="py-2 pr-3 font-medium text-right">Allocated</th>
          <th className="py-2 pr-3 font-medium">Problem</th>
          <th className="py-2 font-medium">Faculty</th>
        </tr>
      </thead>
      <tbody>
        {items.map((m, i) => (
          <tr key={`${m.offeringId}-${m.component}-${i}`} className="border-b border-[var(--hairline-soft)] align-top break-inside-avoid">
            <td className="py-2 pr-3 whitespace-nowrap">
              <span className="font-mono">{m.courseCode}</span> <span className="text-[var(--ink-subtle)]">{sectionLabel(m.section, m.semester)}</span>
            </td>
            <td className="py-2 pr-3 font-mono whitespace-nowrap">{m.ltp}</td>
            <td className="py-2 pr-3">{componentLabel(m.component)}</td>
            <td className="py-2 pr-3 text-right num">{m.plannedHours}h</td>
            <td className="py-2 pr-3 text-right num">{m.assignedHours}h</td>
            <td className="py-2 pr-3">
              <span className={m.type === 'MISSING_OWNER' || m.type === 'BATCH_COVERAGE_GAP' ? 'chip chip-bad' : 'chip chip-warn'}>{MISMATCH_LABEL[m.type]}</span>
              <div className="mt-1 text-[var(--ink-muted)]">{m.detail}</div>
            </td>
            <td className="py-2 text-[var(--ink-muted)]">{m.faculty}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

const PACE: Record<string, string> = { AHEAD: 'Ahead', ON_TRACK: 'On plan', BEHIND: 'Behind', NO_SYLLABUS: 'No syllabus' };

export function RemedialChip({ status }: { status: RemedialStatus }) {
  const cls = status === 'REQUIRED' ? 'chip chip-bad' : status === 'FILED' ? 'chip chip-warn' : status === 'COMPLETED' ? 'chip chip-ok' : 'text-[var(--ink-tertiary)]';
  return <span className={cls}>{REMEDIAL_LABEL[status]}</span>;
}

/** Sections grouped by year of study, the way the department plans its cohorts. */
export function SectionMatrix({ sections, required, onCourse }: { sections: SectionPerformance[]; required: number; onCourse?: (code: string) => void }) {
  const years = [...new Set(sections.map((s) => s.yearOfStudy))].sort();
  return (
    <div className="space-y-8">
      {years.map((y) => {
        const rows = sections.filter((s) => s.yearOfStudy === y);
        const students = rows.reduce((n, r) => n + r.students, 0);
        return (
          <div key={y} className="break-inside-avoid-page">
            <h4 className="text-[14px] font-medium">
              {YEAR_LABEL[y] ?? `Year ${y}`}
              <span className="ml-2 text-[12px] font-normal text-[var(--ink-subtle)] num">
                {rows.length} sections · {students.toLocaleString()} seats
              </span>
            </h4>
            <table className="mt-2 w-full text-[12px]">
              <thead className="text-left text-[var(--ink-subtle)] border-b border-[var(--ink)]">
                <tr>
                  <th className="py-1.5 pr-2 font-medium">Course</th>
                  <th className="py-1.5 pr-2 font-medium">Section</th>
                  <th className="py-1.5 pr-2 font-medium text-right">Students</th>
                  <th className="py-1.5 pr-2 font-medium">Faculty</th>
                  <th className="py-1.5 pr-2 font-medium text-right">Syllabus</th>
                  <th className="py-1.5 pr-2 font-medium">Pace</th>
                  <th className="py-1.5 pr-2 font-medium text-right">Held / due</th>
                  <th className="py-1.5 pr-2 font-medium text-right">CIE</th>
                  <th className="py-1.5 font-medium">Remedial</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.offeringId} className="border-b border-[var(--hairline-soft)] align-top break-inside-avoid">
                    <td className="py-1.5 pr-2">
                      {onCourse ? (
                        <button type="button" onClick={() => onCourse(r.courseCode)} className="font-mono hover:underline hover:text-[var(--accent)]">{r.courseCode}</button>
                      ) : (
                        <span className="font-mono">{r.courseCode}</span>
                      )}
                    </td>
                    <td className="py-1.5 pr-2 whitespace-nowrap">{sectionLabel(r.section)}</td>
                    <td className="py-1.5 pr-2 text-right num">{r.students || '—'}</td>
                    <td className="py-1.5 pr-2 text-[var(--ink-muted)] max-w-[200px]">{r.faculty || '—'}</td>
                    <td className="py-1.5 pr-2 text-right num">{r.totalTopics ? `${r.coveredTopics}/${r.totalTopics}` : '—'}</td>
                    <td className={`py-1.5 pr-2 whitespace-nowrap ${r.pacingStatus === 'BEHIND' ? 'text-[var(--warn-text)]' : ''}`}>
                      {PACE[r.pacingStatus]}{r.pacingStatus === 'BEHIND' || r.pacingStatus === 'AHEAD' ? ` (${r.topicDelta > 0 ? '+' : ''}${r.topicDelta})` : ''}
                    </td>
                    <td className="py-1.5 pr-2 text-right num">{r.scheduled ? `${r.held}/${r.scheduled}` : r.held || '—'}</td>
                    <td className="py-1.5 pr-2 text-right num">{r.assessmentsHeld}/{required}</td>
                    <td className="py-1.5"><RemedialChip status={r.remedialStatus} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        );
      })}
    </div>
  );
}

export function FacultyLines({ f, required }: { f: FacultyBreakdown; required: number }) {
  return (
    <table className="w-full text-[12px]">
      <thead className="text-left text-[var(--ink-subtle)] border-b border-[var(--ink)]">
        <tr>
          <th className="py-1.5 pr-2 font-medium">Year</th>
          <th className="py-1.5 pr-2 font-medium">Course</th>
          <th className="py-1.5 pr-2 font-medium">Section</th>
          <th className="py-1.5 pr-2 font-medium">Type</th>
          <th className="py-1.5 pr-2 font-medium text-right">Hours</th>
          <th className="py-1.5 pr-2 font-medium text-right">Students</th>
          <th className="py-1.5 pr-2 font-medium text-right">Held / due</th>
          <th className="py-1.5 pr-2 font-medium text-right">Syllabus</th>
          <th className="py-1.5 pr-2 font-medium text-right">CIE</th>
          <th className="py-1.5 font-medium">Remedial</th>
        </tr>
      </thead>
      <tbody>
        {f.lines.map((l, i) => (
          <tr key={`${l.offeringId}-${l.component}-${i}`} className="border-b border-[var(--hairline-soft)] break-inside-avoid">
            <td className="py-1.5 pr-2 num">{l.yearOfStudy}</td>
            <td className="py-1.5 pr-2"><span className="font-mono">{l.courseCode}</span> <span className="text-[var(--ink-subtle)]">{l.courseName}</span></td>
            <td className="py-1.5 pr-2 whitespace-nowrap">{l.section}</td>
            <td className="py-1.5 pr-2">{componentLabel(l.component)}</td>
            <td className="py-1.5 pr-2 text-right num">{l.weeklyHours}h</td>
            <td className="py-1.5 pr-2 text-right num">{l.students || '—'}</td>
            <td className={`py-1.5 pr-2 text-right num ${l.scheduled && l.held < l.scheduled * 0.8 ? 'text-[var(--danger-text)]' : ''}`}>
              {l.scheduled ? `${l.held}/${l.scheduled}` : l.held || '—'}
            </td>
            <td className="py-1.5 pr-2 text-right num">{l.pacingStatus === 'NO_SYLLABUS' ? '—' : `${l.coveragePct}%`}</td>
            <td className="py-1.5 pr-2 text-right num">{l.assessmentsHeld}/{required}</td>
            <td className="py-1.5"><RemedialChip status={l.remedialStatus} /></td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export function FacultyHeadline({ f }: { f: FacultyBreakdown }) {
  return (
    <dl className="grid grid-cols-2 sm:grid-cols-4 gap-x-6 gap-y-3 text-[12.5px]">
      <div>
        <dt className="text-[var(--ink-subtle)]">Weekly load</dt>
        <dd className="num font-medium">
          {f.totalHours}h <span className="font-normal text-[var(--ink-subtle)]">({f.lectureHours}L · {f.tutorialHours}T · {f.practicalHours}P{f.pblHours ? ` · ${f.pblHours} PBL` : ''})</span>
        </dd>
      </div>
      <div>
        <dt className="text-[var(--ink-subtle)]">Students taught</dt>
        <dd className="num font-medium">{f.studentsTaught || '—'} <span className="font-normal text-[var(--ink-subtle)]">across {f.years.length} year{f.years.length === 1 ? '' : 's'}</span></dd>
      </div>
      <div>
        <dt className="text-[var(--ink-subtle)]">Classes logged</dt>
        <dd className="num font-medium">
          {f.loggingRate === null ? '—' : `${f.loggingRate}%`}
          {f.scheduled > 0 && <span className="font-normal text-[var(--ink-subtle)]"> ({Math.min(f.held + f.cancelled, f.scheduled)} of {f.scheduled})</span>}
        </dd>
      </div>
      <div>
        <dt className="text-[var(--ink-subtle)]">Cancelled</dt>
        <dd className="num font-medium">{f.cancelled}</dd>
      </div>
    </dl>
  );
}

export function DailyLog({ logs }: { logs: LogEntry[] }) {
  if (logs.length === 0) return <p className="text-[13px] text-[var(--ink-subtle)]">No classes logged yet.</p>;
  return (
    <table className="w-full text-[12.5px]">
      <thead className="text-left text-[var(--ink-subtle)] border-b border-[var(--ink)]">
        <tr>
          <th className="py-2 pr-3 font-medium">Date</th>
          <th className="py-2 pr-3 font-medium">Class</th>
          <th className="py-2 pr-3 font-medium">Status</th>
          <th className="py-2 font-medium">Topics / reason</th>
        </tr>
      </thead>
      <tbody>
        {logs.map((l, i) => (
          <tr key={i} className="border-b border-[var(--hairline-soft)] align-top">
            <td className="py-2 pr-3 whitespace-nowrap num">
              {new Date(`${l.date}T00:00:00`).toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' })}
            </td>
            <td className="py-2 pr-3 whitespace-nowrap">
              <span className="font-mono">{l.courseCode}</span>{' '}
              <span className="text-[var(--ink-subtle)]">{[sectionLabel(l.section), batchLabel(l.subSection), componentLabel(l.sessionType)].filter(Boolean).join(' · ')}</span>
            </td>
            <td className="py-2 pr-3">
              {l.status === 'HELD' ? <span className="chip chip-ok">Held</span> : <span className="chip chip-warn">{l.status === 'CANCELLED' ? 'Cancelled' : 'Swapped'}</span>}
            </td>
            <td className="py-2 text-[var(--ink-muted)]">{l.status === 'HELD' ? l.topics || '—' : l.note || '—'}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
