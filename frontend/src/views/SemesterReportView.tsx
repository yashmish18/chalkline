import { useEffect, useState } from 'react';
import { Printer } from 'lucide-react';
import type { SemesterReport } from '../types/reports';
import type { IrregularityAuditDto } from '../types/tracking';
import { MISMATCH_LABEL, YEAR_LABEL } from '../types/reports';
import { sectionLabel } from '../lib/labels';
import { useAppConfig } from '../lib/appConfig';

/** The report names the worst cases and rolls up the rest, so it stays readable at any scale. */
const NAMED_LIMIT = 6;

/**
 * The semester report a coordinator takes into a department meeting. It reports exceptions, not
 * inventories: where the term stands, which cases need action now, which sections are irregular,
 * and what is still outstanding. Roster and per-section tables live in the working views.
 * Every figure comes from GET /api/reports/semester and GET /api/tracking/irregularities-audit.
 */
export function SemesterReportView() {
  const [report, setReport] = useState<SemesterReport | null>(null);
  const appConfig = useAppConfig();
  const [audit, setAudit] = useState<IrregularityAuditDto | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      try {
        const res = await fetch('/api/reports/semester', { credentials: 'include' });
        if (!res.ok) throw new Error('Could not build the report.');
        setReport(await res.json());
      } catch (e: unknown) {
        setError(e instanceof Error ? e.message : 'Could not build the report.');
      }
      // Irregularities are supporting evidence; the report is still readable without them.
      try {
        const res = await fetch('/api/tracking/irregularities-audit', { credentials: 'include' });
        if (res.ok) setAudit(await res.json());
      } catch {
        setAudit(null);
      }
    })();
  }, []);

  if (error) return <p className="text-[var(--danger-text)]">{error}</p>;
  if (!report) return <p className="text-[var(--ink-subtle)]">Preparing report…</p>;

  const { meta } = report;
  const year = meta.termStart ? new Date(meta.termStart).getFullYear() : new Date().getFullYear();
  const parity = meta.termName?.toLowerCase().includes('even') ? 'EVEN' : 'ODD';
  const docRef = `IET-ACAD-REP-${year}-${parity}`;
  const generated = new Date(meta.generatedAt).toLocaleString(undefined, { dateStyle: 'long', timeStyle: 'short' });
  const fmt = (d: string | null) => (d ? new Date(`${d}T00:00:00`).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }) : '—');

  const critical = report.criticalCases.filter((c) => c.severity === 'CRITICAL');
  const warnings = report.criticalCases.filter((c) => c.severity === 'WARNING');
  const warningCounts = countBy(warnings.map((w) => w.category));

  const criticalSorted = [...critical].sort((a, b) => b.studentsAffected - a.studentsAffected);
  const criticalShown = criticalSorted.slice(0, NAMED_LIMIT);
  const criticalRest = criticalSorted.length - criticalShown.length;

  const unowned = report.mismatches.filter((m) => m.type === 'MISSING_OWNER');
  const batchGaps = report.mismatches.filter((m) => m.type === 'BATCH_COVERAGE_GAP');
  const hoursShort = report.mismatches.filter((m) => m.type === 'HOURS_SHORT' || m.type === 'OVER_ALLOCATION');
  const irregular = [...unowned, ...batchGaps, ...hoursShort];
  const irregularShown = irregular.slice(0, NAMED_LIMIT);
  const irregularRest = irregular.length - irregularShown.length;

  const auditCounts = countBy((audit?.items ?? []).filter((i) => i.severity !== 'INFO').map((i) => i.issueType));
  const noSyllabus = (audit?.items ?? []).filter((i) => i.issueType === 'NO_SYLLABUS').length;

  const onPlan = report.sections.filter((s) => s.pacingStatus === 'ON_TRACK').length;
  const ahead = report.sections.filter((s) => s.pacingStatus === 'AHEAD').length;
  const behind = report.sections.filter((s) => s.pacingStatus === 'BEHIND');
  const noSyllabusSections = report.sections.filter((s) => s.pacingStatus === 'NO_SYLLABUS').length;
  const worstBehind = [...behind].sort((a, b) => a.topicDelta - b.topicDelta).slice(0, 3);

  const outstandingRemedial = report.sections.filter((s) => s.remedialStatus === 'REQUIRED');
  const scores = report.assessments.filter((a) => a.averagePct !== null);
  const weakest = [...report.sections].filter((s) => s.latestAveragePct !== null).sort((a, b) => (a.latestAveragePct ?? 0) - (b.latestAveragePct ?? 0)).slice(0, 3);

  const sectionsAtRisk = behind.length + unowned.length + batchGaps.length;
  const verdict =
    critical.length > 0
      ? `${critical.length} case${critical.length === 1 ? '' : 's'} need action within 48 hours.`
      : warnings.length > 0
        ? `No critical cases. ${warnings.length} warning${warnings.length === 1 ? '' : 's'} to clear this week.`
        : 'Nothing is on fire: no open cases this week.';

  return (
    <div className="report">
      <div className="mb-6 flex items-center justify-end gap-2 print:hidden">
        <button type="button" onClick={() => window.print()} className="btn btn-primary">
          <Printer className="w-4 h-4" /> Print or save as PDF
        </button>
      </div>

      <article className="mx-auto max-w-[860px] bg-white p-12 print:p-0 card print:border-0">
        <header className="border-b-2 border-[var(--ink)] pb-6">
          <p className="eyebrow">{appConfig.institutionName}</p>
          <h1 className="font-display mt-4 text-[32px] leading-tight font-medium">Semester teaching report</h1>
          <p className="mt-1 text-[13px] text-[var(--ink-subtle)]">Exceptions, mismatches and outstanding actions</p>
          <p className="mt-4 text-[15px] font-medium">{verdict}</p>
          <dl className="mt-4 grid grid-cols-2 gap-x-8 gap-y-1 text-[12.5px]">
            <Pair k="Reference" v={docRef} mono />
            <Pair k="Semester" v={meta.termName ?? 'Not set'} />
            <Pair k="Teaching weeks" v={`${fmt(meta.termStart)} – ${fmt(meta.termEnd)}`} />
            <Pair k="Position" v={meta.currentWeek ? `Week ${meta.currentWeek} of ${meta.totalWeeks}` : 'Not started'} />
            <Pair k="Generated" v={`${generated}, by the Faculty Dashboard`} />
            <Pair k="Open" v={`${critical.length} critical · ${warnings.length} warnings · ${report.mismatches.length} mismatches`} />
          </dl>
        </header>

        <Section n={1} title="Where the semester stands" note="Each measure against its target. Every line comes from logs, timetable, syllabi and assessments.">
          <div className="grid grid-cols-2 gap-3">
            {[...report.scorecard]
              .sort((a, b) => statusRank(a.status) - statusRank(b.status))
              .map((l) => (
                <Tile key={l.metric} label={l.metric} value={l.actual} target={l.target} status={l.status} note={l.note} />
              ))}
          </div>
        </Section>

        <Section n={2} title="Critical cases" note="Act on these within 48 hours. Each one names the owner and the action required.">
          {critical.length === 0 ? (
            <p className="text-[13px] text-[var(--ink-subtle)]">No critical case is open this week.</p>
          ) : (
            <div className="space-y-2">
              {criticalShown.map((c) => (
                <div key={c.ref} className="card p-4 break-inside-avoid border-l-2 border-l-[var(--danger-text)]">
                  <div className="flex items-start justify-between gap-4">
                    <div className="min-w-0">
                      <p className="text-[13px] font-medium">
                        <span className="font-mono text-[11.5px] text-[var(--danger-text)] mr-2">{c.ref}</span>
                        {c.category}
                        <span className="font-normal text-[var(--ink-subtle)]"> · {c.entity}</span>
                      </p>
                      <p className="mt-0.5 text-[12.5px] text-[var(--ink-muted)]">{c.description}</p>
                    </div>
                    <p className="shrink-0 text-right text-[12px]">
                      <span className="block font-medium">{c.facultyName}</span>
                      {c.studentsAffected > 0 && <span className="block text-[var(--ink-subtle)] num">{c.studentsAffected} students</span>}
                    </p>
                  </div>
                  <p className="mt-2 text-[12.5px] text-[var(--ink-muted)]">
                    <span className="text-[var(--ink-subtle)]">Required action: </span>{c.requiredAction}
                  </p>
                </div>
              ))}
              {criticalRest > 0 && (
                <p className="text-[12.5px] text-[var(--ink-subtle)]">
                  {criticalRest} further critical case{criticalRest === 1 ? '' : 's'} of lower impact — the full queue is in Compliance Tracking.
                </p>
              )}
            </div>
          )}

          <div className="mt-5">
            <h4 className="text-[13px] font-medium text-[var(--ink-subtle)]">Critical cases by type</h4>
            {critical.length === 0 ? (
              <p className="mt-1 text-[12.5px] text-[var(--ink-muted)]">None.</p>
            ) : (
              <ul className="mt-2 flex flex-wrap gap-x-6 gap-y-1.5 text-[12.5px]">
                {Object.entries(countBy(critical.map((c) => c.category))).map(([category, count]) => (
                  <li key={category} className="flex items-baseline gap-2">
                    <span className="num font-medium text-[var(--danger-text)]">{count}</span>
                    <span>{category}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <div className="mt-5">
            <h4 className="text-[13px] font-medium text-[var(--ink-subtle)]">Warnings this week, by type</h4>
            {warnings.length === 0 ? (
              <p className="mt-1 text-[12.5px] text-[var(--ink-muted)]">None.</p>
            ) : (
              <ul className="mt-2 flex flex-wrap gap-x-6 gap-y-1.5 text-[12.5px]">
                {Object.entries(warningCounts).map(([category, count]) => (
                  <li key={category} className="flex items-baseline gap-2">
                    <span className="num font-medium text-[var(--warn-text)]">{count}</span>
                    <span>{category}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </Section>

        <Section
          n={3}
          title="Irregular sections"
          note="Sections whose allocation, ownership or syllabus does not match what the course plans."
        >
          {irregular.length === 0 && auditCounts && Object.keys(auditCounts).length === 0 ? (
            <p className="text-[13px] text-[var(--ink-subtle)]">Every section matches its plan and has an owner.</p>
          ) : (
            <>
              {irregular.length === 0 ? (
                <p className="text-[13px] text-[var(--ink-subtle)]">Allocation matches the plan for every section.</p>
              ) : (
                <table className="w-full text-[12.5px]">
                  <thead className="text-left text-[var(--ink-subtle)] border-b border-[var(--ink)]">
                    <tr>
                      <th className="py-2 pr-3 font-medium">Section</th>
                      <th className="py-2 pr-3 font-medium whitespace-nowrap">L-T-P</th>
                      <th className="py-2 pr-3 font-medium text-right">Planned / allocated</th>
                      <th className="py-2 pr-3 font-medium">Irregularity</th>
                      <th className="py-2 font-medium">Owner</th>
                    </tr>
                  </thead>
                  <tbody>
                    {irregularShown.map((m, i) => (
                      <tr key={`${m.offeringId}-${m.component}-${i}`} className="border-b border-[var(--hairline-soft)] align-top break-inside-avoid">
                        <td className="py-2 pr-3 whitespace-nowrap">
                          <span className="font-mono">{m.courseCode}</span>{' '}
                          <span className="text-[var(--ink-subtle)]">{sectionLabel(m.section, m.semester)}</span>
                          <span className="block text-[11.5px] text-[var(--ink-tertiary)]">{YEAR_LABEL[m.yearOfStudy] ?? `Year ${m.yearOfStudy}`}</span>
                        </td>
                        <td className="py-2 pr-3 font-mono whitespace-nowrap">{m.ltp}</td>
                        <td className="py-2 pr-3 text-right num whitespace-nowrap">
                          {m.plannedHours}h / {m.assignedHours}h
                        </td>
                        <td className="py-2 pr-3">
                          <span className={m.type === 'MISSING_OWNER' || m.type === 'BATCH_COVERAGE_GAP' ? 'chip chip-bad' : 'chip chip-warn'}>
                            {MISMATCH_LABEL[m.type]}
                          </span>
                          <div className="mt-1 text-[var(--ink-muted)]">{m.detail}</div>
                        </td>
                        <td className="py-2 text-[var(--ink-muted)]">{m.faculty}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
              {irregularRest > 0 && (
                <p className="mt-2 text-[12.5px] text-[var(--ink-subtle)]">
                  {irregularRest} further irregular section{irregularRest === 1 ? '' : 's'} in the same categories.
                </p>
              )}

              {auditCounts && Object.keys(auditCounts).length > 0 && (
                <ul className="mt-4 flex flex-wrap gap-x-6 gap-y-1.5 text-[12.5px]">
                  {Object.entries(auditCounts).map(([type, count]) => (
                    <li key={type} className="flex items-baseline gap-2">
                      <span className="num font-medium text-[var(--warn-text)]">{count}</span>
                      <span>{IRREGULARITY_LABEL[type] ?? type}</span>
                    </li>
                  ))}
                </ul>
              )}
              {noSyllabus > 0 && (
                <p className="mt-3 text-[12.5px] text-[var(--ink-muted)]">
                  {noSyllabus} section{noSyllabus === 1 ? '' : 's'} run without a syllabus, so pacing cannot be measured for them.
                </p>
              )}
            </>
          )}
        </Section>

        <Section n={4} title="Coverage and pace" note="Counted across sections, with the three furthest behind called out by name.">
          <dl className="grid grid-cols-4 gap-x-6 gap-y-2 text-[12.5px]">
            <Figure k="On plan" v={String(onPlan)} />
            <Figure k="Ahead" v={String(ahead)} />
            <Figure k="Behind" v={String(behind.length)} tone={behind.length > 0 ? 'warn' : undefined} />
            <Figure k="Immeasurable" v={String(noSyllabusSections)} tone={noSyllabusSections > 0 ? 'warn' : undefined} />
          </dl>
          {worstBehind.length > 0 && (
            <div className="mt-4 space-y-1.5">
              {worstBehind.map((s) => (
                <p key={s.offeringId} className="text-[12.5px]">
                  <span className="font-mono">{s.courseCode}</span>{' '}
                  <span className="text-[var(--ink-subtle)]">{sectionLabel(s.section, s.semester)}</span>
                  <span className="text-[var(--warn-text)] num"> {Math.abs(s.topicDelta)} topics behind plan</span>
                  <span className="text-[var(--ink-subtle)]"> · {s.faculty || 'no owner'}</span>
                </p>
              ))}
            </div>
          )}
        </Section>

        <Section n={5} title="Assessment and remedial work">
          <div className="grid grid-cols-2 gap-x-10 gap-y-4 text-[12.5px]">
            <div>
              <h4 className="text-[13px] font-medium text-[var(--ink-subtle)]">Continuous assessment</h4>
              <div className="mt-2 space-y-2">
                {report.assessments.map((a) => (
                  <div key={a.index} className="flex items-center gap-3">
                    <span className="w-12 shrink-0">CIE-{a.index}</span>
                    <div className="bar-track h-2 flex-1">
                      <div className="bar-fill bg-[var(--ink-muted)]" style={{ width: `${a.sectionsTotal ? (a.sectionsDone / a.sectionsTotal) * 100 : 0}%` }} />
                    </div>
                    <span className="w-24 shrink-0 text-right num text-[var(--ink-muted)]">
                      {a.sectionsDone}/{a.sectionsTotal} sections
                    </span>
                  </div>
                ))}
              </div>
              <p className="mt-2 text-[12px] text-[var(--ink-subtle)]">Each section is to hold {meta.requiredAssessments} this semester.</p>
            </div>
            <div>
              <h4 className="text-[13px] font-medium text-[var(--ink-subtle)]">Remedial plans</h4>
              <p className="mt-2">
                <span className="num font-medium">{outstandingRemedial.length}</span>
                <span className="text-[var(--ink-subtle)]"> section{outstandingRemedial.length === 1 ? '' : 's'} need a plan</span>
                <span className="text-[var(--ink-subtle)]"> · </span>
                <span className="num font-medium">{report.remedialPlans.length}</span>
                <span className="text-[var(--ink-subtle)]"> filed</span>
              </p>
              {outstandingRemedial.length > 0 && (
                <ul className="mt-2 space-y-1">
                  {outstandingRemedial.slice(0, 5).map((s) => (
                    <li key={s.offeringId}>
                      <span className="font-mono">{s.courseCode}</span>{' '}
                      <span className="text-[var(--ink-subtle)]">{sectionLabel(s.section, s.semester)}</span>
                      <span className="text-[var(--ink-subtle)]"> · {s.faculty || 'no owner'}</span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>

          <div className="mt-5">
            <h4 className="text-[13px] font-medium text-[var(--ink-subtle)]">Assessment health</h4>
            {scores.length === 0 ? (
              <p className="mt-1 text-[12.5px] text-[var(--ink-muted)]">No scores recorded yet.</p>
            ) : (
              <>
                <p className="mt-1 text-[12.5px]">
                  Average across recorded assessments:{' '}
                  <span className="num font-medium">
                    {Math.round(scores.reduce((n, a) => n + (a.averagePct ?? 0), 0) / scores.length)}%
                  </span>
                </p>
                {weakest.length > 0 && (
                  <p className="mt-1 text-[12.5px] text-[var(--ink-muted)]">
                    Weakest sections:{' '}
                    {weakest
                      .map((s) => `${s.courseCode} ${sectionLabel(s.section, s.semester)} (${s.latestAveragePct}%)`)
                      .join(' · ')}
                  </p>
                )}
              </>
            )}
          </div>

          <p className="mt-4 text-[12px] text-[var(--ink-tertiary)]">
            {sectionsAtRisk} section{sectionsAtRisk === 1 ? '' : 's'} carry an open issue out of {report.sections.length} running this semester.
          </p>
        </Section>

        <footer className="mt-16 grid grid-cols-2 gap-16 text-[12px] text-[var(--ink-subtle)] break-inside-avoid">
          <div className="border-t border-[var(--ink)] pt-2">Department Coordinator</div>
          <div className="border-t border-[var(--ink)] pt-2">Head of Department</div>
        </footer>
      </article>
    </div>
  );
}

const IRREGULARITY_LABEL: Record<string, string> = {
  MISSING_FACULTY: 'sections with no faculty at all',
  UNASSIGNED_LAB: 'unowned practical batches',
  UNASSIGNED_TUTORIAL: 'unowned tutorial batches',
  NO_SYLLABUS: 'sections without a syllabus',
};

function statusRank(status: string): number {
  if (status === 'CRITICAL') return 0;
  if (status === 'WATCH') return 1;
  if (status === 'OK') return 2;
  return 3;
}

function countBy(values: string[]): Record<string, number> {
  return values.reduce<Record<string, number>>((acc, v) => ({ ...acc, [v]: (acc[v] ?? 0) + 1 }), {});
}

function Section({ n, title, note, children }: { n: number; title: string; note?: string; children: React.ReactNode }) {
  return (
    <section className="mt-10">
      <h2 className="font-display text-[20px] font-medium break-after-avoid">
        <span className="text-[var(--ink-tertiary)] mr-2 num">{n}.</span>{title}
      </h2>
      {note && <p className="mt-0.5 text-[12px] text-[var(--ink-subtle)] break-after-avoid">{note}</p>}
      <div className="mt-4">{children}</div>
    </section>
  );
}

function Tile({ label, value, target, status, note }: { label: string; value: string; target: string; status: string; note: string }) {
  const chip = status === 'CRITICAL' ? 'chip chip-bad' : status === 'WATCH' ? 'chip chip-warn' : status === 'OK' ? 'chip chip-ok' : 'chip';
  const word = status === 'CRITICAL' ? 'Critical' : status === 'WATCH' ? 'Watch' : status === 'OK' ? 'OK' : 'No data';
  return (
    <div className="card p-4 break-inside-avoid">
      <div className="flex items-start justify-between gap-2">
        <p className="text-[13px] font-medium">{label}</p>
        <span className={chip}>{word}</span>
      </div>
      <p className="mt-2 font-display text-[24px] leading-none num">{value}</p>
      <p className="mt-1 text-[12px] text-[var(--ink-tertiary)]">Target: {target}</p>
      <p className="mt-2 text-[12px] text-[var(--ink-subtle)]">{note}</p>
    </div>
  );
}

function Figure({ k, v, tone }: { k: string; v: string; tone?: 'warn' }) {
  return (
    <div>
      <dt className="text-[var(--ink-subtle)]">{k}</dt>
      <dd className={`mt-0.5 font-display text-[24px] leading-none num ${tone === 'warn' ? 'text-[var(--warn-text)]' : ''}`}>{v}</dd>
    </div>
  );
}

function Pair({ k, v, mono = false }: { k: string; v: string; mono?: boolean }) {
  return (
    <div className="flex gap-3">
      <dt className="w-40 shrink-0 text-[var(--ink-subtle)]">{k}</dt>
      <dd className={mono ? 'font-mono' : ''}>{v}</dd>
    </div>
  );
}
