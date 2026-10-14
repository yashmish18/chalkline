import { useMemo, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { SubmissionOverviewDto, SubmissionStatus } from '../types/submissions';
import { DeadlineAssignmentDto } from '../types/deadlines';
import { SubmissionModal } from '../components/SubmissionModal';
import {
  FileSpreadsheet,
  FileText,
  Download,
  RefreshCw,
  AlertCircle,
  Clock,
  CheckCircle2,
  RotateCcw,
  Inbox,
} from 'lucide-react';

const STATUS_FILTERS: { id: SubmissionStatus | 'ALL'; label: string }[] = [
  { id: 'ALL', label: 'All' },
  { id: 'SUBMITTED', label: 'Submitted' },
  { id: 'LATE', label: 'Late' },
  { id: 'RETURNED', label: 'Returned' },
  { id: 'ACCEPTED', label: 'Accepted' },
  { id: 'PENDING', label: 'Pending' },
];

const STATUS_STYLE: Record<SubmissionStatus, string> = {
  PENDING: 'bg-slate-100 text-slate-700 border-slate-200',
  SUBMITTED: 'bg-blue-50 text-blue-700 border-blue-200',
  LATE: 'bg-amber-50 text-amber-800 border-amber-200',
  RETURNED: 'bg-rose-50 text-rose-700 border-rose-200',
  ACCEPTED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
};

function StatusBadge({ status }: { status: SubmissionStatus }) {
  const icon =
    status === 'PENDING' ? (
      <Clock className="w-3 h-3" />
    ) : status === 'ACCEPTED' ? (
      <CheckCircle2 className="w-3 h-3" />
    ) : status === 'RETURNED' ? (
      <RotateCcw className="w-3 h-3" />
    ) : status === 'LATE' ? (
      <AlertCircle className="w-3 h-3" />
    ) : (
      <CheckCircle2 className="w-3 h-3" />
    );

  return (
    <span
      className={`inline-flex items-center gap-1 text-[10px] font-semibold px-2 py-0.5 rounded-full border ${STATUS_STYLE[status]}`}
    >
      {icon}
      {status}
    </span>
  );
}

/**
 * Coordinator board for every deadline submission in the department. Surfaces what faculty have
 * actually turned in, lets the coordinator download the artifact and accept / return it, and
 * carries the same Excel audit export as the compliance screen.
 */
export function SubmissionsView() {
  const [statusFilter, setStatusFilter] = useState<SubmissionStatus | 'ALL'>('ALL');
  const [selected, setSelected] = useState<DeadlineAssignmentDto | null>(null);
  const [board, setBoard] = useState<'submissions' | 'remedial'>('submissions');

  const { data: rows = [], isLoading, error, refetch, isFetching } = useQuery<SubmissionOverviewDto[]>({
    queryKey: ['submissions', 'all'],
    queryFn: async () => {
      const res = await fetch('/api/submissions');
      if (!res.ok) throw new Error(`Failed to load submissions (${res.status})`);
      return res.json();
    },
  });

  const counts = useMemo(() => {
    const base: Record<string, number> = { ALL: rows.length };
    rows.forEach((r) => {
      base[r.status] = (base[r.status] ?? 0) + 1;
    });
    return base;
  }, [rows]);

  const visible = statusFilter === 'ALL' ? rows : rows.filter((r) => r.status === statusFilter);

  const formatDate = (iso: string | null) => {
    if (!iso) return '—';
    return new Date(iso).toLocaleString('en-IN', {
      timeZone: 'Asia/Kolkata',
      dateStyle: 'medium',
      timeStyle: 'short',
    });
  };

  const openRow = (row: SubmissionOverviewDto) => {
    setSelected({
      id: row.assignmentId,
      deadlineId: row.deadlineId,
      title: row.deadlineTitle,
      description: '',
      type: row.deadlineType as DeadlineAssignmentDto['type'],
      dueAt: row.dueAt,
      facultyId: row.facultyId,
      facultyName: row.facultyName,
      offeringId: null,
      isOverdue: row.isOverdue,
    });
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="font-sans text-2xl font-bold text-slate-900 tracking-tight">
            Submission Review Board
          </h2>
          <p className="text-sm text-slate-600 mt-0.5">
            Every file faculty have submitted against department deadlines, in one queue. Download,
            accept or return each one.
          </p>
        </div>

        <div className="flex items-center gap-2.5 self-start sm:self-auto">
          <button
            type="button"
            onClick={() => refetch()}
            className="p-2 border border-border rounded-md text-slate-600 hover:text-slate-900 hover:bg-slate-50 transition-colors"
            title="Refresh submissions"
          >
            <RefreshCw className={`w-4 h-4 ${isFetching ? 'animate-spin text-blue-600' : ''}`} />
          </button>
          <a
            href="/api/reports/submissions/export"
            download
            className="inline-flex items-center gap-2 px-4 py-2 bg-slate-900 text-white text-xs font-semibold rounded-md hover:bg-slate-800 shadow-xs transition-colors"
          >
            <FileSpreadsheet className="w-4 h-4" />
            Export Excel Report (.xlsx)
          </a>
        </div>
      </div>

      {/* Board switcher: deadline submissions vs the remedial plans faculty filed. */}
      <div role="tablist" className="flex items-center gap-0.5 rounded-lg border border-border bg-white p-0.5 w-fit text-xs">
        {([['submissions', 'Deadline submissions'], ['remedial', 'Remedial plans']] as const).map(([id, label]) => (
          <button
            key={id}
            type="button"
            role="tab"
            aria-selected={board === id}
            onClick={() => setBoard(id)}
            className={`px-3 h-7 rounded-md font-semibold transition-colors ${
              board === id ? 'bg-slate-900 text-white' : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {board === 'remedial' ? (
        <RemedialReview />
      ) : (
        <>
      {/* Filter chips */}
      <div className="flex flex-wrap items-center gap-2">
        {STATUS_FILTERS.map((f) => {
          const active = statusFilter === f.id;
          const count = counts[f.id] ?? 0;
          return (
            <button
              key={f.id}
              type="button"
              onClick={() => setStatusFilter(f.id)}
              className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold border transition-colors ${
                active
                  ? 'bg-slate-900 text-white border-slate-900'
                  : 'bg-white text-slate-600 border-border hover:text-slate-900 hover:bg-slate-50'
              }`}
            >
              {f.label}
              <span
                className={`tabular-nums text-[10px] px-1.5 py-0.5 rounded-full ${
                  active ? 'bg-white/15 text-white' : 'bg-slate-100 text-slate-500'
                }`}
              >
                {count}
              </span>
            </button>
          );
        })}
      </div>

      {error && (
        <div className="bg-rose-50 border border-rose-200 text-rose-900 px-4 py-3 rounded-lg text-xs font-medium flex items-center gap-2">
          <AlertCircle className="w-4 h-4 text-rose-600 shrink-0" />
          <span>{(error as Error).message}</span>
        </div>
      )}

      {/* Table */}
      <div className="bg-white rounded-lg border border-border shadow-xs overflow-hidden">
        {isLoading ? (
          <div className="p-8 text-center text-xs text-slate-500 animate-pulse">Loading submissions...</div>
        ) : visible.length === 0 ? (
          <div className="p-12 text-center">
            <Inbox className="w-10 h-10 text-slate-300 mx-auto mb-2" />
            <h3 className="font-sans text-base font-bold text-slate-800">Nothing here yet</h3>
            <p className="text-xs text-slate-500 mt-1">
              {rows.length === 0
                ? 'No deadline assignments exist. Create a deadline to generate submission slots.'
                : 'No submissions match this filter.'}
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-border text-left">
              <thead className="bg-slate-50 text-[11px] font-semibold text-slate-600 uppercase tracking-wider">
                <tr>
                  <th className="px-5 py-3">Faculty</th>
                  <th className="px-5 py-3">Deadline</th>
                  <th className="px-5 py-3">Type</th>
                  <th className="px-5 py-3">Due</th>
                  <th className="px-5 py-3">Status</th>
                  <th className="px-5 py-3">Submitted</th>
                  <th className="px-5 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border text-xs">
                {visible.map((row) => (
                  <tr key={row.assignmentId} className="hover:bg-slate-50/70 transition-colors">
                    <td className="px-5 py-3.5 font-semibold text-slate-900 whitespace-nowrap">
                      {row.facultyName}
                    </td>
                    <td className="px-5 py-3.5 text-slate-700">
                      <div className="font-medium text-slate-800">{row.deadlineTitle}</div>
                      {row.latestFileName && (
                        <div className="text-[10px] text-slate-500 flex items-center gap-1 mt-0.5">
                          <FileText className="w-3 h-3" />
                          <span className="truncate max-w-[220px]">{row.latestFileName}</span>
                          {row.totalVersions > 1 && (
                            <span className="font-mono text-slate-400">v{row.totalVersions}</span>
                          )}
                        </div>
                      )}
                    </td>
                    <td className="px-5 py-3.5">
                      <span className="px-2 py-0.5 rounded-full font-medium bg-slate-100 text-slate-700 border border-slate-200 text-[10px] whitespace-nowrap">
                        {row.deadlineType.replace('_', ' ')}
                      </span>
                    </td>
                    <td className="px-5 py-3.5 text-slate-600 whitespace-nowrap">
                      {formatDate(row.dueAt)}
                      {row.isOverdue && !['ACCEPTED', 'SUBMITTED', 'LATE'].includes(row.status) && (
                        <div className="text-[10px] font-semibold text-rose-600 mt-0.5">Past due</div>
                      )}
                    </td>
                    <td className="px-5 py-3.5">
                      <StatusBadge status={row.status} />
                    </td>
                    <td className="px-5 py-3.5 text-slate-600 whitespace-nowrap">
                      {formatDate(row.submittedAt)}
                    </td>
                    <td className="px-5 py-3.5">
                      <div className="flex items-center justify-end gap-2">
                        {row.latestFileId && (
                          <a
                            href={`/api/submission-files/${row.latestFileId}/download`}
                            download
                            className="inline-flex items-center gap-1 px-2.5 py-1 bg-white border border-border rounded text-[11px] font-medium text-slate-700 hover:bg-slate-50 transition-colors shadow-2xs"
                          >
                            <Download className="w-3 h-3" />
                            File
                          </a>
                        )}
                        <button
                          type="button"
                          onClick={() => openRow(row)}
                          className="inline-flex items-center gap-1 px-2.5 py-1 bg-slate-900 text-white rounded text-[11px] font-semibold hover:bg-slate-800 transition-colors"
                        >
                          <FileText className="w-3 h-3" />
                          Review
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {selected && <SubmissionModal assignment={selected} onClose={() => setSelected(null)} />}
        </>
      )}
    </div>
  );
}

interface RemedialRow {
  id: number;
  offeringId: number;
  courseCode: string;
  courseName: string;
  section: string;
  facultyId: number;
  facultyName: string;
  reason: string;
  laggingTopics: string | null;
  action: string;
  extraSessions: number;
  targetDate: string;
  status: string;
  createdAt: string;
}

const REASON_LABEL: Record<string, string> = {
  BEHIND_SYLLABUS: 'Behind syllabus',
  LOW_ASSESSMENT: 'Low assessment scores',
  CANCELLED_CLASSES: 'Cancelled classes',
  OTHER: 'Other',
};

/**
 * The remedial plans faculty have filed, reviewed here beside their file submissions. A plan is
 * filed from the faculty side (My Courses) and lands in this queue automatically.
 */
function RemedialReview() {
  const queryClient = useQueryClient();

  const { data: rows = [], isLoading, error } = useQuery<RemedialRow[]>({
    queryKey: ['remedial-plans'],
    queryFn: async () => {
      const res = await fetch('/api/remedial-plans', { credentials: 'include' });
      if (!res.ok) throw new Error(`Failed to load remedial plans (${res.status})`);
      return res.json();
    },
  });

  const complete = useMutation({
    mutationFn: async (id: number) => {
      const res = await fetch(`/api/remedial-plans/${id}/complete`, { method: 'POST', credentials: 'include' });
      if (!res.ok) throw new Error('Could not update the plan');
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['remedial-plans'] }),
  });

  const open = rows.filter((r) => r.status !== 'COMPLETED');

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-6 text-[13px]">
        <div>
          <div className="text-[var(--ink-subtle)]">Open plans</div>
          <div className="font-display text-[26px] leading-none num">{open.length}</div>
        </div>
        <div>
          <div className="text-[var(--ink-subtle)]">Completed</div>
          <div className="font-display text-[26px] leading-none num">{rows.length - open.length}</div>
        </div>
      </div>

      {error && (
        <div className="bg-rose-50 border border-rose-200 text-rose-900 px-4 py-3 rounded-lg text-xs font-medium flex items-center gap-2">
          <AlertCircle className="w-4 h-4 text-rose-600 shrink-0" />
          <span>{(error as Error).message}</span>
        </div>
      )}

      <div className="bg-white rounded-lg border border-border shadow-xs overflow-hidden">
        {isLoading ? (
          <div className="p-8 text-center text-xs text-slate-500 animate-pulse">Loading remedial plans...</div>
        ) : rows.length === 0 ? (
          <div className="p-12 text-center">
            <Inbox className="w-10 h-10 text-slate-300 mx-auto mb-2" />
            <h3 className="font-sans text-base font-bold text-slate-800">No remedial plans filed</h3>
            <p className="text-xs text-slate-500 mt-1">
              Faculty file a plan from My Courses when a section falls 2+ topics behind or scores poorly.
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-border text-left">
              <thead className="bg-slate-50 text-[11px] font-semibold text-slate-600 uppercase tracking-wider">
                <tr>
                  <th className="px-5 py-3">Course / Section</th>
                  <th className="px-5 py-3">Faculty</th>
                  <th className="px-5 py-3">Reason</th>
                  <th className="px-5 py-3">Plan</th>
                  <th className="px-5 py-3">Target</th>
                  <th className="px-5 py-3">Status</th>
                  <th className="px-5 py-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border text-xs">
                {rows.map((r) => (
                  <tr key={r.id} className="hover:bg-slate-50/70 transition-colors align-top">
                    <td className="px-5 py-3.5 whitespace-nowrap">
                      <div className="font-mono font-semibold text-slate-900">{r.courseCode}</div>
                      <div className="text-[10px] text-slate-500">Sec {r.section}</div>
                    </td>
                    <td className="px-5 py-3.5 font-semibold text-slate-900 whitespace-nowrap">{r.facultyName}</td>
                    <td className="px-5 py-3.5 text-slate-700 whitespace-nowrap">{REASON_LABEL[r.reason] ?? r.reason}</td>
                    <td className="px-5 py-3.5 text-slate-700 max-w-[320px]">
                      <div>{r.action}</div>
                      {r.laggingTopics && <div className="text-[10px] text-slate-500 mt-0.5">Areas: {r.laggingTopics}</div>}
                    </td>
                    <td className="px-5 py-3.5 whitespace-nowrap text-slate-600">
                      <div>{new Date(`${r.targetDate}T00:00:00`).toLocaleDateString()}</div>
                      <div className="text-[10px] text-slate-500">{r.extraSessions} extra sessions</div>
                    </td>
                    <td className="px-5 py-3.5">
                      <span className={`inline-flex items-center gap-1 text-[10px] font-semibold px-2 py-0.5 rounded-full border ${
                        r.status === 'COMPLETED'
                          ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                          : 'bg-blue-50 text-blue-700 border-blue-200'
                      }`}>
                        {r.status === 'COMPLETED' ? 'Completed' : 'Submitted'}
                      </span>
                    </td>
                    <td className="px-5 py-3.5 text-right">
                      {r.status !== 'COMPLETED' ? (
                        <button
                          type="button"
                          onClick={() => complete.mutate(r.id)}
                          disabled={complete.isPending}
                          className="inline-flex items-center gap-1 px-2.5 py-1 bg-slate-900 text-white rounded text-[11px] font-semibold hover:bg-slate-800 transition-colors disabled:opacity-50"
                        >
                          Mark completed
                        </button>
                      ) : (
                        <span className="text-[11px] text-slate-400">—</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
