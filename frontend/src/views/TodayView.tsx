import { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import {
  CalendarCheck,
  CheckCircle2,
  Clock,
  AlertCircle,
  Sparkles,
  ChevronRight,
  PlusCircle
} from 'lucide-react';

interface TodayClass {
  offeringId: number;
  courseCode: string;
  courseName: string;
  section: string;
  component: string;
  timeSlot: string;
  nextTopic: string;
  unitTitle: string;
  targetWeek: number;
  totalPlanned: number;
  completedSoFar: number;
  isLogged: boolean;
}

export function TodayView({ onNavigate }: { onNavigate?: (tab: string) => void }) {
  const { user } = useAuth();
  const [classes, setClasses] = useState<TodayClass[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedClass, setSelectedClass] = useState<TodayClass | null>(null);
  const [sessionStatus, setSessionStatus] = useState<'HELD' | 'CANCELLED' | 'SWAPPED'>('HELD');
  const [note, setNote] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  useEffect(() => {
    // In Phase 1 / Slice 8, provide structured initial classes mapped to active teaching assignments
    setTimeout(() => {
      setClasses([
        {
          offeringId: 1,
          courseCode: 'CS501',
          courseName: 'Computer Networks',
          section: 'Sec A',
          component: 'THEORY',
          timeSlot: '10:00 AM – 11:00 AM (Room 304)',
          nextTopic: 'TCP Congestion Control: AIMD, Slow Start & Fast Recovery',
          unitTitle: 'Unit 5: Transport & Application Layer',
          targetWeek: 11,
          totalPlanned: 15,
          completedSoFar: 13,
          isLogged: false,
        },
        {
          offeringId: 2,
          courseCode: 'CS502',
          courseName: 'Database Management Systems',
          section: 'Sec A',
          component: 'THEORY',
          timeSlot: '02:00 PM – 03:00 PM (Room 201)',
          nextTopic: 'Two-Phase Locking (2PL) Protocol & Deadlock Prevention',
          unitTitle: 'Unit 4: Transaction & Concurrency Control',
          targetWeek: 9,
          totalPlanned: 15,
          completedSoFar: 11,
          isLogged: false,
        },
      ]);
      setLoading(false);
    }, 200);
  }, []);

  const handleQuickLog = (cls: TodayClass) => {
    setSelectedClass(cls);
    setSessionStatus('HELD');
    setNote('');
  };

  const handleSaveLog = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedClass) return;

    setIsSubmitting(true);
    setTimeout(() => {
      setClasses((prev) =>
        prev.map((c) =>
          c.offeringId === selectedClass.offeringId ? { ...c, isLogged: true, completedSoFar: c.completedSoFar + 1 } : c
        )
      );
      setIsSubmitting(false);
      setSelectedClass(null);
      setSuccessMessage(`Session logged successfully for ${selectedClass.courseCode}! Syllabus progress updated.`);
      setTimeout(() => setSuccessMessage(null), 4000);
    }, 300);
  };

  return (
    <div className="space-y-6">
      {/* Header Banner */}
      <div className="bg-white rounded-lg border border-border p-6 shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 text-xs font-bold text-primary tracking-wide uppercase font-sans">
            <CalendarCheck className="w-4 h-4" />
            Faculty Academic Workspace — Today's Schedule
          </div>
          <h1 className="font-serif text-2xl font-bold text-slate-900 mt-1">
            {user?.name ? `Hello, ${user.name}` : 'Welcome, Faculty Member'}
          </h1>
          <p className="text-xs text-slate-600 mt-1">
            Log today's held or cancelled sessions in 20 seconds. Syllabus coverage and compliance are derived automatically.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <div className="px-3.5 py-1.5 rounded-md bg-slate-100 border border-slate-200 text-xs font-semibold text-slate-700">
            Term: <span className="text-primary font-bold">Odd 2026-27 (Week 11)</span>
          </div>
        </div>
      </div>

      {successMessage && (
        <div className="bg-emerald-50 border border-emerald-200 text-emerald-900 px-4 py-3 rounded-lg text-xs font-medium flex items-center gap-2">
          <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
          <span>{successMessage}</span>
        </div>
      )}

      {/* Main Grid: Classes Today vs Weekly Pacing Summary */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Classes Scheduled Today (2 cols) */}
        <div className="lg:col-span-2 space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-bold font-serif text-slate-900 uppercase tracking-wider flex items-center gap-2">
              <Clock className="w-4 h-4 text-primary" />
              Classes Scheduled For Today
            </h2>
            <span className="text-xs text-slate-500 font-medium">
              {classes.filter((c) => c.isLogged).length} of {classes.length} logged
            </span>
          </div>

          {loading ? (
            <div className="p-8 text-center bg-white rounded-lg border border-border text-xs text-slate-500">
              Loading today's teaching timetable...
            </div>
          ) : classes.length === 0 ? (
            <div className="p-8 text-center bg-white rounded-lg border border-border text-xs text-slate-500">
              No teaching sessions scheduled for today. Enjoy your research and prep time!
            </div>
          ) : (
            <div className="space-y-3.5">
              {classes.map((cls) => {
                const progressPct = Math.round((cls.completedSoFar / cls.totalPlanned) * 100);

                return (
                  <div
                    key={cls.offeringId}
                    className={`bg-white rounded-lg border transition-all p-5 shadow-xs ${
                      cls.isLogged
                        ? 'border-emerald-200 bg-emerald-50/20'
                        : 'border-border hover:border-primary/50'
                    }`}
                  >
                    <div className="flex items-start justify-between gap-4">
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-primary text-white">
                            {cls.courseCode}
                          </span>
                          <span className="text-xs font-semibold text-slate-700">{cls.section}</span>
                          <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-slate-100 text-slate-600 uppercase">
                            {cls.component}
                          </span>
                        </div>
                        <h3 className="font-serif text-lg font-bold text-slate-900 mt-1">{cls.courseName}</h3>
                        <div className="flex items-center gap-2 text-xs text-slate-500 mt-0.5">
                          <Clock className="w-3.5 h-3.5 text-slate-400" />
                          <span>{cls.timeSlot}</span>
                        </div>
                      </div>

                      {cls.isLogged ? (
                        <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-emerald-100 text-emerald-800 text-xs font-bold shrink-0">
                          <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                          Logged
                        </div>
                      ) : (
                        <button
                          type="button"
                          onClick={() => handleQuickLog(cls)}
                          className="flex items-center gap-1.5 px-4 py-2 rounded-md bg-primary text-white text-xs font-bold shadow-xs hover:bg-primary-hover transition-colors shrink-0"
                        >
                          <PlusCircle className="w-4 h-4" />
                          1-Tap Log Session
                        </button>
                      )}
                    </div>

                    {/* Pre-selected Next Syllabus Topic */}
                    <div className="mt-4 p-3 bg-slate-50 border border-slate-200/80 rounded-md">
                      <div className="text-[10px] font-bold text-slate-500 uppercase tracking-wider">
                        Next Uncovered Syllabus Topic (Pre-selected)
                      </div>
                      <div className="text-xs font-semibold text-slate-900 mt-0.5 flex items-start gap-1.5">
                        <span className="text-primary font-bold">›</span>
                        <span>{cls.nextTopic}</span>
                      </div>
                      <div className="text-[11px] text-slate-500 mt-0.5">{cls.unitTitle}</div>
                    </div>

                    {/* Syllabus Pacing Bar */}
                    <div className="mt-3 flex items-center justify-between text-xs text-slate-600">
                      <span>Syllabus Progress: {cls.completedSoFar} / {cls.totalPlanned} topics</span>
                      <span className="font-bold text-slate-900">{progressPct}%</span>
                    </div>
                    <div className="w-full h-1.5 bg-slate-100 rounded-full mt-1.5 overflow-hidden">
                      <div
                        className="h-full bg-primary rounded-full transition-all"
                        style={{ width: `${progressPct}%` }}
                      ></div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Weekly Targets & Deadlines Sidebar Widget */}
        <div className="space-y-4">
          <div className="bg-white rounded-lg border border-border p-5 shadow-xs space-y-4">
            <h3 className="font-serif text-sm font-bold text-slate-900 uppercase tracking-wider flex items-center gap-2">
              <Sparkles className="w-4 h-4 text-amber-500" />
              This Week's Target Pacing
            </h3>

            <div className="space-y-3">
              <div className="p-3 rounded-md bg-slate-50 border border-slate-200">
                <div className="flex items-center justify-between text-xs">
                  <span className="font-bold text-slate-900">CS501 (Sec A)</span>
                  <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-emerald-100 text-emerald-800">
                    On Track
                  </span>
                </div>
                <div className="text-[11px] text-slate-500 mt-1">Target: 3 lectures | Held: 3</div>
              </div>

              <div className="p-3 rounded-md bg-slate-50 border border-slate-200">
                <div className="flex items-center justify-between text-xs">
                  <span className="font-bold text-slate-900">CS502 (Sec A)</span>
                  <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-amber-100 text-amber-800">
                    Behind by 1 Topic
                  </span>
                </div>
                <div className="text-[11px] text-slate-500 mt-1">Target: 3 lectures | Held: 2</div>
              </div>
            </div>

            <button
              type="button"
              onClick={() => onNavigate && onNavigate('my-courses')}
              className="w-full py-2 text-xs font-bold text-primary hover:text-primary-hover flex items-center justify-center gap-1 border-t border-border pt-3"
            >
              View Full Course Syllabus <ChevronRight className="w-3.5 h-3.5" />
            </button>
          </div>

          <div className="bg-white rounded-lg border border-border p-5 shadow-xs space-y-3">
            <h3 className="font-serif text-sm font-bold text-slate-900 uppercase tracking-wider flex items-center gap-2">
              <AlertCircle className="w-4 h-4 text-primary" />
              Upcoming Academic Deadlines
            </h3>

            <div className="space-y-2.5 text-xs">
              <div className="p-2.5 rounded border border-border">
                <div className="font-bold text-slate-900">Mid-Sem Question Bank Submission</div>
                <div className="text-[11px] text-slate-500 mt-0.5">Due in 5 days (Oct 15)</div>
              </div>

              <div className="p-2.5 rounded border border-border">
                <div className="font-bold text-slate-900">Course File Audit - Unit 1-3</div>
                <div className="text-[11px] text-slate-500 mt-0.5">Due in 12 days (Oct 22)</div>
              </div>
            </div>

            <button
              type="button"
              onClick={() => onNavigate && onNavigate('deadlines')}
              className="w-full py-2 text-xs font-bold text-primary hover:text-primary-hover flex items-center justify-center gap-1 border-t border-border pt-3"
            >
              Go to Submissions Center <ChevronRight className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </div>

      {/* 20-Second Quick Log Modal */}
      {selectedClass && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4 backdrop-blur-2xs">
          <div className="bg-white rounded-lg border border-border shadow-xl max-w-lg w-full p-6 animate-in fade-in zoom-in-95 duration-150">
            <div className="flex items-center justify-between pb-3 border-b border-border">
              <div>
                <span className="text-xs font-bold text-primary uppercase tracking-wider">Quick Session Log</span>
                <h3 className="font-serif text-lg font-bold text-slate-900 mt-0.5">
                  {selectedClass.courseCode} — {selectedClass.courseName}
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setSelectedClass(null)}
                className="text-slate-400 hover:text-slate-700 text-sm font-bold"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleSaveLog} className="space-y-4 mt-4 text-xs">
              <div>
                <label className="block font-bold text-slate-700 mb-1.5">Session Status</label>
                <div className="grid grid-cols-3 gap-2">
                  {(['HELD', 'CANCELLED', 'SWAPPED'] as const).map((st) => (
                    <button
                      key={st}
                      type="button"
                      onClick={() => setSessionStatus(st)}
                      className={`py-2 px-3 rounded-md font-bold text-xs border text-center transition-all ${
                        sessionStatus === st
                          ? 'bg-primary text-white border-primary shadow-xs'
                          : 'bg-white text-slate-700 border-border hover:bg-slate-50'
                      }`}
                    >
                      {st}
                    </button>
                  ))}
                </div>
              </div>

              {sessionStatus === 'HELD' && (
                <div className="p-3 bg-slate-50 border border-slate-200 rounded-md">
                  <div className="text-[10px] font-bold text-slate-500 uppercase tracking-wider">Topic Covered</div>
                  <div className="font-bold text-slate-900 mt-1">{selectedClass.nextTopic}</div>
                  <div className="text-[11px] text-slate-500 mt-0.5">{selectedClass.unitTitle}</div>
                </div>
              )}

              {sessionStatus !== 'HELD' && (
                <div>
                  <label className="block font-bold text-slate-700 mb-1">Reason for {sessionStatus.toLowerCase()}</label>
                  <select className="w-full px-3 py-2 border border-border rounded-md text-xs bg-white">
                    <option>Institutional Holiday / Event</option>
                    <option>Faculty On Duty (OD) / Conference</option>
                    <option>Medical Leave</option>
                    <option>Rescheduled Timetable Swap</option>
                  </select>
                </div>
              )}

              <div>
                <label className="block font-bold text-slate-700 mb-1">Teaching Note (Optional)</label>
                <input
                  type="text"
                  placeholder="e.g. Completed slide deck and distributed homework set #3"
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  className="w-full px-3 py-2 border border-border rounded-md text-xs focus:ring-1 focus:ring-primary focus:border-primary outline-hidden"
                />
              </div>

              <div className="flex items-center justify-end gap-3 pt-3 border-t border-border">
                <button
                  type="button"
                  onClick={() => setSelectedClass(null)}
                  className="px-4 py-2 rounded-md border border-border text-slate-700 font-bold hover:bg-slate-50 transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="px-5 py-2 rounded-md bg-primary text-white font-bold hover:bg-primary-hover shadow-xs transition-colors"
                >
                  {isSubmitting ? 'Saving...' : 'Confirm & Save Log'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
