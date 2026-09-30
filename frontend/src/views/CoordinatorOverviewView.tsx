import { useState, useEffect, useCallback } from 'react';
import { OverviewSummaryDto, ComplianceMatrixDto } from '../types/tracking';
import {
  TrendingUp,
  Clock,
  AlertTriangle,
  CheckCircle2,
  BellRing,
  RefreshCw,
  BarChart3,
  LineChart as LineChartIcon,
  Table as TableIcon,
  ShieldAlert,
  Send
} from 'lucide-react';

export function CoordinatorOverviewView() {
  const [summary, setSummary] = useState<OverviewSummaryDto | null>(null);
  const [matrix, setMatrix] = useState<ComplianceMatrixDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [remindToast, setRemindToast] = useState<string | null>(null);
  const [remindingId, setRemindingId] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [sumRes, matRes] = await Promise.all([
        fetch('/api/tracking/overview-summary', { credentials: 'include' }),
        fetch('/api/tracking/compliance-matrix', { credentials: 'include' })
      ]);

      if (!sumRes.ok || !matRes.ok) {
        throw new Error('Failed to fetch coordinator overview data');
      }

      const sumData: OverviewSummaryDto = await sumRes.json();
      const matData: ComplianceMatrixDto = await matRes.json();

      setSummary(sumData);
      setMatrix(matData);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Error connecting to analytics service';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleRemindFaculty = async (item: OverviewSummaryDto['needsAttention'][0]) => {
    if (!item.facultyId) {
      alert('No faculty member linked to this item');
      return;
    }

    setRemindingId(item.id);
    try {
      const res = await fetch('/api/notifications/remind-faculty', {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          facultyId: item.facultyId,
          title: `Action Required: ${item.title}`,
          message: `The Department Coordinator has requested your attention: ${item.subtitle}. Please log today's session or complete the required submission.`
        })
      });

      if (!res.ok) {
        throw new Error('Failed to dispatch reminder');
      }

      setRemindToast(`Official reminder dispatched to ${item.facultyName} via in-app alert & email.`);
      setTimeout(() => setRemindToast(null), 5000);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to send reminder';
      alert(msg);
    } finally {
      setRemindingId(null);
    }
  };

  // SVG Chart Helper for Conduct Trajectory
  const renderConductLineChart = (trajectory: OverviewSummaryDto['conductTrajectory']) => {
    if (!trajectory || trajectory.length === 0) return null;

    const width = 500;
    const height = 160;
    const padding = 28;

    const minX = 1;
    const maxX = Math.max(11, trajectory.length);
    const minY = 60; // Conduct rate floor for visual sensitivity
    const maxY = 100;

    const getX = (week: number) => padding + ((week - minX) / (maxX - minX)) * (width - 2 * padding);
    const getY = (rate: number) => height - padding - ((rate - minY) / (maxY - minY)) * (height - 2 * padding);

    const points = trajectory.map((p) => `${getX(p.weekNumber)},${getY(p.conductRate)}`).join(' ');

    return (
      <svg viewBox={`0 0 ${width} ${height}`} className="w-full h-44 overflow-visible">
        {/* Horizontal Guide Lines */}
        {[70, 80, 90, 100].map((level) => {
          const y = getY(level);
          return (
            <g key={level}>
              <line x1={padding} y1={y} x2={width - padding} y2={y} stroke="#E2E8F0" strokeDasharray="3 3" />
              <text x={padding - 6} y={y + 3} textAnchor="end" fontSize="9" fill="#94A3B8" fontWeight="600">
                {level}%
              </text>
            </g>
          );
        })}

        {/* Shaded Area under Curve */}
        <polygon
          points={`${getX(minX)},${height - padding} ${points} ${getX(trajectory[trajectory.length - 1].weekNumber)},${height - padding}`}
          fill="url(#conductGradient)"
          opacity="0.25"
        />

        {/* Gradient Def */}
        <defs>
          <linearGradient id="conductGradient" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#2563EB" stopOpacity="0.8" />
            <stop offset="100%" stopColor="#2563EB" stopOpacity="0.0" />
          </linearGradient>
        </defs>

        {/* Trend Polyline */}
        <polyline fill="none" stroke="#2563EB" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" points={points} />

        {/* Data Points */}
        {trajectory.map((p) => {
          const cx = getX(p.weekNumber);
          const cy = getY(p.conductRate);
          return (
            <g key={p.weekNumber} className="group">
              <circle
                cx={cx}
                cy={cy}
                r="3.5"
                fill={p.conductRate < 85 ? '#DC2626' : '#2563EB'}
                stroke="#FFFFFF"
                strokeWidth="1.5"
                className="transition-all hover:r-5 cursor-pointer"
              />
              <text
                x={cx}
                y={height - padding + 14}
                textAnchor="middle"
                fontSize="9"
                fill="#64748B"
                fontWeight="500"
              >
                W{p.weekNumber}
              </text>
            </g>
          );
        })}
      </svg>
    );
  };

  return (
    <div className="space-y-6">
      {/* Top Banner */}
      <div className="bg-white rounded-lg border border-border p-6 shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 text-xs font-bold text-primary tracking-wide uppercase font-sans">
            <BarChart3 className="w-4 h-4" />
            Academic Operations — Overview
          </div>
          <h1 className="font-sans text-2xl font-bold text-slate-900 mt-1">
            Overview Dashboard
          </h1>
          <p className="text-xs text-slate-600 mt-1">
            Read-and-review surface derived dynamically from faculty daily class logs. Zero typed numbers.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={fetchData}
            className="p-2 border border-border rounded-md hover:bg-slate-50 text-slate-600 hover:text-slate-900 transition-colors"
            title="Refresh analytics"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin text-primary' : ''}`} />
          </button>
          <div className="px-3.5 py-1.5 rounded-md bg-slate-100 border border-slate-200 text-xs font-semibold text-slate-700">
            Active Term: <span className="text-primary font-bold">Semester 1 (2026-27)</span>
          </div>
        </div>
      </div>

      {remindToast && (
        <div className="p-3 bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs rounded-md flex items-center justify-between animate-in fade-in duration-150">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-emerald-600" />
            <span>{remindToast}</span>
          </div>
          <button
            type="button"
            onClick={() => setRemindToast(null)}
            className="text-emerald-600 hover:text-emerald-900 font-bold"
          >
            ×
          </button>
        </div>
      )}

      {error && (
        <div className="bg-rose-50 border border-rose-200 text-rose-900 px-4 py-3 rounded-lg text-xs font-medium flex items-center justify-between">
          <div className="flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 text-rose-600 shrink-0" />
            <span>{error}</span>
          </div>
          <button type="button" onClick={fetchData} className="font-bold underline text-rose-800 hover:text-rose-950 ml-4">
            Retry
          </button>
        </div>
      )}

      {/* 1 Row of 4 KPI Numbers */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* KPI 1 */}
        <div className="bg-white rounded-lg border border-border p-5 shadow-xs">
          <div className="flex items-center justify-between text-slate-500">
            <span className="text-xs font-semibold uppercase tracking-wider">Avg Syllabus Coverage</span>
            <TrendingUp className="w-4 h-4 text-primary" />
          </div>
          <div className="text-2xl font-bold font-sans text-slate-900 mt-2">
            {loading ? '—' : `${summary?.kpis.averageCoveragePercentage ?? 0}%`}
          </div>
          <p className="text-xs text-slate-500 mt-1">Derived from verified class topics</p>
          <div className="w-full bg-slate-100 rounded-full h-1.5 mt-3 overflow-hidden">
            <div
              className="bg-primary h-1.5 rounded-full transition-all duration-500"
              style={{ width: `${summary?.kpis.averageCoveragePercentage ?? 0}%` }}
            />
          </div>
        </div>

        {/* KPI 2 */}
        <div className="bg-white rounded-lg border border-border p-5 shadow-xs">
          <div className="flex items-center justify-between text-slate-500">
            <span className="text-xs font-semibold uppercase tracking-wider">Avg Conduct Rate</span>
            <Clock className="w-4 h-4 text-emerald-600" />
          </div>
          <div className="text-2xl font-bold font-sans text-slate-900 mt-2">
            {loading ? '—' : `${summary?.kpis.averageConductRatePercentage ?? 0}%`}
          </div>
          <p className="text-xs text-slate-500 mt-1">Held vs scheduled teaching sessions</p>
          <div className="w-full bg-slate-100 rounded-full h-1.5 mt-3 overflow-hidden">
            <div
              className="bg-emerald-500 h-1.5 rounded-full transition-all duration-500"
              style={{ width: `${summary?.kpis.averageConductRatePercentage ?? 0}%` }}
            />
          </div>
        </div>

        {/* KPI 3 */}
        <div className="bg-white rounded-lg border border-border p-5 shadow-xs">
          <div className="flex items-center justify-between text-slate-500">
            <span className="text-xs font-semibold uppercase tracking-wider">Courses Lagging Behind</span>
            <AlertTriangle className="w-4 h-4 text-amber-500" />
          </div>
          <div className="text-2xl font-bold font-sans text-slate-900 mt-2">
            {loading ? '—' : summary?.kpis.coursesBehindPacing ?? 0}
          </div>
          <p className="text-xs text-slate-500 mt-1">Below target weekly topic pacing</p>
          <div className="mt-3">
            {(summary?.kpis.coursesBehindPacing ?? 0) > 0 ? (
              <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-amber-800 bg-amber-50 px-2 py-0.5 rounded-full border border-amber-200">
                Remedial Action Recommended
              </span>
            ) : (
              <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-full border border-emerald-200">
                All Courses On Track
              </span>
            )}
          </div>
        </div>

        {/* KPI 4 */}
        <div className="bg-white rounded-lg border border-border p-5 shadow-xs">
          <div className="flex items-center justify-between text-slate-500">
            <span className="text-xs font-semibold uppercase tracking-wider">Actionable Anomalies</span>
            <ShieldAlert className="w-4 h-4 text-rose-500" />
          </div>
          <div className="text-2xl font-bold font-sans text-slate-900 mt-2">
            {loading ? '—' : summary?.kpis.needsAttentionCount ?? 0}
          </div>
          <p className="text-xs text-slate-500 mt-1">Pending items in Needs Attention</p>
          <div className="mt-3">
            {(summary?.kpis.needsAttentionCount ?? 0) > 0 ? (
              <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-rose-700 bg-rose-50 px-2 py-0.5 rounded-full border border-rose-200">
                Action Required
              </span>
            ) : (
              <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-slate-600 bg-slate-100 px-2 py-0.5 rounded-full">
                Zero Escalations
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Visuals Grid: 2 Columns (Worst-First Bar Chart & Weekly Conduct Trajectory) */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Visual 1: Worst-First Coverage Bar Chart */}
        <div className="bg-white rounded-lg border border-border p-5 shadow-xs space-y-4">
          <div className="flex items-center justify-between pb-3 border-b border-border">
            <div className="flex items-center gap-2">
              <BarChart3 className="w-4 h-4 text-primary" />
              <h2 className="font-sans text-sm font-bold text-slate-900 uppercase tracking-wider">
                Syllabus Coverage (Worst First)
              </h2>
            </div>
            <span className="text-[11px] text-slate-500 font-medium">Actual vs Target</span>
          </div>

          {loading ? (
            <div className="p-8 text-center text-xs text-slate-500">Loading course coverage ranking...</div>
          ) : summary?.worstFirstCoverage.length === 0 ? (
            <div className="p-8 text-center text-xs text-slate-500">No active course offerings found.</div>
          ) : (
            <div className="space-y-3.5">
              {summary?.worstFirstCoverage.map((item) => (
                <div key={item.offeringId} className="space-y-1.5">
                  <div className="flex items-center justify-between text-xs">
                    <div>
                      <span className="font-bold text-slate-900">{item.courseCode}</span>
                      <span className="text-slate-500 ml-1.5">({item.section})</span>
                      <span className="text-[11px] text-slate-500 ml-2">— {item.facultyName}</span>
                    </div>

                    <div className="flex items-center gap-2">
                      <span
                        className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${
                          item.pacingStatus === 'BEHIND'
                            ? 'bg-rose-100 text-rose-800'
                            : 'bg-emerald-100 text-emerald-800'
                        }`}
                      >
                        {item.coveragePercentage}% ({item.coveredTopics}/{item.totalTopics})
                      </span>
                    </div>
                  </div>

                  {/* Dual Bar: Actual (Foreground) vs Expected Target (Background) */}
                  <div className="w-full bg-slate-100 rounded-full h-2 relative overflow-hidden">
                    <div
                      className="absolute top-0 bottom-0 bg-slate-300 opacity-60 rounded-full"
                      style={{ width: `${Math.min(item.expectedPercentage, 100)}%` }}
                      title={`Target Expected: ${item.expectedPercentage}%`}
                    ></div>
                    <div
                      className={`h-full rounded-full relative ${
                        item.coveragePercentage < 60 ? 'bg-amber-500' : 'bg-primary'
                      }`}
                      style={{ width: `${Math.min(item.coveragePercentage, 100)}%` }}
                    ></div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Visual 2: Weekly Conduct Trajectory Line Chart */}
        <div className="bg-white rounded-lg border border-border p-5 shadow-xs space-y-4">
          <div className="flex items-center justify-between pb-3 border-b border-border">
            <div className="flex items-center gap-2">
              <LineChartIcon className="w-4 h-4 text-primary" />
              <h2 className="font-sans text-sm font-bold text-slate-900 uppercase tracking-wider">
                Weekly Conduct Rate Trajectory
              </h2>
            </div>
            <span className="text-[11px] text-slate-500 font-medium">Weeks 1 – 11</span>
          </div>

          {loading ? (
            <div className="p-8 text-center text-xs text-slate-500">Loading conduct trend...</div>
          ) : (
            <div className="pt-2">
              {renderConductLineChart(summary?.conductTrajectory || [])}
              <div className="flex items-center justify-between text-[11px] text-slate-500 border-t border-border/80 pt-3 mt-3">
                <div className="flex items-center gap-2">
                  <span className="w-2.5 h-2.5 rounded-full bg-blue-600 inline-block"></span>
                  <span>Held Lecture Rate (%)</span>
                </div>
                <span>Minimum Target: 80%</span>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Visual 3: Compliance Matrix & One Actionable List */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Visual 3: Compliance Matrix (2 cols) */}
        <div className="lg:col-span-2 bg-white rounded-lg border border-border shadow-xs overflow-hidden">
          <div className="p-5 border-b border-border flex items-center justify-between">
            <div className="flex items-center gap-2">
              <TableIcon className="w-4 h-4 text-primary" />
              <h2 className="font-sans text-sm font-bold text-slate-900 uppercase tracking-wider">
                Compliance Matrix
              </h2>
            </div>
            <span className="text-xs text-slate-500">4 Core Milestones</span>
          </div>

          {loading ? (
            <div className="p-8 text-center text-xs text-slate-500">Loading compliance matrix...</div>
          ) : !matrix || matrix.rows.length === 0 ? (
            <div className="p-8 text-center text-xs text-slate-500">No course rows available.</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-border text-left">
                <thead className="bg-slate-50 text-[11px] font-semibold text-slate-600 uppercase tracking-wider">
                  <tr>
                    <th className="px-4 py-3">Course</th>
                    <th className="px-4 py-3">Faculty</th>
                    {matrix.checkpoints.map((cp) => (
                      <th key={cp.key} className="px-4 py-3 text-center">
                        {cp.name}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-border text-xs">
                  {matrix.rows.map((row) => (
                    <tr key={row.offeringId} className="hover:bg-slate-50/70 transition-colors">
                      <td className="px-4 py-3 font-semibold text-slate-900 whitespace-nowrap">
                        {row.courseCode} <span className="text-slate-500 text-[11px]">({row.section})</span>
                      </td>
                      <td className="px-4 py-3 text-slate-700 whitespace-nowrap">
                        {row.facultyName}
                      </td>
                      {row.cells.map((cell) => (
                        <td key={cell.checkpointKey} className="px-4 py-3 text-center">
                          {cell.status === 'DONE' && (
                            <span
                              className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-emerald-50 text-emerald-800 border border-emerald-200"
                              title={cell.note}
                            >
                              <CheckCircle2 className="w-3 h-3 text-emerald-600" />
                              Done
                            </span>
                          )}
                          {cell.status === 'PENDING' && (
                            <span
                              className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-slate-100 text-slate-700 border border-slate-200"
                              title={cell.note}
                            >
                              Pending
                            </span>
                          )}
                          {cell.status === 'OVERDUE' && (
                            <span
                              className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-rose-50 text-rose-800 border border-rose-200"
                              title={cell.note}
                            >
                              <AlertTriangle className="w-3 h-3 text-rose-600" />
                              Overdue
                            </span>
                          )}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Actionable "Needs Attention" List (1 col) */}
        <div className="bg-white rounded-lg border border-border shadow-xs p-5 space-y-4">
          <div className="flex items-center justify-between pb-3 border-b border-border">
            <div className="flex items-center gap-2">
              <BellRing className="w-4 h-4 text-amber-500" />
              <h2 className="font-sans text-sm font-bold text-slate-900 uppercase tracking-wider">
                Needs Attention
              </h2>
            </div>
            <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-rose-100 text-rose-800">
              {summary?.needsAttention.length ?? 0} Pending
            </span>
          </div>

          {loading ? (
            <div className="p-8 text-center text-xs text-slate-500">Checking anomalies...</div>
          ) : !summary || summary.needsAttention.length === 0 ? (
            <div className="p-8 text-center text-xs text-slate-500">
              No critical anomalies detected. Department running smoothly.
            </div>
          ) : (
            <div className="space-y-3">
              {summary.needsAttention.map((item) => (
                <div
                  key={item.id}
                  className="p-3.5 rounded-md border border-border bg-slate-50 hover:bg-white transition-all space-y-2"
                >
                  <div className="flex items-start justify-between gap-2">
                    <span
                      className={`text-[9px] font-bold px-1.5 py-0.5 rounded uppercase tracking-wider ${
                        item.severity === 'CRITICAL'
                          ? 'bg-rose-100 text-rose-800'
                          : 'bg-amber-100 text-amber-800'
                      }`}
                    >
                      {item.severity}
                    </span>
                  </div>

                  <div>
                    <h3 className="text-xs font-bold text-slate-900 leading-snug">{item.title}</h3>
                    <p className="text-[11px] text-slate-500 mt-0.5">{item.subtitle}</p>
                  </div>

                  <div className="pt-1 flex items-center justify-end">
                    <button
                      type="button"
                      onClick={() => handleRemindFaculty(item)}
                      disabled={remindingId === item.id}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-primary text-white rounded text-[11px] font-bold hover:bg-primary-hover shadow-xs transition-colors disabled:opacity-50"
                    >
                      <Send className="w-3 h-3" />
                      {remindingId === item.id ? 'Sending...' : item.actionLabel}
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
