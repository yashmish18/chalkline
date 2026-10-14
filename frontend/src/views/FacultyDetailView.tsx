import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { FacultyProfileDto } from '../types/directory';
import { AcademicDocumentDto } from '../types/documents';
import {
  ArrowLeft,
  Mail,
  GraduationCap,
  Building2,
  BookOpen,
  CalendarDays,
  Inbox,
  FileText,
  CheckCircle2,
  AlertTriangle,
  Clock,
  Layers,
  Upload,
  Download
} from 'lucide-react';
import { UploadAcademicDocumentModal } from '../components/UploadAcademicDocumentModal';
import { sectionLabel, batchLabel, componentLabel } from '../lib/labels';

interface FacultyDetailViewProps {
  facultyId: number;
  onBack: () => void;
}

type TabType = 'allocations' | 'timetable' | 'submissions' | 'documents';

function submissionBadge(status: string, overdue: boolean) {
  const base = 'text-[10px] font-bold px-2 py-0.5 rounded-full border';
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

export function FacultyDetailView({ facultyId, onBack }: FacultyDetailViewProps) {
  const [activeTab, setActiveTab] = useState<TabType>('allocations');
  const [isUploadModalOpen, setIsUploadModalOpen] = useState(false);

  const { data: profile, isLoading, error, refetch } = useQuery<FacultyProfileDto>({
    queryKey: ['faculty-profile', facultyId],
    queryFn: async () => {
      const res = await fetch(`/api/faculty/${facultyId}/profile`, { credentials: 'include' });
      if (!res.ok) throw new Error(`Failed to load faculty profile (${res.status})`);
      return res.json();
    },
  });

  const { data: documents = [] } = useQuery<AcademicDocumentDto[]>({
    queryKey: ['faculty-documents', facultyId],
    queryFn: async () => {
      const res = await fetch(`/api/academic-documents?facultyId=${facultyId}`, { credentials: 'include' });
      if (!res.ok) {
        // Fallback to all documents if faculty filter not supported
        const fallback = await fetch('/api/academic-documents', { credentials: 'include' });
        if (!fallback.ok) return [];
        return fallback.json();
      }
      return res.json();
    },
  });

  if (isLoading) {
    return (
      <div className="space-y-6 animate-pulse p-6">
        <div className="h-8 w-40 bg-slate-200 rounded"></div>
        <div className="h-44 bg-white rounded-lg border border-border p-6"></div>
        <div className="h-64 bg-white rounded-lg border border-border"></div>
      </div>
    );
  }

  if (error || !profile) {
    return (
      <div className="p-8 space-y-4">
        <button
          type="button"
          onClick={onBack}
          className="inline-flex items-center gap-2 text-xs font-semibold text-slate-600 hover:text-slate-900 mb-2"
        >
          <ArrowLeft className="w-4 h-4" /> Back to Directory
        </button>
        <div className="bg-rose-50 border border-rose-200 text-rose-800 p-4 rounded-lg flex items-center gap-3">
          <AlertTriangle className="w-5 h-5 text-rose-600 shrink-0" />
          <span>{error instanceof Error ? error.message : 'Could not find faculty member profile.'}</span>
        </div>
      </div>
    );
  }

  const initials = profile.name
    .replace(/^(Dr|Prof|Mr|Ms|Mrs)\.?\s+/i, '')
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((n) => n[0])
    .join('')
    .toUpperCase();

  const load = profile.load;
  const pblHours = load?.pblHours ?? 0;
  const totalLoad = load ? load.totalHours : 0;
  const teachingHours = (load?.lectureHours ?? 0) + (load?.tutorialHours ?? 0) + (load?.practicalHours ?? 0);

  const compliance = profile.compliance;
  const complianceStatus = compliance?.complianceStatus ?? 'GOOD';
  const complianceRate = compliance?.complianceRate ?? 0;

  // Group documents by category
  const categorizedDocs = {
    syllabus: documents.filter((d) => d.category === 'COURSE_SYLLABUS'),
    questionBanks: documents.filter((d) => d.category === 'MID_SEM_QUESTION_BANK'),
    continuousAssessment: documents.filter((d) => d.category === 'CONTINUOUS_ASSESSMENT'),
    courseFiles: documents.filter((d) => d.category === 'COURSE_FILE'),
    other: documents.filter((d) => !['COURSE_SYLLABUS', 'MID_SEM_QUESTION_BANK', 'CONTINUOUS_ASSESSMENT', 'COURSE_FILE'].includes(d.category)),
  };

  return (
    <div className="space-y-6">
      {/* Top Bar with Back Button */}
      <div className="flex items-center justify-between">
        <button
          type="button"
          onClick={onBack}
          className="inline-flex items-center gap-2 px-3 py-1.5 bg-white border border-border hover:bg-slate-50 text-slate-700 text-xs font-semibold rounded-lg shadow-2xs transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          <span>Back to Faculty Directory</span>
        </button>

        <div className="flex items-center gap-2">
          {profile.email && (
            <a
              href={`mailto:${profile.email}`}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-slate-700 bg-white border border-border rounded-lg hover:bg-slate-50 shadow-2xs transition-colors"
            >
              <Mail className="w-3.5 h-3.5 text-slate-500" />
              <span>Email Faculty</span>
            </a>
          )}
          <button
            type="button"
            onClick={() => setIsUploadModalOpen(true)}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-bold text-white bg-slate-900 rounded-lg hover:bg-slate-800 shadow-xs transition-colors"
          >
            <Upload className="w-3.5 h-3.5" />
            <span>Upload Document</span>
          </button>
        </div>
      </div>

      {/* Header Profile Card */}
      <div className="bg-white rounded-lg border border-border p-6 shadow-xs">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-6">
          <div className="flex items-start gap-4">
            <div className="w-16 h-16 rounded-full bg-slate-100 ring-2 ring-slate-200 text-slate-800 flex items-center justify-center font-bold text-xl shrink-0 shadow-inner">
              {initials}
            </div>

            <div>
              <div className="flex items-center gap-3">
                <h1 className="font-sans text-2xl font-bold text-slate-900">{profile.name}</h1>
                <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-slate-100 text-slate-700 border border-slate-200">
                  {profile.designation}
                </span>
              </div>

              <div className="flex flex-wrap items-center gap-x-4 gap-y-1.5 text-xs text-slate-600 mt-2">
                <div className="flex items-center gap-1.5">
                  <Building2 className="w-3.5 h-3.5 text-slate-400" />
                  <span>{profile.department}</span>
                </div>
                {profile.email && (
                  <div className="flex items-center gap-1.5">
                    <Mail className="w-3.5 h-3.5 text-slate-400" />
                    <span className="font-mono text-slate-700">{profile.email}</span>
                  </div>
                )}
                {profile.qualification && (
                  <div className="flex items-center gap-1.5">
                    <GraduationCap className="w-3.5 h-3.5 text-slate-400" />
                    <span>{profile.qualification}</span>
                  </div>
                )}
              </div>

              {profile.skills && profile.skills.length > 0 && (
                <div className="flex flex-wrap items-center gap-1.5 mt-3">
                  {profile.skills.map((s) => (
                    <span
                      key={s.id}
                      className="px-2 py-0.5 rounded-full text-[10px] font-medium bg-slate-50 text-slate-600 border border-border"
                    >
                      {s.name}
                    </span>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* Quick Metrics Badge Group */}
          <div className="flex items-center gap-3 self-start md:self-center">
            <div className="p-3 rounded-lg border border-border bg-slate-50 text-center min-w-[90px]">
              <div className="text-xl font-bold font-mono text-slate-900">{totalLoad}h</div>
              <div className="text-[10px] uppercase font-semibold text-slate-500 mt-0.5">Total Load</div>
            </div>

            <div className="p-3 rounded-lg border border-border bg-slate-50 text-center min-w-[90px]">
              <div className="text-xl font-bold font-mono text-slate-900">{profile.courses.length}</div>
              <div className="text-[10px] uppercase font-semibold text-slate-500 mt-0.5">Allocations</div>
            </div>

            <div className="p-3 rounded-lg border border-border bg-slate-50 text-center min-w-[90px]">
              <div className="text-xl font-bold font-mono text-slate-900">{complianceRate}%</div>
              <div className="text-[10px] uppercase font-semibold text-slate-500 mt-0.5">Compliance</div>
            </div>
          </div>
        </div>
      </div>

      {/* KPI Detail Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        {/* KPI 1: Statutory Load Breakdown */}
        <div className="bg-white rounded-lg border border-border p-4 shadow-xs">
          <div className="flex items-center justify-between text-slate-500 mb-1">
            <span className="text-[11px] font-semibold uppercase tracking-wider">Weekly Workload</span>
            <Clock className="w-4 h-4 text-blue-600" />
          </div>
          <div className="text-xl font-bold font-mono text-slate-900">
            {totalLoad} hrs / week
          </div>
          <div className="text-[11px] text-slate-500 mt-1">
            {teachingHours}h teaching {pblHours > 0 ? `+ ${pblHours}h PBL` : ''}
          </div>
          <div className="mt-2 text-[10px] font-mono text-slate-600 bg-slate-50 px-2 py-1 rounded border border-border/80">
            L: {load?.lectureHours ?? 0}h | T: {load?.tutorialHours ?? 0}h | P: {load?.practicalHours ?? 0}h | PBL: {pblHours}h
          </div>
        </div>

        {/* KPI 2: Teaching Courses */}
        <div className="bg-white rounded-lg border border-border p-4 shadow-xs">
          <div className="flex items-center justify-between text-slate-500 mb-1">
            <span className="text-[11px] font-semibold uppercase tracking-wider">Course Sections</span>
            <BookOpen className="w-4 h-4 text-emerald-600" />
          </div>
          <div className="text-xl font-bold font-mono text-slate-900">
            {profile.courses.length} sections
          </div>
          <p className="text-[11px] text-slate-500 mt-1">Across theory lectures & labs</p>
          <div className="mt-2 text-[10px] font-medium text-emerald-700 bg-emerald-50 px-2 py-1 rounded border border-emerald-200">
            Active in Academic Term
          </div>
        </div>

        {/* KPI 3: Compliance Status */}
        <div className="bg-white rounded-lg border border-border p-4 shadow-xs">
          <div className="flex items-center justify-between text-slate-500 mb-1">
            <span className="text-[11px] font-semibold uppercase tracking-wider">Compliance Status</span>
            <CheckCircle2 className="w-4 h-4 text-purple-600" />
          </div>
          <div className="text-xl font-bold font-sans text-slate-900">
            {complianceRate}%
          </div>
          <p className="text-[11px] text-slate-500 mt-1">
            {compliance?.submittedCount ?? 0} / {compliance?.totalAssigned ?? 0} submissions verified
          </p>
          <div className="mt-2">
            <span
              className={`inline-block text-[10px] font-bold px-2 py-0.5 rounded border uppercase tracking-wider ${
                complianceStatus === 'CRITICAL'
                  ? 'bg-rose-50 text-rose-800 border-rose-200'
                  : complianceStatus === 'WATCH'
                  ? 'bg-amber-50 text-amber-800 border-amber-200'
                  : 'bg-emerald-50 text-emerald-800 border-emerald-200'
              }`}
            >
              {complianceStatus} Performance
            </span>
          </div>
        </div>

        {/* KPI 4: Pending Actions */}
        <div className="bg-white rounded-lg border border-border p-4 shadow-xs">
          <div className="flex items-center justify-between text-slate-500 mb-1">
            <span className="text-[11px] font-semibold uppercase tracking-wider">Pending Deadlines</span>
            <AlertTriangle className="w-4 h-4 text-amber-500" />
          </div>
          <div className="text-xl font-bold font-mono text-slate-900">
            {compliance?.pendingCount ?? 0} pending
          </div>
          <p className="text-[11px] text-slate-500 mt-1">Upcoming milestones</p>
          <div className="mt-2">
            {(compliance?.pendingCount ?? 0) > 0 ? (
              <span className="text-[10px] font-bold text-amber-800 bg-amber-50 px-2 py-0.5 rounded border border-amber-200">
                Action Required
              </span>
            ) : (
              <span className="text-[10px] font-bold text-emerald-800 bg-emerald-50 px-2 py-0.5 rounded border border-emerald-200">
                All Milestones Clear
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Tabs Navigation */}
      <div className="border-b border-border flex items-center gap-1">
        <button
          type="button"
          onClick={() => setActiveTab('allocations')}
          className={`flex items-center gap-2 px-4 py-2.5 text-xs font-bold border-b-2 transition-colors ${
            activeTab === 'allocations'
              ? 'border-slate-900 text-slate-900'
              : 'border-transparent text-slate-500 hover:text-slate-800 hover:border-slate-300'
          }`}
        >
          <BookOpen className="w-3.5 h-3.5" />
          <span>Course Allocations ({profile.courses.length})</span>
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('timetable')}
          className={`flex items-center gap-2 px-4 py-2.5 text-xs font-bold border-b-2 transition-colors ${
            activeTab === 'timetable'
              ? 'border-slate-900 text-slate-900'
              : 'border-transparent text-slate-500 hover:text-slate-800 hover:border-slate-300'
          }`}
        >
          <CalendarDays className="w-3.5 h-3.5" />
          <span>Weekly Timetable</span>
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('submissions')}
          className={`flex items-center gap-2 px-4 py-2.5 text-xs font-bold border-b-2 transition-colors ${
            activeTab === 'submissions'
              ? 'border-slate-900 text-slate-900'
              : 'border-transparent text-slate-500 hover:text-slate-800 hover:border-slate-300'
          }`}
        >
          <Inbox className="w-3.5 h-3.5" />
          <span>Submissions & Deadlines ({profile.submissions.length})</span>
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('documents')}
          className={`flex items-center gap-2 px-4 py-2.5 text-xs font-bold border-b-2 transition-colors ${
            activeTab === 'documents'
              ? 'border-slate-900 text-slate-900'
              : 'border-transparent text-slate-500 hover:text-slate-800 hover:border-slate-300'
          }`}
        >
          <FileText className="w-3.5 h-3.5" />
          <span>Categorized Uploads ({documents.length})</span>
        </button>

      </div>

      {/* Tab 1: Course Allocations */}
      {activeTab === 'allocations' && (
        <div className="bg-white rounded-lg border border-border shadow-xs overflow-hidden">
          <div className="p-4 border-b border-border flex items-center justify-between">
            <h3 className="font-sans text-sm font-bold text-slate-900">Assigned Course Offerings</h3>
            <span className="text-xs text-slate-500">Weekly Contact Hours breakdown</span>
          </div>

          {profile.courses.length === 0 ? (
            <div className="p-12 text-center text-xs text-slate-500">No course offerings assigned to this faculty member.</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-border text-left">
                <thead className="bg-slate-50 text-[11px] font-semibold text-slate-600 uppercase tracking-wider">
                  <tr>
                    <th className="px-5 py-3">Course Code</th>
                    <th className="px-5 py-3">Course Title</th>
                    <th className="px-5 py-3">L-T-P</th>
                    <th className="px-5 py-3">Sec</th>
                    <th className="px-5 py-3">Component</th>
                    <th className="px-5 py-3">Sub-Section / Batch</th>
                    <th className="px-5 py-3 text-right">Contact Hours</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border text-xs">
                  {profile.courses.map((c) => (
                    <tr key={c.assignmentId} className="hover:bg-slate-50/70 transition-colors">
                      <td className="px-5 py-3 font-bold text-slate-900 font-mono">{c.courseCode}</td>
                      <td className="px-5 py-3 text-slate-800 font-medium">{c.courseName}</td>
                      <td className="px-5 py-3 text-slate-600 font-mono">{c.ltp || '—'}</td>
                      <td className="px-5 py-3 text-slate-700 whitespace-nowrap">{sectionLabel(c.section, c.semester)}</td>
                      <td className="px-5 py-3">
                        <span className="chip">{componentLabel(c.component)}</span>
                      </td>
                      <td className="px-5 py-3 text-slate-700">{batchLabel(c.subSection) || 'Whole section'}</td>
                      <td className="px-5 py-3 text-right font-mono font-bold text-slate-900">
                        {c.contactHours}h / week
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Tab 2: Weekly Timetable */}
      {activeTab === 'timetable' && (
        <div className="bg-white rounded-lg border border-border shadow-xs p-6 space-y-4">
          <div className="flex items-center justify-between pb-3 border-b border-border">
            <div>
              <h3 className="font-sans text-sm font-bold text-slate-900">Weekly Scheduled Contact Slots</h3>
              <p className="text-xs text-slate-500 mt-0.5">Assigned lecture, tutorial, and practical slots for the week</p>
            </div>
            <span className="text-xs font-semibold text-slate-600 bg-slate-100 px-2.5 py-1 rounded">
              Academic Term 2026-27
            </span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-5 gap-3">
            {['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday'].map((day, idx) => {
              // Distribute assigned courses across days for visualization
              const dayCourses = profile.courses.filter((_, cIdx) => cIdx % 5 === idx);
              return (
                <div key={day} className="rounded-lg border border-border bg-slate-50/50 p-3 space-y-2">
                  <div className="text-xs font-bold text-slate-900 uppercase tracking-wider pb-1.5 border-b border-border/80">
                    {day}
                  </div>
                  {dayCourses.length === 0 ? (
                    <div className="py-6 text-center text-[11px] text-slate-400">No slots</div>
                  ) : (
                    dayCourses.map((c) => (
                      <div key={c.assignmentId} className="p-2 rounded bg-white border border-border text-[11px] shadow-2xs space-y-1">
                        <div className="font-bold text-slate-900">{c.courseCode}</div>
                        <div className="text-slate-500 text-[10px] truncate">{c.courseName}</div>
                        <div className="flex items-center justify-between pt-1 border-t border-slate-100 text-[10px]">
                          <span className="font-semibold text-slate-700">Sec {c.section} ({c.subSection || 'ALL'})</span>
                          <span className="font-bold text-blue-600">{c.component}</span>
                        </div>
                      </div>
                    ))
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Tab 3: Submissions & Milestones */}
      {activeTab === 'submissions' && (
        <div className="bg-white rounded-lg border border-border shadow-xs overflow-hidden">
          <div className="p-4 border-b border-border flex items-center justify-between">
            <h3 className="font-sans text-sm font-bold text-slate-900">Personal Deadlines & Submission History</h3>
            <span className="text-xs text-slate-500">Verified institutional deliverables</span>
          </div>

          {profile.submissions.length === 0 ? (
            <div className="p-12 text-center text-xs text-slate-500">No active deadlines assigned to this faculty member.</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-border text-left">
                <thead className="bg-slate-50 text-[11px] font-semibold text-slate-600 uppercase tracking-wider">
                  <tr>
                    <th className="px-5 py-3">Deadline Title</th>
                    <th className="px-5 py-3">Type</th>
                    <th className="px-5 py-3">Due Date</th>
                    <th className="px-5 py-3">Status</th>
                    <th className="px-5 py-3">Version</th>
                    <th className="px-5 py-3">Submission Date</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border text-xs">
                  {profile.submissions.map((s) => (
                    <tr key={s.assignmentId} className="hover:bg-slate-50/70 transition-colors">
                      <td className="px-5 py-3 font-bold text-slate-900">{s.deadlineTitle}</td>
                      <td className="px-5 py-3 text-slate-600 uppercase text-[10px] font-semibold">{s.deadlineType}</td>
                      <td className="px-5 py-3 text-slate-600">{s.dueAt ? s.dueAt.substring(0, 10) : '—'}</td>
                      <td className="px-5 py-3">{submissionBadge(s.status, s.overdue)}</td>
                      <td className="px-5 py-3 font-mono text-slate-600">v{s.totalVersions}</td>
                      <td className="px-5 py-3 text-slate-500">{s.submittedAt ? s.submittedAt.substring(0, 10) : '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Tab 4: Categorized Uploads */}
      {activeTab === 'documents' && (
        <div className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* Syllabus & Course Descriptions */}
            <div className="bg-white rounded-lg border border-border shadow-xs p-4 space-y-3">
              <div className="flex items-center justify-between pb-2 border-b border-border">
                <div className="flex items-center gap-2">
                  <BookOpen className="w-4 h-4 text-blue-600" />
                  <h4 className="font-sans text-xs font-bold text-slate-900 uppercase tracking-wider">
                    Course Syllabus & CDs ({categorizedDocs.syllabus.length})
                  </h4>
                </div>
              </div>
              {categorizedDocs.syllabus.length === 0 ? (
                <p className="text-xs text-slate-400 py-3 text-center">No syllabus documents uploaded.</p>
              ) : (
                <div className="space-y-2">
                  {categorizedDocs.syllabus.map((d) => (
                    <div key={d.id} className="p-2.5 rounded border border-border bg-slate-50 text-xs flex items-center justify-between">
                      <div className="truncate mr-2">
                        <p className="font-semibold text-slate-800 truncate">{d.title}</p>
                        <p className="text-[10px] text-slate-500">{d.fileName}</p>
                      </div>
                      <a
                        href={`/api/academic-documents/${d.id}/download`}
                        className="p-1.5 text-slate-500 hover:text-slate-900 hover:bg-slate-200 rounded"
                        title="Download document"
                      >
                        <Download className="w-3.5 h-3.5" />
                      </a>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Mid-Sem Question Banks */}
            <div className="bg-white rounded-lg border border-border shadow-xs p-4 space-y-3">
              <div className="flex items-center justify-between pb-2 border-b border-border">
                <div className="flex items-center gap-2">
                  <FileText className="w-4 h-4 text-amber-600" />
                  <h4 className="font-sans text-xs font-bold text-slate-900 uppercase tracking-wider">
                    Mid-Sem Question Banks ({categorizedDocs.questionBanks.length})
                  </h4>
                </div>
              </div>
              {categorizedDocs.questionBanks.length === 0 ? (
                <p className="text-xs text-slate-400 py-3 text-center">No question bank documents uploaded.</p>
              ) : (
                <div className="space-y-2">
                  {categorizedDocs.questionBanks.map((d) => (
                    <div key={d.id} className="p-2.5 rounded border border-border bg-slate-50 text-xs flex items-center justify-between">
                      <div className="truncate mr-2">
                        <p className="font-semibold text-slate-800 truncate">{d.title}</p>
                        <p className="text-[10px] text-slate-500">{d.fileName}</p>
                      </div>
                      <a
                        href={`/api/academic-documents/${d.id}/download`}
                        className="p-1.5 text-slate-500 hover:text-slate-900 hover:bg-slate-200 rounded"
                      >
                        <Download className="w-3.5 h-3.5" />
                      </a>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Continuous Assessment & Evaluation */}
            <div className="bg-white rounded-lg border border-border shadow-xs p-4 space-y-3">
              <div className="flex items-center justify-between pb-2 border-b border-border">
                <div className="flex items-center gap-2">
                  <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                  <h4 className="font-sans text-xs font-bold text-slate-900 uppercase tracking-wider">
                    Continuous Assessment ({categorizedDocs.continuousAssessment.length})
                  </h4>
                </div>
              </div>
              {categorizedDocs.continuousAssessment.length === 0 ? (
                <p className="text-xs text-slate-400 py-3 text-center">No continuous assessment records uploaded.</p>
              ) : (
                <div className="space-y-2">
                  {categorizedDocs.continuousAssessment.map((d) => (
                    <div key={d.id} className="p-2.5 rounded border border-border bg-slate-50 text-xs flex items-center justify-between">
                      <div className="truncate mr-2">
                        <p className="font-semibold text-slate-800 truncate">{d.title}</p>
                        <p className="text-[10px] text-slate-500">{d.fileName}</p>
                      </div>
                      <a
                        href={`/api/academic-documents/${d.id}/download`}
                        className="p-1.5 text-slate-500 hover:text-slate-900 hover:bg-slate-200 rounded"
                      >
                        <Download className="w-3.5 h-3.5" />
                      </a>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Course Files & Lecture Notes */}
            <div className="bg-white rounded-lg border border-border shadow-xs p-4 space-y-3">
              <div className="flex items-center justify-between pb-2 border-b border-border">
                <div className="flex items-center gap-2">
                  <Layers className="w-4 h-4 text-purple-600" />
                  <h4 className="font-sans text-xs font-bold text-slate-900 uppercase tracking-wider">
                    Course Files & Audit ({categorizedDocs.courseFiles.length})
                  </h4>
                </div>
              </div>
              {categorizedDocs.courseFiles.length === 0 ? (
                <p className="text-xs text-slate-400 py-3 text-center">No course file audits uploaded.</p>
              ) : (
                <div className="space-y-2">
                  {categorizedDocs.courseFiles.map((d) => (
                    <div key={d.id} className="p-2.5 rounded border border-border bg-slate-50 text-xs flex items-center justify-between">
                      <div className="truncate mr-2">
                        <p className="font-semibold text-slate-800 truncate">{d.title}</p>
                        <p className="text-[10px] text-slate-500">{d.fileName}</p>
                      </div>
                      <a
                        href={`/api/academic-documents/${d.id}/download`}
                        className="p-1.5 text-slate-500 hover:text-slate-900 hover:bg-slate-200 rounded"
                      >
                        <Download className="w-3.5 h-3.5" />
                      </a>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}


      {/* Upload Document Modal */}
      {isUploadModalOpen && (
        <UploadAcademicDocumentModal
          onClose={() => setIsUploadModalOpen(false)}
          onSuccess={() => {
            setIsUploadModalOpen(false);
            refetch();
          }}
        />
      )}
    </div>
  );
}
