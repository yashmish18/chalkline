import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  DepartmentMetricsDto,
  DeadlineTrackingRowDto,
  FacultyComplianceRowDto,
  AuditLogDto,
} from '../types/tracking';
import {
  AlertTriangle,
  CheckCircle2,
  Clock,
  TrendingUp,
  FileSpreadsheet,
  Users,
  BellRing,
  ShieldAlert,
  Calendar,
  History,
  Check,
} from 'lucide-react';

export function TrackingDashboardView() {
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState<'deadlines' | 'faculty' | 'audit'>('deadlines');
  const [reminderToast, setReminderToast] = useState<string | null>(null);

  // Fetch KPI Metrics
  const { data: metrics, isLoading: isMetricsLoading } = useQuery<DepartmentMetricsDto>({
    queryKey: ['tracking', 'metrics'],
    queryFn: async () => {
      const res = await fetch('/api/tracking/metrics');
      if (!res.ok) throw new Error('Failed to fetch metrics');
      return res.json();
    },
  });

  // Fetch Deadline Tracking Summary
  const { data: deadlineRows = [], isLoading: isDeadlinesLoading } = useQuery<DeadlineTrackingRowDto[]>({
    queryKey: ['tracking', 'by-deadline'],
    queryFn: async () => {
      const res = await fetch('/api/tracking/by-deadline');
      if (!res.ok) throw new Error('Failed to fetch deadline tracking');
      return res.json();
    },
    enabled: activeTab === 'deadlines',
  });

  // Fetch Faculty Compliance Summary
  const { data: facultyRows = [], isLoading: isFacultyLoading } = useQuery<FacultyComplianceRowDto[]>({
    queryKey: ['tracking', 'by-faculty'],
    queryFn: async () => {
      const res = await fetch('/api/tracking/by-faculty');
      if (!res.ok) throw new Error('Failed to fetch faculty compliance');
      return res.json();
    },
    enabled: activeTab === 'faculty',
  });

  // Fetch Recent Audit Logs
  const { data: auditLogs = [], isLoading: isAuditLoading } = useQuery<AuditLogDto[]>({
    queryKey: ['tracking', 'audit-logs'],
    queryFn: async () => {
      const res = await fetch('/api/tracking/audit-logs?limit=30');
      if (!res.ok) throw new Error('Failed to fetch audit logs');
      return res.json();
    },
    enabled: activeTab === 'audit',
  });

  // Trigger automated reminders mutation
  const triggerRemindersMutation = useMutation({
    mutationFn: async () => {
      const res = await fetch('/api/notifications/trigger-reminders', { method: 'POST' });
      if (!res.ok) throw new Error('Failed to trigger reminders');
      return res.json();
    },
    onSuccess: (data) => {
      setReminderToast(`Triggered ${data.triggeredReminders} automated notification alerts successfully.`);
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
      setTimeout(() => setReminderToast(null), 5000);
    },
  });

  const formatDate = (isoString: string) => {
    return new Date(isoString).toLocaleString('en-IN', {
      timeZone: 'Asia/Kolkata',
      dateStyle: 'medium',
      timeStyle: 'short',
    });
  };

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="font-sans text-2xl font-bold text-slate-900 tracking-tight">
            Compliance Tracking &amp; Submissions Audit
          </h2>
          <p className="text-sm text-slate-600 mt-0.5">
            Real-time department KPIs, drill-down faculty compliance roster, and Excel audit export.
          </p>
        </div>

        <div className="flex items-center gap-2.5 self-start sm:self-auto">
          <button
            type="button"
            onClick={() => triggerRemindersMutation.mutate()}
            disabled={triggerRemindersMutation.isPending}
            className="inline-flex items-center gap-1.5 px-3 py-2 bg-white border border-border rounded-md text-xs font-medium text-slate-700 hover:bg-slate-50 transition-colors shadow-2xs disabled:opacity-50"
            title="Scan upcoming & overdue deadlines and dispatch notifications"
          >
            <BellRing className="w-3.5 h-3.5 text-amber-600" />
            {triggerRemindersMutation.isPending ? 'Dispatching...' : 'Dispatch Reminders'}
          </button>

          <a
            href="/api/reports/submissions/export"
            download
            className="inline-flex items-center gap-2 px-4 py-2 bg-primary text-white text-xs font-semibold rounded-md hover:bg-primary-hover shadow-xs transition-colors"
          >
            <FileSpreadsheet className="w-4 h-4" />
            Export Excel Report (.xlsx)
          </a>
        </div>
      </div>

      {/* Reminder Toast */}
      {reminderToast && (
        <div className="p-3 bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs rounded-md flex items-center justify-between animate-in fade-in duration-150">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-emerald-600" />
            <span>{reminderToast}</span>
          </div>
          <button
            type="button"
            onClick={() => setReminderToast(null)}
            className="text-emerald-600 hover:text-emerald-900 font-bold"
          >
            ×
          </button>
        </div>
      )}

      {/* 4 KPI Metric Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Metric 1: Submission Rate */}
        <div className="bg-white rounded-lg border border-border p-5 shadow-xs">
          <div className="flex items-center justify-between text-slate-500">
            <span className="text-xs font-semibold uppercase tracking-wider">Submission Rate</span>
            <TrendingUp className="w-4 h-4 text-primary" />
          </div>
          <div className="text-2xl font-bold font-sans text-slate-900 mt-2">
            {isMetricsLoading ? '—' : `${metrics?.submissionRate ?? 0}%`}
          </div>
          <p className="text-xs text-slate-500 mt-1">
            {metrics?.totalSubmissions ?? 0} of {metrics?.totalAssignments ?? 0} slots submitted
          </p>
          <div className="w-full bg-slate-100 rounded-full h-1.5 mt-3 overflow-hidden">
            <div
              className="bg-primary h-1.5 rounded-full transition-all duration-500"
              style={{ width: `${Math.min(metrics?.submissionRate ?? 0, 100)}%` }}
            />
          </div>
        </div>

        {/* Metric 2: On-Time Rate */}
        <div className="bg-white rounded-lg border border-border p-5 shadow-xs">
          <div className="flex items-center justify-between text-slate-500">
            <span className="text-xs font-semibold uppercase tracking-wider">On-Time Rate</span>
            <CheckCircle2 className="w-4 h-4 text-emerald-600" />
          </div>
          <div className="text-2xl font-bold font-sans text-slate-900 mt-2">
            {isMetricsLoading ? '—' : `${metrics?.onTimeRate ?? 0}%`}
          </div>
          <p className="text-xs text-slate-500 mt-1">
            {metrics?.onTimeSubmissions ?? 0} on-time submissions
          </p>
          <div className="w-full bg-slate-100 rounded-full h-1.5 mt-3 overflow-hidden">
            <div
              className="bg-emerald-500 h-1.5 rounded-full transition-all duration-500"
              style={{ width: `${Math.min(metrics?.onTimeRate ?? 0, 100)}%` }}
            />
          </div>
        </div>

        {/* Metric 3: Overdue Submissions */}
        <div className="bg-white rounded-lg border border-border p-5 shadow-xs">
          <div className="flex items-center justify-between text-slate-500">
            <span className="text-xs font-semibold uppercase tracking-wider">Overdue Slots</span>
            <Clock className="w-4 h-4 text-rose-500" />
          </div>
          <div className="text-2xl font-bold font-sans text-slate-900 mt-2">
            {isMetricsLoading ? '—' : metrics?.overdueCount ?? 0}
          </div>
          <p className="text-xs text-slate-500 mt-1">Pending slots past due date</p>
          <div className="mt-3">
            {(metrics?.overdueCount ?? 0) > 0 ? (
              <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-rose-700 bg-rose-50 px-2 py-0.5 rounded-full border border-rose-200">
                <AlertTriangle className="w-3 h-3 text-rose-500" /> Action Required
              </span>
            ) : (
              <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-full border border-emerald-200">
                <Check className="w-3 h-3 text-emerald-600" /> All On Track
              </span>
            )}
          </div>
        </div>

        {/* Metric 4: Critical Faculty */}
        <div className="bg-white rounded-lg border border-border p-5 shadow-xs">
          <div className="flex items-center justify-between text-slate-500">
            <span className="text-xs font-semibold uppercase tracking-wider">Critical Roster</span>
            <ShieldAlert className="w-4 h-4 text-amber-500" />
          </div>
          <div className="text-2xl font-bold font-sans text-slate-900 mt-2">
            {isMetricsLoading ? '—' : metrics?.criticalFacultyCount ?? 0}
          </div>
          <p className="text-xs text-slate-500 mt-1">
            Faculty with compliance &lt; 60%
          </p>
          <div className="mt-3">
            {(metrics?.criticalFacultyCount ?? 0) > 0 ? (
              <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-amber-800 bg-amber-50 px-2 py-0.5 rounded-full border border-amber-200">
                Needs Follow-up
              </span>
            ) : (
              <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-slate-600 bg-slate-100 px-2 py-0.5 rounded-full">
                Zero Critical
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className="border-b border-border flex items-center gap-6">
        <button
          type="button"
          onClick={() => setActiveTab('deadlines')}
          className={`py-3 text-xs font-semibold border-b-2 transition-all flex items-center gap-2 ${
            activeTab === 'deadlines'
              ? 'border-primary text-primary'
              : 'border-transparent text-slate-600 hover:text-slate-900'
          }`}
        >
          <Calendar className="w-4 h-4" />
          Tracking by Deadline
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('faculty')}
          className={`py-3 text-xs font-semibold border-b-2 transition-all flex items-center gap-2 ${
            activeTab === 'faculty'
              ? 'border-primary text-primary'
              : 'border-transparent text-slate-600 hover:text-slate-900'
          }`}
        >
          <Users className="w-4 h-4" />
          Tracking by Faculty
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('audit')}
          className={`py-3 text-xs font-semibold border-b-2 transition-all flex items-center gap-2 ${
            activeTab === 'audit'
              ? 'border-primary text-primary'
              : 'border-transparent text-slate-600 hover:text-slate-900'
          }`}
        >
          <History className="w-4 h-4" />
          Coordinator Audit Trail
        </button>
      </div>

      {/* Tab 1: Tracking by Deadline */}
      {activeTab === 'deadlines' && (
        <div className="bg-white rounded-lg border border-border shadow-xs overflow-hidden">
          {isDeadlinesLoading ? (
            <div className="p-8 text-center text-xs text-slate-500">Loading deadline tracking...</div>
          ) : deadlineRows.length === 0 ? (
            <div className="p-12 text-center text-xs text-slate-500">No deadlines available to track.</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-border text-left">
                <thead className="bg-slate-50 text-[11px] font-semibold text-slate-600 uppercase tracking-wider">
                  <tr>
                    <th className="px-5 py-3">Deadline Title</th>
                    <th className="px-5 py-3">Type</th>
                    <th className="px-5 py-3">Due Date</th>
                    <th className="px-5 py-3 text-center">Assigned</th>
                    <th className="px-5 py-3 text-center">Submitted</th>
                    <th className="px-5 py-3 text-center">Accepted</th>
                    <th className="px-5 py-3 text-center">Overdue</th>
                    <th className="px-5 py-3 text-right">Compliance Rate</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border text-xs">
                  {deadlineRows.map((row) => (
                    <tr key={row.deadlineId} className="hover:bg-slate-50/70 transition-colors">
                      <td className="px-5 py-3.5 font-semibold text-slate-900">
                        {row.title}
                      </td>
                      <td className="px-5 py-3.5">
                        <span className="px-2 py-0.5 rounded-full font-medium bg-slate-100 text-slate-700 border border-slate-200 text-[10px]">
                          {row.type.replace('_', ' ')}
                        </span>
                      </td>
                      <td className="px-5 py-3.5 text-slate-600 whitespace-nowrap">
                        {formatDate(row.dueAt)}
                      </td>
                      <td className="px-5 py-3.5 text-center font-medium text-slate-700">
                        {row.totalAssigned}
                      </td>
                      <td className="px-5 py-3.5 text-center font-medium text-blue-700">
                        {row.submittedCount}
                      </td>
                      <td className="px-5 py-3.5 text-center font-medium text-emerald-700">
                        {row.acceptedCount}
                      </td>
                      <td className="px-5 py-3.5 text-center font-medium text-rose-700">
                        {row.overdueCount}
                      </td>
                      <td className="px-5 py-3.5 text-right">
                        <div className="flex items-center justify-end gap-2">
                          <span className="font-semibold text-slate-900">{row.complianceRate}%</span>
                          <div className="w-16 bg-slate-100 rounded-full h-1.5 overflow-hidden">
                            <div
                              className={`h-1.5 rounded-full ${
                                row.complianceRate >= 85
                                  ? 'bg-emerald-500'
                                  : row.complianceRate >= 60
                                  ? 'bg-amber-500'
                                  : 'bg-rose-500'
                              }`}
                              style={{ width: `${Math.min(row.complianceRate, 100)}%` }}
                            />
                          </div>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Tab 2: Tracking by Faculty */}
      {activeTab === 'faculty' && (
        <div className="bg-white rounded-lg border border-border shadow-xs overflow-hidden">
          {isFacultyLoading ? (
            <div className="p-8 text-center text-xs text-slate-500">Loading faculty compliance...</div>
          ) : facultyRows.length === 0 ? (
            <div className="p-12 text-center text-xs text-slate-500">No faculty members found.</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-border text-left">
                <thead className="bg-slate-50 text-[11px] font-semibold text-slate-600 uppercase tracking-wider">
                  <tr>
                    <th className="px-5 py-3">Faculty Member</th>
                    <th className="px-5 py-3">Designation</th>
                    <th className="px-5 py-3 text-center">Assigned</th>
                    <th className="px-5 py-3 text-center">Submitted</th>
                    <th className="px-5 py-3 text-center">Accepted</th>
                    <th className="px-5 py-3 text-center">Pending</th>
                    <th className="px-5 py-3 text-center">Compliance</th>
                    <th className="px-5 py-3 text-right">Status Tier</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border text-xs">
                  {facultyRows.map((row) => (
                    <tr key={row.facultyId} className="hover:bg-slate-50/70 transition-colors">
                      <td className="px-5 py-3.5 font-semibold text-slate-900">
                        {row.facultyName}
                      </td>
                      <td className="px-5 py-3.5 text-slate-600">
                        {row.designation} ({row.department})
                      </td>
                      <td className="px-5 py-3.5 text-center font-medium text-slate-700">
                        {row.totalAssigned}
                      </td>
                      <td className="px-5 py-3.5 text-center font-medium text-blue-700">
                        {row.submittedCount}
                      </td>
                      <td className="px-5 py-3.5 text-center font-medium text-emerald-700">
                        {row.acceptedCount}
                      </td>
                      <td className="px-5 py-3.5 text-center font-medium text-amber-700">
                        {row.pendingCount}
                      </td>
                      <td className="px-5 py-3.5 text-center font-bold text-slate-900">
                        {row.complianceRate}%
                      </td>
                      <td className="px-5 py-3.5 text-right">
                        {row.complianceStatus === 'GOOD' && (
                          <span className="inline-flex items-center gap-1 text-[11px] px-2.5 py-0.5 rounded-full font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                            <Check className="w-3 h-3 text-emerald-600" /> High (≥ 85%)
                          </span>
                        )}
                        {row.complianceStatus === 'WATCH' && (
                          <span className="inline-flex items-center gap-1 text-[11px] px-2.5 py-0.5 rounded-full font-semibold bg-amber-50 text-amber-800 border border-amber-200">
                            Watch (60–84%)
                          </span>
                        )}
                        {row.complianceStatus === 'CRITICAL' && (
                          <span className="inline-flex items-center gap-1 text-[11px] px-2.5 py-0.5 rounded-full font-semibold bg-rose-50 text-rose-700 border border-rose-200">
                            <AlertTriangle className="w-3 h-3 text-rose-600" /> Critical (&lt; 60%)
                          </span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Tab 3: Audit Trail */}
      {activeTab === 'audit' && (
        <div className="bg-white rounded-lg border border-border shadow-xs overflow-hidden">
          {isAuditLoading ? (
            <div className="p-8 text-center text-xs text-slate-500">Loading audit history...</div>
          ) : auditLogs.length === 0 ? (
            <div className="p-12 text-center text-xs text-slate-500">No actions logged yet.</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-border text-left">
                <thead className="bg-slate-50 text-[11px] font-semibold text-slate-600 uppercase tracking-wider">
                  <tr>
                    <th className="px-5 py-3">Timestamp</th>
                    <th className="px-5 py-3">User</th>
                    <th className="px-5 py-3">Action</th>
                    <th className="px-5 py-3">Target</th>
                    <th className="px-5 py-3">Details</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border text-xs font-mono">
                  {auditLogs.map((log) => (
                    <tr key={log.id} className="hover:bg-slate-50/70 transition-colors">
                      <td className="px-5 py-3 text-slate-500 whitespace-nowrap">
                        {formatDate(log.createdAt)}
                      </td>
                      <td className="px-5 py-3 font-sans font-semibold text-slate-800">
                        {log.userName}
                      </td>
                      <td className="px-5 py-3">
                        <span className="px-2 py-0.5 rounded font-mono font-semibold text-[10px] bg-slate-100 text-slate-800 border border-slate-200">
                          {log.action}
                        </span>
                      </td>
                      <td className="px-5 py-3 text-slate-600">
                        {log.targetEntity} {log.targetId ? `#${log.targetId}` : ''}
                      </td>
                      <td className="px-5 py-3 font-sans text-slate-600">
                        {log.details}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
