import { useQuery } from '@tanstack/react-query';
import {
  FacultyProfileDto,
  FacultySubmissionRowDto,
} from '../types/directory';
import {
  X,
  Mail,
  Building2,
  GraduationCap,
  BookOpen,
  Layers,
  AlertTriangle,
  CheckCircle2,
  Clock,
  TrendingUp,
  FileText,
} from 'lucide-react';
import { batchLabel } from '../lib/labels';

interface FacultyProfileDrawerProps {
  facultyId: number;
  onClose: () => void;
}

const CATEGORY_LABELS: Record<string, string> = {
  CORE: 'Core',
  DEPARTMENT_ELECTIVE: 'Department Elective',
  OPEN_ELECTIVE: 'Open Elective',
};

function submissionBadge(status: string, overdue: boolean) {
  const base = 'text-[10px] font-semibold px-2 py-0.5 rounded-full border';
  switch ((status || '').toUpperCase()) {
    case 'ACCEPTED':
      return <span className={`${base} bg-emerald-50 text-emerald-800 border-emerald-200`}>Accepted</span>;
    case 'SUBMITTED':
      return <span className={`${base} bg-blue-50 text-blue-800 border-blue-200`}>Submitted</span>;
    case 'LATE':
      return <span className={`${base} bg-amber-50 text-amber-800 border-amber-200`}>Late</span>;
    case 'RETURNED':
      return <span className={`${base} bg-rose-50 text-rose-800 border-rose-200`}>Returned</span>;
    default:
      return (
        <span
          className={`${base} ${
            overdue ? 'bg-rose-50 text-rose-800 border-rose-200' : 'bg-slate-100 text-slate-700 border-slate-200'
          }`}
        >
          {overdue ? 'Overdue' : 'Pending'}
        </span>
      );
  }
}

/**
 * Full coordinator-facing profile for one faculty member: identity and department, every
 * course and section they own with their LTP share, computed weekly load, compliance record
 * and complete submission history.
 */
