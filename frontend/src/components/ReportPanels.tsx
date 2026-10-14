import { useEffect, useState } from 'react';
import { X } from 'lucide-react';
import type { CourseReport, FacultyReport } from '../types/reports';
import { CriticalCasesTable, DailyLog, FacultyHeadline, FacultyLines, MismatchTable, SectionMatrix } from './ReportParts';

function useReport<T>(url: string | null) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    if (!url) return;
    setData(null);
    setError(null);
    fetch(url, { credentials: 'include' })
      .then(async (res) => {
        if (!res.ok) throw new Error(res.status === 403 ? 'You do not have access to this report.' : 'Could not load the report.');
        setData(await res.json());
      })
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not load the report.'));
  }, [url]);
  return { data, error };
}

/** A faculty member's performance: load, every course across years, open cases and the daily class log. */
export function FacultyReportBody({ facultyId, self = false }: { facultyId?: number; self?: boolean }) {
  const { data, error } = useReport<FacultyReport>(self ? '/api/reports/faculty/me' : `/api/reports/faculty/${facultyId}`);
  if (error) return <p className="text-[var(--danger-text)] text-[13px]">{error}</p>;
  if (!data) return <p className="text-[var(--ink-subtle)] text-[13px]">Loading…</p>;
  const f = data.summary;
  return (
    <div className="space-y-10">
      <div>
        <h2 className="font-display text-[28px] font-medium leading-tight">{f.name}</h2>
        <p className="text-[13px] text-[var(--ink-subtle)]">{[f.designation, f.department, data.meta.termName].filter(Boolean).join(' · ')}</p>
        <div className="mt-5"><FacultyHeadline f={f} /></div>
      </div>
      <section>
        <h3 className="font-display text-[20px] font-medium mb-3">Courses taught</h3>
        <FacultyLines f={f} required={data.meta.requiredAssessments} />
      </section>
      <section>
        <h3 className="font-display text-[20px] font-medium mb-3">
          Open cases <span className="text-[14px] text-[var(--ink-tertiary)] num">{data.cases.length}</span>
        </h3>
        <CriticalCasesTable cases={data.cases} compact />
      </section>
      <section>
        <h3 className="font-display text-[20px] font-medium mb-3">Daily class log</h3>
        <DailyLog logs={data.recentLogs} />
      </section>
    </div>
  );
}

/** Every section of one course: students, faculty, pace, assessments and mismatches. */
export function CourseReportBody({ courseCode }: { courseCode: string }) {
  const { data, error } = useReport<CourseReport>(`/api/reports/courses/${encodeURIComponent(courseCode)}`);
  if (error) return <p className="text-[var(--danger-text)] text-[13px]">{error}</p>;
  if (!data) return <p className="text-[var(--ink-subtle)] text-[13px]">Loading…</p>;
  return (
    <div className="space-y-10">
      <div>
        <p className="font-mono text-[13px] text-[var(--ink-subtle)]">{data.courseCode} · L-T-P {data.ltp}</p>
        <h2 className="font-display text-[28px] font-medium leading-tight">{data.courseName}</h2>
        <p className="mt-2 text-[13px] text-[var(--ink-muted)] num">
          {data.sections.length} sections · {data.totalStudents ? `${data.totalStudents.toLocaleString()} students` : 'strength not recorded'}
        </p>
      </div>
      <section>
        <h3 className="font-display text-[20px] font-medium mb-3">Sections</h3>
        <SectionMatrix sections={data.sections} required={data.meta.requiredAssessments} />
      </section>
      <section>
        <h3 className="font-display text-[20px] font-medium mb-3">Allocation mismatches</h3>
        <MismatchTable items={data.mismatches} />
      </section>
    </div>
  );
}

/** Full-height side panel for a drill-down. */
export function ReportPanel({ onClose, children }: { onClose: () => void; children: React.ReactNode }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);
  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-slate-900/40" role="dialog" aria-modal="true" onClick={onClose}>
      <div className="h-full w-full max-w-4xl overflow-y-auto bg-[var(--paper)] px-8 py-8 shadow-lg" onClick={(e) => e.stopPropagation()}>
        <div className="flex justify-end">
          <button type="button" onClick={onClose} className="btn btn-quiet w-8 px-0" aria-label="Close">
            <X className="w-4 h-4" />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}
