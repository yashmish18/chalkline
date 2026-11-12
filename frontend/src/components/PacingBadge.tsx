import type { CourseProgressDto } from '../types/academics';

/**
 * A section's pace. AHEAD / ON_TRACK / BEHIND are measured against the timetabled lectures;
 * NO_SYLLABUS and NOT_MEASURED are neutral and the label says what is missing.
 */
export function PacingBadge({ status, label }: { status: CourseProgressDto['pacingStatus']; label: string }) {
    if (status === 'NO_SYLLABUS' || status === 'NOT_MEASURED') {
      // Nothing to judge yet: neutral, and the label says what is missing.
      return (
        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-slate-50 text-slate-700 border border-slate-200">
          <span className="w-1.5 h-1.5 rounded-full bg-slate-400" />
          {label}
        </span>
      );
    }
    if (status === 'ON_TRACK') {
      return (
        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-50 text-emerald-800 border border-emerald-200">
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-600" />
          {label || 'On Track'}
        </span>
      );
    }
    if (status === 'AHEAD') {
      return (
        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-blue-50 text-blue-800 border border-blue-200">
          <span className="w-1.5 h-1.5 rounded-full bg-blue-600" />
          {label || 'Ahead'}
        </span>
      );
    }
    return (
      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-amber-50 text-amber-800 border border-amber-200">
        <span className="w-1.5 h-1.5 rounded-full bg-amber-600" />
        {label || 'Behind'}
      </span>
    );
  }
