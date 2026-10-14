import { useState, useEffect, useCallback, useMemo } from 'react';
import { OverviewSummaryDto, ComplianceMatrixDto, FacultyPerformanceStatus } from '../types/tracking';
import { RefreshCw, Check, AlertTriangle, CheckCircle2, Settings2 } from 'lucide-react';
import { sectionLabel } from '../lib/labels';
import { SemesterSettingsModal } from '../components/SemesterSettingsModal';
import { CourseReportBody, FacultyReportBody, ReportPanel } from '../components/ReportPanels';
import { CriticalCasesTable, MismatchTable } from '../components/ReportParts';
import type { CriticalCase, SemesterReport } from '../types/reports';

type Tab = 'performance' | 'cases' | 'mismatches' | 'milestones';
type RankingItem = OverviewSummaryDto['worstFirstCoverage'][number];
type FacultyRow = OverviewSummaryDto['facultyPerformance'][number];

/** One colour per faculty status, shared by the status chart, its legend and the table chips. */
const STATUS: Record<FacultyPerformanceStatus, { label: string; color: string; chip: string }> = {
  NOT_LOGGING: { label: 'Not logging', color: 'var(--danger)', chip: 'chip chip-bad' },
  BEHIND: { label: 'Behind pace', color: 'var(--warn)', chip: 'chip chip-warn' },
  ON_TRACK: { label: 'On track', color: 'var(--accent)', chip: 'chip chip-ok' },
  NO_TIMETABLE: { label: 'No timetable', color: 'var(--hairline-tertiary)', chip: 'chip' },
  NO_DATA: { label: 'Nothing due yet', color: 'var(--surface-5)', chip: 'chip' },
  NOT_STARTED: { label: 'Semester not started', color: 'var(--surface-5)', chip: 'chip' },
};
const STATUS_ORDER: FacultyPerformanceStatus[] = ['NOT_LOGGING', 'BEHIND', 'ON_TRACK', 'NO_TIMETABLE', 'NO_DATA', 'NOT_STARTED'];

function lastLog(days: number | null): string {
  if (days === null) return 'Never';
  if (days <= 0) return 'Today';
  if (days === 1) return 'Yesterday';
  return `${days} days ago`;
}

function formatDate(iso: string | null): string {
  return iso ? new Date(`${iso}T00:00:00`).toLocaleDateString(undefined, { day: 'numeric', month: 'short' }) : '';
}