export function FacultyProfileDrawer({ facultyId, onClose }: FacultyProfileDrawerProps) {
  const { data: profile, isLoading, error } = useQuery<FacultyProfileDto>({
    queryKey: ['faculty-profile', facultyId],
    queryFn: async () => {
      const res = await fetch(`/api/faculty/${facultyId}/profile`, { credentials: 'include' });
      if (!res.ok) throw new Error(`Failed to load profile (${res.status})`);
      return res.json();
    },
  });

  const compliance = profile?.compliance;
  const complianceTone =
    !compliance || compliance.complianceStatus === 'GOOD'
      ? 'text-emerald-700 bg-emerald-50 border-emerald-200'
      : compliance.complianceStatus === 'WATCH'
      ? 'text-amber-800 bg-amber-50 border-amber-200'
      : 'text-rose-800 bg-rose-50 border-rose-200';

  const submittedRows = (profile?.submissions ?? []).filter((s) => s.status !== 'PENDING');
  const onTimeRows = submittedRows.filter((s) => s.status !== 'LATE');

  return (
    <div className="fixed inset-0 z-50 flex justify-end">
      <div className="absolute inset-0 bg-slate-900/40 backdrop-blur-xs" onClick={onClose} />

      <aside
        role="dialog"
        aria-modal="true"
        aria-label="Faculty profile"
        className="relative h-full w-full max-w-xl bg-[#FFFFFF] border-l border-[#E6E4DD] overflow-y-auto"
      >
        {isLoading ? (
          <div className="p-8 text-sm text-slate-500 animate-pulse">Loading faculty profile…</div>
        ) : error || !profile ? (
          <div className="p-8 space-y-4">
            <div className="flex items-start gap-2 text-xs text-rose-800 bg-rose-50 border border-rose-200 rounded-md p-3">
              <AlertTriangle className="w-4 h-4 text-rose-600 shrink-0" />
              <span>{error instanceof Error ? error.message : 'Could not load this profile.'}</span>
            </div>
            <button
              type="button"
              onClick={onClose}
              className="px-3 py-1.5 text-xs font-medium bg-slate-900 text-white rounded-md"
            >
              Close
            </button>
          </div>
        ) : (
          <>
            {/* Header */}
            <div className="sticky top-0 z-10 bg-[#FFFFFF]/95 backdrop-blur border-b border-[#E6E4DD] px-6 py-5">
              <div className="flex items-start justify-between gap-4">
                <div className="flex items-start gap-3 min-w-0">
                  <div className="w-11 h-11 rounded-full bg-[#EFEEE9] ring-1 ring-[#D6D3CA] text-[#16181D] flex items-center justify-center text-sm font-semibold shrink-0">
                    {profile.name
                      .replace(/^(Dr|Prof|Mr|Ms|Mrs)\.?\s+/i, '')
                      .split(' ')
                      .filter(Boolean)
                      .slice(0, 2)
                      .map((n) => n[0])
                      .join('')
                      .toUpperCase()}
                  </div>
                  <div className="min-w-0">
                    <h2 className="font-sans text-lg font-semibold text-slate-900 truncate">
                      {profile.name}
                    </h2>
                    <p className="text-xs text-slate-500 mt-0.5">{profile.designation}</p>
                    <div className="flex flex-wrap items-center gap-x-3 gap-y-1 mt-2 text-[11px] text-slate-500">
                      <span className="inline-flex items-center gap-1">
                        <Building2 className="w-3 h-3" />
                        {profile.department}
                      </span>
                      {profile.email && (
                        <span className="inline-flex items-center gap-1">
                          <Mail className="w-3 h-3" />
                          {profile.email}
                        </span>
                      )}
                      {profile.qualification && (
                        <span className="inline-flex items-center gap-1">
                          <GraduationCap className="w-3 h-3" />
                          {profile.qualification}
                        </span>
                      )}
                    </div>
                  </div>
                </div>

                <button
                  type="button"
                  onClick={onClose}
                  aria-label="Close profile"
                  className="p-1.5 rounded-md text-slate-400 hover:text-slate-900 hover:bg-slate-100 transition-colors shrink-0"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
            </div>

            <div className="px-6 py-5 space-y-6">
              {/* Record summary */}
              <div className="grid grid-cols-3 gap-3">
                <div className="rounded-lg border border-border bg-white p-3.5">
                  <div className="flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-wider text-slate-500">
                    <TrendingUp className="w-3 h-3" />
                    Compliance
                  </div>
                  <div className="mt-1.5 flex items-baseline gap-1.5">
                    <span className="font-sans text-xl font-bold text-slate-900">
                      {compliance ? `${compliance.complianceRate}%` : '—'}
                    </span>
                    {compliance && (
                      <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded border ${complianceTone}`}>
                        {compliance.complianceStatus}
                      </span>
                    )}
                  </div>
                  <p className="text-[10px] text-slate-500 mt-1">
                    {compliance
                      ? `${compliance.submittedCount} of ${compliance.totalAssigned} deadlines submitted`
                      : 'No deadline assignments yet'}
                  </p>
                </div>

                <div className="rounded-lg border border-border bg-white p-3.5">
                  <div className="flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-wider text-slate-500">
                    <Clock className="w-3 h-3" />
                    On-time
                  </div>
                  <div className="mt-1.5 font-sans text-xl font-bold text-slate-900">
                    {submittedRows.length > 0
                      ? `${Math.round((onTimeRows.length / submittedRows.length) * 100)}%`
                      : '—'}
                  </div>
                  <p className="text-[10px] text-slate-500 mt-1">
                    {onTimeRows.length} of {submittedRows.length} submissions on time
                  </p>
                </div>

                <div className="rounded-lg border border-border bg-white p-3.5">
                  <div className="flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-wider text-slate-500">
                    <Layers className="w-3 h-3" />
                    Weekly load
                  </div>
                  <div className="mt-1.5 font-sans text-xl font-bold text-slate-900">
                    {profile.load.totalHours}h
                  </div>
                  <p className="text-[10px] text-slate-500 mt-1 font-mono">
                    {profile.load.lectureHours}L · {profile.load.tutorialHours}T · {profile.load.practicalHours}P
                  </p>
                </div>
              </div>

              {/* Skills */}
              <section>
                <h3 className="text-[10px] font-bold text-slate-500 uppercase tracking-wider mb-2">
                  Skills &amp; Expertise
                </h3>
                <div className="flex flex-wrap gap-1.5">
                  {profile.skills.length > 0 ? (
                    profile.skills.map((skill) => (
                      <span
                        key={skill.id}
                        className="text-xs px-2.5 py-0.5 rounded-md border border-border bg-slate-50 text-slate-700 font-medium"
                      >
                        {skill.name}
                      </span>
                    ))
                  ) : (
                    <span className="text-xs text-slate-400 italic">No skills recorded</span>
                  )}
                </div>
              </section>

              {/* Courses & sections */}
              <section>
                <div className="flex items-center justify-between mb-2">
                  <h3 className="text-[10px] font-bold text-slate-500 uppercase tracking-wider flex items-center gap-1.5">
                    <BookOpen className="w-3 h-3" />
                    Courses &amp; Sections
                  </h3>
                  <span className="text-[10px] text-slate-500">{profile.courses.length} allocations</span>
                </div>

                {profile.courses.length === 0 ? (
                  <p className="text-xs text-slate-400 italic">No teaching allocations in the active term.</p>
                ) : (
                  <div className="rounded-lg border border-border overflow-hidden divide-y divide-border">
                    {profile.courses.map((course) => (
                      <div key={course.assignmentId} className="p-3.5 hover:bg-slate-50/70 transition-colors">
                        <div className="flex items-start justify-between gap-3">
                          <div className="min-w-0">
                            <div className="flex items-center gap-2 flex-wrap">
                              <span className="font-mono text-[11px] font-bold text-slate-800">
                                {course.courseCode}
                              </span>
                              <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded border border-border bg-slate-50 text-slate-600">
                                {CATEGORY_LABELS[course.category ?? 'CORE'] ?? course.category}
                              </span>
                              <span className="text-[10px] font-mono text-slate-500">LTP {course.ltp}</span>
                            </div>
                            <div className="text-xs font-medium text-slate-900 mt-1 truncate">
                              {course.courseName}
                            </div>
                            <div className="text-[11px] text-slate-500 mt-0.5">
                              Section {course.section} · Sem {course.semester} · Yr {course.yearOfStudy} ·{' '}
                              {course.academicYear}
                            </div>
                          </div>

                          <div className="text-right shrink-0">
                            <div className="text-[10px] font-bold px-2 py-0.5 rounded-full border border-border bg-slate-100 text-slate-700">
                              {course.component}
                            </div>
                            <div className="text-[11px] font-mono text-slate-700 mt-1.5">
                              {course.contactHours}h/week
                            </div>
                            {batchLabel(course.subSection) && (
                              <div className="chip chip-info mt-1">{batchLabel(course.subSection)}</div>
                            )}
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </section>

              {/* Submission history */}
              <section>
                <div className="flex items-center justify-between mb-2">
                  <h3 className="text-[10px] font-bold text-slate-500 uppercase tracking-wider flex items-center gap-1.5">
                    <FileText className="w-3 h-3" />
                    Submission History
                  </h3>
                  <span className="text-[10px] text-slate-500">{profile.submissions.length} deadlines</span>
                </div>

                {profile.submissions.length === 0 ? (
                  <p className="text-xs text-slate-400 italic">
                    No deadlines have been targeted at this faculty member yet.
                  </p>
                ) : (
                  <div className="rounded-lg border border-border overflow-hidden divide-y divide-border">
                    {profile.submissions.map((row: FacultySubmissionRowDto) => (
                      <div
                        key={row.assignmentId}
                        className="p-3.5 flex items-center justify-between gap-3 hover:bg-slate-50/70 transition-colors"
                      >
                        <div className="min-w-0">
                          <div className="text-xs font-medium text-slate-900 truncate">
                            {row.deadlineTitle}
                          </div>
                          <div className="text-[11px] text-slate-500 mt-0.5">
                            {row.deadlineType?.replace(/_/g, ' ')} ·{' '}
                            {row.dueAt ? new Date(row.dueAt).toLocaleDateString() : '—'}
                            {row.totalVersions > 1 && (
                              <span className="ml-2 text-slate-400">{row.totalVersions} versions</span>
                            )}
                          </div>
                        </div>
                        <div className="flex items-center gap-2 shrink-0">
                          {row.status === 'ACCEPTED' && <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />}
                          {submissionBadge(row.status, row.overdue)}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </section>
            </div>
          </>
        )}
      </aside>
    </div>
  );
}