export function CoordinatorOverviewView() {
  const [tab, setTab] = useState<Tab>('performance');
  const [summary, setSummary] = useState<OverviewSummaryDto | null>(null);
  const [matrix, setMatrix] = useState<ComplianceMatrixDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const [remindingId, setRemindingId] = useState<string | null>(null);
  const [showAllSections, setShowAllSections] = useState(false);
  const [openFacultyId, setOpenFacultyId] = useState<number | null>(null);
  const [editingTerm, setEditingTerm] = useState(false);
  const [report, setReport] = useState<SemesterReport | null>(null);
  const [openCourse, setOpenCourse] = useState<string | null>(null);
  const [showAllFaculty, setShowAllFaculty] = useState(false);
  const [cohortFilter, setCohortFilter] = useState<number | null>(null);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [sumRes, matRes] = await Promise.all([
        fetch('/api/tracking/overview-summary', { credentials: 'include' }),
        fetch('/api/tracking/compliance-matrix', { credentials: 'include' }),
      ]);
      if (!sumRes.ok) throw new Error('Could not load the overview. Check that the backend is running.');
      setSummary(await sumRes.json());
      setMatrix(matRes.ok ? await matRes.json() : null);
      fetch('/api/reports/semester', { credentials: 'include' })
        .then((r) => (r.ok ? r.json() : null))
        .then(setReport)
        .catch(() => setReport(null));
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Could not load the overview.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const remind = async (c: CriticalCase) => {
    if (!c.facultyId) return;
    setRemindingId(c.ref);
    try {
      const res = await fetch('/api/notifications/remind-faculty', {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          facultyId: c.facultyId,
          title: `Action required: ${c.category} (${c.entity})`,
          message: `${c.description}. ${c.requiredAction}`,
        }),
      });
      if (!res.ok) throw new Error('Reminder could not be sent.');
      setToast(`Reminder sent to ${c.facultyName} (in-app and email).`);
      setTimeout(() => setToast(null), 4000);
    } catch (err: unknown) {
      setToast(err instanceof Error ? err.message : 'Reminder could not be sent.');
    } finally {
      setRemindingId(null);
    }
  };

  const rawRanking = summary?.worstFirstCoverage ?? [];
  const ranking = useMemo(() => {
    if (cohortFilter === null) return rawRanking;
    return rawRanking.filter((r) => (r.yearOfStudy || Math.max(1, Math.floor((r.semester + 1) / 2))) === cohortFilter);
  }, [rawRanking, cohortFilter]);
  const behind = useMemo(() => ranking.filter((r) => r.syllabusConfigured && r.pacingStatus === 'BEHIND'), [ranking]);
  const term = summary?.term;
  const kpis = summary?.kpis;
  const dash = loading ? '…' : '—';

  return (
    <div className="space-y-10">
      {/* Semester line + tabs */}
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div className="flex items-center gap-3 text-[14px]">
          {term?.name ? (
            <>
              <span className="font-medium">{term.name}</span>
              <span className="text-[var(--ink-subtle)] num">
                {term.started ? `Week ${term.currentWeek} of ${term.totalWeeks}` : `Starts ${formatDate(term.startDate)}`}
                {' · '}
                {formatDate(term.startDate)} – {formatDate(term.endDate)}
              </span>
            </>
          ) : (
            <span className="text-[var(--warn-text)]">No semester set — set the dates so pacing and logging can be measured.</span>
          )}
          <button type="button" onClick={() => setEditingTerm(true)} className="btn btn-quiet h-7 px-2 text-[12px]">
            <Settings2 className="w-3.5 h-3.5" /> Semester
          </button>
        </div>
        <div className="flex items-center gap-2">
          <div role="tablist" className="inline-flex rounded-lg border border-[var(--hairline)] bg-white p-0.5 text-[13px]">
            {([
              ['performance', 'Performance'],
              ['cases', `Cases${report ? ` · ${report.criticalCases.length}` : ''}`],
              ['mismatches', `Mismatches${report ? ` · ${report.mismatches.length}` : ''}`],
              ['milestones', 'Milestones'],
            ] as const).map(([id, label]) => (
              <button
                key={id}
                type="button"
                role="tab"
                aria-selected={tab === id}
                onClick={() => setTab(id)}
                className={`px-3 h-7 rounded-md ${tab === id ? 'bg-[var(--ink)] text-white' : 'text-[var(--ink-muted)] hover:text-[var(--ink)]'}`}
              >
                {label}
              </button>
            ))}
          </div>
          <button type="button" onClick={fetchData} className="btn w-8 px-0" aria-label="Refresh">
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {toast && (
        <div role="status" className="flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-2.5 text-[13px] text-emerald-800">
          <CheckCircle2 className="w-4 h-4" /> {toast}
        </div>
      )}
      {error && (
        <div role="alert" className="flex items-center justify-between rounded-lg border border-rose-200 bg-rose-50 px-4 py-2.5 text-[13px] text-rose-800">
          <span className="inline-flex items-center gap-2"><AlertTriangle className="w-4 h-4" /> {error}</span>
          <button type="button" onClick={fetchData} className="font-medium underline">Retry</button>
        </div>
      )}

      {tab === 'performance' && (
        <>
          {/* Four numbers, lots of air */}
          <section className="grid grid-cols-2 lg:grid-cols-4 gap-x-10 gap-y-8">
            <BigNumber
              label="Classes logged"
              value={kpis?.loggingRatePercentage != null ? `${kpis.loggingRatePercentage}%` : dash}
              note={kpis?.loggingRatePercentage != null ? 'of classes the timetable scheduled so far' : 'measured once a timetable is uploaded'}
            />
            <BigNumber
              label="Syllabus covered"
              value={kpis?.averageCoveragePercentage != null ? `${kpis.averageCoveragePercentage}%` : dash}
              note={kpis?.averageCoveragePercentage != null ? 'average across paced sections' : 'measured once syllabi are loaded'}
            />
            <BigNumber
              label="Sections behind plan"
              value={kpis ? String(kpis.coursesBehindPacing) : dash}
              note={`of ${ranking.length} sections`}
              tone={kpis && kpis.coursesBehindPacing > 0 ? 'warn' : undefined}
            />
            <BigNumber
              label="Course seats"
              value={kpis?.totalStudents ? kpis.totalStudents.toLocaleString() : dash}
              note={kpis?.totalStudents ? 'section strengths added up across courses' : 'add Section Students to the sheet'}
            />
          </section>

          {/* Charts */}
          <section className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <ChartCard title="Faculty status" caption={`${summary?.facultyPerformance.length ?? 0} faculty`}>
              <StatusBar rows={summary?.facultyPerformance ?? []} />
            </ChartCard>
            <ChartCard title="Classes per week" caption="held · cancelled">
              <WeeklyBars points={summary?.conductTrajectory ?? []} />
            </ChartCard>
            <ChartCard title="Weekly load" caption="hours per faculty, incl. PBL">
              <LoadHistogram rows={summary?.facultyPerformance ?? []} />
            </ChartCard>
          </section>

          {/* The five most urgent cases; the Cases tab has the full ledger */}
          {report && report.criticalCases.length > 0 && (
            <section>
              <SectionTitle
                title="Most urgent"
                count={report.criticalCases.length}
                action={
                  <button type="button" onClick={() => setTab('cases')} className="text-[13px] text-[var(--accent)] hover:underline">
                    All cases
                  </button>
                }
              />
              <div className="card px-5 py-4 overflow-x-auto">
                <CriticalCasesTable cases={report.criticalCases.slice(0, 5)} onFaculty={setOpenFacultyId} onRemind={remind} busyRef={remindingId} />
              </div>
            </section>
          )}

          {/* Priority: only sections behind their plan */}
          <section>
            <SectionTitle
              title="Sections behind plan"
              count={behind.length}
              action={
                ranking.length > 0 && (
                  <button type="button" onClick={() => setShowAllSections((v) => !v)} className="text-[13px] text-[var(--accent)] hover:underline">
                    {showAllSections ? 'Show only behind' : `Show all ${ranking.length}`}
                  </button>
                )
              }
            />
            {/* Cohort filter pills */}
            <div className="mb-4 flex items-center gap-2 flex-wrap">
              <span className="text-[12px] text-[var(--ink-subtle)]">Cohort:</span>
              {[
                { label: 'All Cohorts', val: null },
                { label: '1st Year', val: 1 },
                { label: '2nd Year', val: 2 },
                { label: '3rd Year', val: 3 },
                { label: '4th Year', val: 4 },
              ].map((c) => (
                <button
                  key={c.label}
                  type="button"
                  onClick={() => setCohortFilter(c.val)}
                  className={`px-2.5 py-1 text-[12px] rounded-md transition-colors ${
                    cohortFilter === c.val
                      ? 'bg-[var(--ink)] text-white font-medium'
                      : 'bg-white text-[var(--ink-muted)] border border-[var(--hairline)] hover:border-[var(--ink-muted)]'
                  }`}
                >
                  {c.label}
                </button>
              ))}
            </div>
            {(showAllSections ? ranking : behind).length === 0 ? (
              <p className="card px-5 py-10 text-center text-[14px] text-[var(--ink-subtle)]">
                {kpis && kpis.offeringsWithoutSyllabus === ranking.length && ranking.length > 0
                  ? 'No syllabus is loaded yet, so no section can fall behind. Load syllabi from Course Allocations.'
                  : 'Every section is on or ahead of its plan.'}
              </p>
            ) : (
              <ol className="card divide-y divide-[var(--hairline-soft)]">
                {(showAllSections ? ranking : behind).map((item) => (
                  <SectionRow key={item.offeringId} item={item} onCourse={setOpenCourse} />
                ))}
              </ol>
            )}
          </section>

          {/* Faculty accountability */}
          <section>
            <SectionTitle title="Faculty" count={summary?.facultyPerformance.length ?? 0} />
            {!summary || summary.facultyPerformance.length === 0 ? (
              <p className="card px-5 py-10 text-center text-[14px] text-[var(--ink-subtle)]">No allocations yet.</p>
            ) : (
              <div className="card overflow-x-auto">
                <table className="min-w-full text-[13px]">
                  <thead className="text-left text-[var(--ink-subtle)] border-b border-[var(--hairline)]">
                    <tr>
                      <th className="px-5 py-3 font-medium">Name</th>
                      <th className="px-3 py-3 font-medium">Status</th>
                      <th className="px-3 py-3 font-medium text-right">Load</th>
                      <th className="px-3 py-3 font-medium w-48">Classes logged</th>
                      <th className="px-3 py-3 font-medium">Last log</th>
                      <th className="px-5 py-3 font-medium">Syllabus</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[var(--hairline-soft)]">
                    {(showAllFaculty ? summary.facultyPerformance : summary.facultyPerformance.slice(0, 12)).map((row) => (
                      <FacultyTableRow key={row.facultyId} row={row} onOpen={() => setOpenFacultyId(row.facultyId)} />
                    ))}
                  </tbody>
                </table>
                {summary.facultyPerformance.length > 12 && (
                  <div className="px-5 py-3 border-t border-[var(--hairline)]">
                    <button type="button" onClick={() => setShowAllFaculty((v) => !v)} className="text-[13px] text-[var(--accent)] hover:underline">
                      {showAllFaculty ? 'Show fewer' : `Show all ${summary.facultyPerformance.length}`}
                    </button>
                  </div>
                )}
              </div>
            )}
          </section>
        </>
      )}

      {tab === 'milestones' && (
        <section>
          <SectionTitle title="Milestones" caption="Each column is a deadline type; a cell counts that type's submissions for the section." />
          {!matrix || matrix.rows.length === 0 ? (
            <p className="card px-5 py-10 text-center text-[14px] text-[var(--ink-subtle)]">No sections yet.</p>
          ) : (
            <div className="card overflow-x-auto max-h-[640px]">
              <table className="min-w-full text-[13px]">
                <thead className="sticky top-0 bg-white text-left text-[var(--ink-subtle)] border-b border-[var(--hairline)]">
                  <tr>
                    <th className="px-5 py-3 font-medium">Section</th>
                    <th className="px-3 py-3 font-medium">Faculty</th>
                    {matrix.checkpoints.map((cp) => (
                      <th key={cp.key} className="px-3 py-3 font-medium text-center" title={cp.description}>{cp.name}</th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-[var(--hairline-soft)]">
                  {matrix.rows.map((row) => (
                    <tr key={row.offeringId}>
                      <td className="px-5 py-2.5 whitespace-nowrap">
                        <span className="font-mono">{row.courseCode}</span>
                        <span className="ml-2 text-[var(--ink-subtle)]">{sectionLabel(row.section)}</span>
                      </td>
                      <td className="px-3 py-2.5 text-[var(--ink-muted)] max-w-[220px] truncate">{row.facultyName}</td>
                      {row.cells.map((cell) => (
                        <td key={cell.checkpointKey} className="px-3 py-2.5 text-center" title={cell.note}>
                          {cell.status === 'DONE' && <span className="chip chip-ok"><Check className="w-3 h-3" /> Done</span>}
                          {cell.status === 'OVERDUE' && <span className="chip chip-bad">Overdue</span>}
                          {cell.status === 'PENDING' && <span className="text-[12px] text-[var(--ink-tertiary)]">{cell.note}</span>}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}

      {tab === 'cases' && (
        <section>
          <SectionTitle
            title="Critical cases"
            caption="Not logging, overdue work, sections behind plan, poor assessment results and cancellations — most urgent first."
          />
          <div className="card px-5 py-4 overflow-x-auto">
            {report ? <CriticalCasesTable cases={report.criticalCases} onFaculty={setOpenFacultyId} onRemind={remind} busyRef={remindingId} /> : <p className="text-[13px] text-[var(--ink-subtle)]">Loading…</p>}
          </div>
        </section>
      )}

      {tab === 'mismatches' && (
        <section>
          <SectionTitle
            title="Allocation mismatches"
            caption="Sections whose allocated hours or owners do not match the course's L-T-P plan. Fix the plan or the load sheet, then re-import."
          />
          <div className="card px-5 py-4 overflow-x-auto">
            {report ? <MismatchTable items={report.mismatches} /> : <p className="text-[13px] text-[var(--ink-subtle)]">Loading…</p>}
          </div>
        </section>
      )}

      {openFacultyId !== null && (
        <ReportPanel onClose={() => setOpenFacultyId(null)}>
          <FacultyReportBody facultyId={openFacultyId} />
        </ReportPanel>
      )}
      {openCourse && (
        <ReportPanel onClose={() => setOpenCourse(null)}>
          <CourseReportBody courseCode={openCourse} />
        </ReportPanel>
      )}
      {editingTerm && (
        <SemesterSettingsModal
          term={{ name: term?.name ?? null, startDate: term?.startDate ?? null, endDate: term?.endDate ?? null }}
          onClose={() => setEditingTerm(false)}
          onSaved={fetchData}
        />
      )}
    </div>
  );
}

function BigNumber({ label, value, note, tone }: { label: string; value: string; note: string; tone?: 'warn' }) {
  return (
    <div>
      <div className="text-[13px] text-[var(--ink-subtle)]">{label}</div>
      <div className={`mt-2 font-display text-[44px] leading-none num ${tone === 'warn' ? 'text-[var(--warn-text)]' : ''}`}>{value}</div>
      <div className="mt-2.5 text-[12px] text-[var(--ink-tertiary)]">{note}</div>
    </div>
  );
}

function SectionTitle({ title, count, caption, action }: { title: string; count?: number; caption?: string; action?: React.ReactNode }) {
  return (
    <div className="mb-3 flex items-end justify-between gap-4">
      <div>
        <h2 className="font-display text-[22px] font-medium">
          {title}
          {count !== undefined && <span className="ml-2 text-[15px] text-[var(--ink-tertiary)] num">{count}</span>}
        </h2>
        {caption && <p className="mt-0.5 text-[13px] text-[var(--ink-subtle)]">{caption}</p>}
      </div>
      {action}
    </div>
  );
}

function ChartCard({ title, caption, children }: { title: string; caption: string; children: React.ReactNode }) {
  return (
    <div className="card px-5 pt-5 pb-4 flex flex-col">
      <div className="flex items-baseline justify-between">
        <h3 className="text-[14px] font-medium">{title}</h3>
        <span className="text-[12px] text-[var(--ink-tertiary)]">{caption}</span>
      </div>
      <div className="mt-5 flex-1">{children}</div>
    </div>
  );
}

function StatusBar({ rows }: { rows: FacultyRow[] }) {
  const counts = STATUS_ORDER.map((s) => ({ status: s, n: rows.filter((r) => r.status === s).length })).filter((c) => c.n > 0);
  const total = rows.length || 1;
  if (rows.length === 0) return <p className="text-[13px] text-[var(--ink-subtle)]">No faculty yet.</p>;
  return (
    <div>
      <div className="flex h-3 rounded-full overflow-hidden" role="img" aria-label={counts.map((c) => `${STATUS[c.status].label}: ${c.n}`).join(', ')}>
        {counts.map((c) => (
          <div key={c.status} style={{ width: `${(c.n / total) * 100}%`, background: STATUS[c.status].color }} title={`${STATUS[c.status].label}: ${c.n}`} />
        ))}
      </div>
      <ul className="mt-5 space-y-2">
        {counts.map((c) => (
          <li key={c.status} className="flex items-center justify-between text-[13px]">
            <span className="inline-flex items-center gap-2">
              <span className="w-2 h-2 rounded-sm" style={{ background: STATUS[c.status].color }} /> {STATUS[c.status].label}
            </span>
            <span className="num text-[var(--ink-muted)]">{c.n}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function WeeklyBars({ points }: { points: OverviewSummaryDto['conductTrajectory'] }) {
  if (points.length === 0) {
    return <p className="text-[13px] text-[var(--ink-subtle)]">Appears once classes are logged.</p>;
  }
  const recent = points.slice(-12);
  const max = Math.max(...recent.map((p) => p.heldCount + p.cancelledCount), 1);
  return (
    <div>
      <div className="flex items-end gap-1.5 h-32" role="img" aria-label="Classes held and cancelled per week">
        {recent.map((p) => (
          <div key={p.weekNumber} className="flex-1 flex flex-col justify-end h-full" title={`Week ${p.weekNumber}: ${p.heldCount} held, ${p.cancelledCount} cancelled`}>
            <div style={{ height: `${(p.cancelledCount / max) * 100}%`, background: 'var(--warn)' }} className="rounded-t-sm" />
            <div style={{ height: `${(p.heldCount / max) * 100}%`, background: 'var(--accent)' }} className={p.cancelledCount ? '' : 'rounded-t-sm'} />
          </div>
        ))}
      </div>
      <div className="mt-2 flex justify-between text-[11px] text-[var(--ink-tertiary)] num">
        <span>Wk {recent[0].weekNumber}</span>
        <span>Wk {recent[recent.length - 1].weekNumber}</span>
      </div>
    </div>
  );
}

function LoadHistogram({ rows }: { rows: FacultyRow[] }) {
  const buckets = [
    { label: '< 8h', test: (h: number) => h < 8 },
    { label: '8–12h', test: (h: number) => h >= 8 && h < 12 },
    { label: '12–16h', test: (h: number) => h >= 12 && h < 16 },
    { label: '16h +', test: (h: number) => h >= 16 },
  ].map((b) => ({ ...b, n: rows.filter((r) => b.test(r.weeklyHours)).length }));
  const max = Math.max(...buckets.map((b) => b.n), 1);
  if (rows.length === 0) return <p className="text-[13px] text-[var(--ink-subtle)]">No allocations yet.</p>;
  return (
    <ul className="space-y-3">
      {buckets.map((b) => (
        <li key={b.label} className="grid grid-cols-[52px_1fr_28px] items-center gap-3 text-[13px]">
          <span className="text-[var(--ink-subtle)] num">{b.label}</span>
          <div className="bar-track h-2">
            <div className="bar-fill" style={{ width: `${(b.n / max) * 100}%`, background: 'var(--ink-muted)' }} />
          </div>
          <span className="num text-right">{b.n}</span>
        </li>
      ))}
    </ul>
  );
}

function SectionRow({ item, onCourse }: { item: RankingItem; onCourse: (code: string) => void }) {
  const isBehind = item.syllabusConfigured && item.pacingStatus === 'BEHIND';
  const yearNum = item.yearOfStudy || Math.max(1, Math.floor((item.semester + 1) / 2));
  return (
    <li className="px-5 py-4 grid grid-cols-1 md:grid-cols-[minmax(0,1fr)_220px] gap-x-8 gap-y-2 items-center">
      <div className="min-w-0">
        <div className="flex items-baseline gap-2 flex-wrap">
          <button type="button" onClick={() => onCourse(item.courseCode)} className="font-mono text-[13px] hover:underline hover:text-[var(--accent)]">
            {item.courseCode}
          </button>
          <span className="text-[14px] font-medium truncate">{item.courseName}</span>
          <span className="text-[11px] px-1.5 py-0.5 rounded bg-[var(--surface-4)] text-[var(--ink-muted)]">
            Year {yearNum}
          </span>
          {item.studentCount && item.studentCount > 0 ? (
            <span className="text-[12px] text-[var(--ink-tertiary)] num">
              · {item.studentCount} students
            </span>
          ) : null}
        </div>
        <div className="mt-0.5 text-[13px] text-[var(--ink-subtle)] truncate">
          {sectionLabel(item.section, item.semester)} · {item.facultyName}
        </div>
      </div>
      {item.syllabusConfigured ? (
        <div>
          <div className="flex justify-between text-[12px] num">
            <span>{item.coveragePercentage}% covered</span>
            <span className={isBehind ? 'text-[var(--warn-text)]' : 'text-[var(--ink-subtle)]'}>
              {item.topicDelta < 0 ? `${-item.topicDelta} topics behind` : item.topicDelta > 0 ? `${item.topicDelta} ahead` : 'on plan'}
            </span>
          </div>
          <div className="relative mt-1.5 bar-track">
            <div className="bar-fill" style={{ width: `${Math.max(item.coveragePercentage, 1)}%`, background: isBehind ? 'var(--warn)' : 'var(--accent)' }} />
            {item.expectedPercentage > 0 && (
              <span className="absolute -top-1 -bottom-1 w-px bg-[var(--ink)]" style={{ left: `${Math.min(item.expectedPercentage, 100)}%` }} title={`Plan: ${item.expectedPercentage}%`} />
            )}
          </div>
        </div>
      ) : (
        <span className="text-[13px] text-[var(--ink-tertiary)]">No syllabus loaded</span>
      )}
    </li>
  );
}

function FacultyTableRow({ row, onOpen }: { row: FacultyRow; onOpen: () => void }) {
  const status = STATUS[row.status];
  return (
    <tr className="hover:bg-[var(--surface-3)]">
      <td className="px-5 py-3 whitespace-nowrap">
        <button type="button" onClick={onOpen} className="font-medium hover:text-[var(--accent)] hover:underline text-left">{row.facultyName}</button>
      </td>
      <td className="px-3 py-3"><span className={status.chip}>{status.label}</span></td>
      <td className="px-3 py-3 text-right num whitespace-nowrap">
        {row.weeklyHours}h<span className="text-[var(--ink-tertiary)]">{row.pblHours > 0 ? ` · ${row.pblHours} PBL` : ''}</span>
      </td>
      <td className="px-3 py-3">
        {row.loggingRate === null ? (
          <span className="text-[var(--ink-tertiary)]">—</span>
        ) : (
          <div className="flex items-center gap-3">
            <div className="bar-track flex-1">
              <div className="bar-fill" style={{ width: `${row.loggingRate}%`, background: row.loggingRate < 50 ? 'var(--danger)' : row.loggingRate < 80 ? 'var(--warn)' : 'var(--accent)' }} />
            </div>
            <span className="num w-20 text-right text-[var(--ink-muted)]">
              {Math.min(row.sessionsHeld + row.sessionsCancelled, row.scheduledSoFar)}/{row.scheduledSoFar}
            </span>
          </div>
        )}
      </td>
      <td className={`px-3 py-3 whitespace-nowrap ${row.status === 'NOT_LOGGING' ? 'text-[var(--danger-text)]' : 'text-[var(--ink-muted)]'}`}>
        {lastLog(row.daysSinceLastLog)}
      </td>
      <td className="px-5 py-3 whitespace-nowrap text-[var(--ink-muted)]">
        {row.averageCoverage === null ? <span className="text-[var(--ink-tertiary)]">—</span> : (
          <>
            <span className="num">{row.averageCoverage}%</span>
            {row.offeringsBehind > 0 && <span className="text-[var(--warn-text)]"> · {row.offeringsBehind} behind</span>}
          </>
        )}
      </td>
    </tr>
  );
}
