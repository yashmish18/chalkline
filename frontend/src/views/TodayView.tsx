import { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../context/AuthContext';
import { TodayClassDto, SyllabusTopicDto } from '../types/academics';
import { CourseDescriptionModal } from '../components/CourseDescriptionModal';
import {
  CalendarCheck,
  CheckCircle2,
  Clock,
  AlertCircle,
  Sparkles,
  ChevronLeft,
  ChevronRight,
  PlusCircle,
  RefreshCw,
  BookOpen,
  MapPin,
  Calendar,
  Trash2
} from 'lucide-react';

export function TodayView({ onNavigate }: { onNavigate?: (tab: string) => void }) {
  const { user } = useAuth();
  const [classes, setClasses] = useState<TodayClassDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const todayStr = new Date().toISOString().split('T')[0];

  // Modal states
  const [selectedClass, setSelectedClass] = useState<TodayClassDto | null>(null);
  const [sessionStatus, setSessionStatus] = useState<'HELD' | 'CANCELLED' | 'SWAPPED'>('HELD');
  const [selectedComponent, setSelectedComponent] = useState<string>('LECTURE');
  const [selectedSubSection, setSelectedSubSection] = useState<string>('ALL');
  const [selectedUnit, setSelectedUnit] = useState<number>(1);
  const [selectedTopicId, setSelectedTopicId] = useState<number | null>(null);
  const [customTopic, setCustomTopic] = useState<string>('');
  const [lecturesTaken, setLecturesTaken] = useState<number>(1);
  const [reasonNotHeld, setReasonNotHeld] = useState('Institutional Holiday / Event');
  const [note, setNote] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Description drawer modal
  const [courseForDescription, setCourseForDescription] = useState<TodayClassDto | null>(null);

  // Date picker state (defaults to today)
  const [selectedDate, setSelectedDate] = useState<string>(new Date().toISOString().split('T')[0]);

  const fetchClassesForDate = useCallback(async (dateStr: string) => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch(`/api/teaching-sessions/today?date=${dateStr}`, {
        credentials: 'include',
        headers: { 'Accept': 'application/json' }
      });
      if (!res.ok) {
        throw new Error(`Failed to load classes for ${dateStr} (${res.status})`);
      }
      const data: TodayClassDto[] = await res.json();
      setClasses(data);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to connect to timetable service';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, []);

  const fetchTodayClasses = useCallback(() => {
    fetchClassesForDate(selectedDate);
  }, [fetchClassesForDate, selectedDate]);

  useEffect(() => {
    fetchClassesForDate(selectedDate);
  }, [fetchClassesForDate, selectedDate]);

  const handleQuickLog = (cls: TodayClassDto) => {
    setSelectedClass(cls);
    setSessionStatus('HELD');
    setSelectedComponent(cls.component || 'LECTURE');
    setSelectedSubSection(cls.subSection || 'ALL');
    setSelectedUnit(cls.currentUnitNumber || 1);
    setSelectedTopicId(cls.nextTopicId);
    setCustomTopic(cls.nextTopic || '');
    setLecturesTaken(cls.component === 'PRACTICAL' || cls.component === 'LAB' ? 2 : 1);
    setReasonNotHeld('Institutional Holiday / Event');
    setNote('');
  };

  // When selectedUnit changes in the modal, adjust topic dropdown and custom topic
  const handleUnitChange = (unitNum: number) => {
    setSelectedUnit(unitNum);
    if (!selectedClass || !selectedClass.availableUnits) return;
    const unitObj = selectedClass.availableUnits.find((u) => u.unitNumber === unitNum);
    if (unitObj && unitObj.topics.length > 0) {
      setSelectedTopicId(unitObj.topics[0].id);
      setCustomTopic(unitObj.topics[0].topicName);
    } else {
      setSelectedTopicId(null);
      setCustomTopic(`Unit ${unitNum} Comprehensive Discussion`);
    }
  };

  const handleTopicDropdownSelect = (topicIdStr: string) => {
    if (!topicIdStr) {
      setSelectedTopicId(null);
      return;
    }
    const id = parseInt(topicIdStr, 10);
    setSelectedTopicId(id);
    if (selectedClass?.availableUnits) {
      for (const u of selectedClass.availableUnits) {
        const found = u.topics.find((t) => t.id === id);
        if (found) {
          setCustomTopic(found.topicName);
          break;
        }
      }
    }
  };

  const handleSaveLog = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedClass) return;

    setIsSubmitting(true);
    try {
      const payload = {
        offeringId: selectedClass.offeringId,
        sessionDate: selectedDate,
        sessionType: selectedComponent,
        status: sessionStatus,
        unitNumber: selectedUnit,
        topicIds: sessionStatus === 'HELD' && selectedTopicId ? [selectedTopicId] : [],
        customTopicName: customTopic.trim() || undefined,
        subSection: selectedSubSection,
        lecturesTaken: lecturesTaken,
        note: note.trim() || undefined,
        reasonNotHeld: sessionStatus !== 'HELD' ? reasonNotHeld : undefined
      };

      const res = await fetch('/api/teaching-sessions', {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json'
        },
        body: JSON.stringify(payload)
      });

      if (!res.ok) {
        const errorData = await res.json().catch(() => ({}));
        throw new Error(errorData.detail || errorData.title || `Error logging session (${res.status})`);
      }

      setSuccessMessage(`Session logged successfully for ${selectedClass.courseCode}! Syllabus coverage updated.`);
      setSelectedClass(null);
      await fetchTodayClasses();
      setTimeout(() => setSuccessMessage(null), 4000);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Error logging session';
      alert(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDeleteLog = async (cls: TodayClassDto) => {
    if (!window.confirm(`Are you sure you want to remove the session log for ${cls.courseCode} on ${selectedDate}?`)) {
      return;
    }
    try {
      const res = await fetch(`/api/teaching-sessions/offering/${cls.offeringId}`, {
        credentials: 'include',
        headers: { 'Accept': 'application/json' }
      });
      if (res.ok) {
        const sessions: Array<{ id: number; sessionDate: string }> = await res.json();
        const matching = sessions.find((s) => s.sessionDate === selectedDate);
        if (matching) {
          const delRes = await fetch(`/api/teaching-sessions/${matching.id}`, {
            method: 'DELETE',
            credentials: 'include'
          });
          if (delRes.ok) {
            setSuccessMessage(`Session log for ${cls.courseCode} removed.`);
            await fetchTodayClasses();
            setTimeout(() => setSuccessMessage(null), 3000);
            return;
          }
        }
      }
      alert('Could not find logged session for this date.');
    } catch {
      alert('Error deleting session log.');
    }
  };

  const getTopicsForCurrentModalUnit = (): SyllabusTopicDto[] => {
    if (!selectedClass || !selectedClass.availableUnits) return [];
    const unitObj = selectedClass.availableUnits.find((u) => u.unitNumber === selectedUnit);
    return unitObj ? unitObj.topics : [];
  };

  return (
    <div className="space-y-6">
      {/* Header Banner */}
      <div className="bg-white rounded-xl border border-slate-200/80 p-6 shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 text-xs font-bold text-primary tracking-wide uppercase font-sans">
            <CalendarCheck className="w-4 h-4" />
            Today&rsquo;s Teaching Log
          </div>
          <h1 className="text-xl font-bold text-slate-900 mt-1">
            {user?.name ? user.name : 'Faculty Member'}
          </h1>
          <p className="text-xs text-slate-500 mt-1">
            Record your conducted lectures and labs. Syllabus coverage and contact hours update in real-time.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={fetchTodayClasses}
            className="p-2 border border-slate-200 rounded-lg hover:bg-slate-50 text-slate-600 hover:text-slate-900 transition-colors"
            title="Refresh schedule"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin text-primary' : ''}`} />
          </button>
          {onNavigate && (
            <button
              type="button"
              onClick={() => onNavigate('timetable')}
              className="px-3 py-1.5 rounded-lg border border-primary text-primary hover:bg-primary/5 text-xs font-bold flex items-center gap-1.5 transition-colors"
            >
              <Calendar className="w-3.5 h-3.5" />
              Weekly Schedule
            </button>
          )}
          <div className="px-3 py-1.5 rounded-lg bg-slate-100 border border-slate-200 text-xs font-semibold text-slate-700">
            Term: <span className="text-primary font-bold">Semester 1 (2026-27)</span>
          </div>
        </div>
      </div>

      {/* Linear Precision Date Selector Bar */}
      <div className="bg-white rounded-lg border border-border p-3.5 shadow-xs flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2.5">
          <div className="flex items-center gap-1 bg-slate-50 border border-border px-1.5 py-1 rounded-md">
            <button
              type="button"
              onClick={() => {
                const parts = selectedDate.split('-');
                const d = new Date(parseInt(parts[0], 10), parseInt(parts[1], 10) - 1, parseInt(parts[2], 10));
                d.setDate(d.getDate() - 1);
                const yr = d.getFullYear();
                const mo = String(d.getMonth() + 1).padStart(2, '0');
                const dy = String(d.getDate()).padStart(2, '0');
                setSelectedDate(`${yr}-${mo}-${dy}`);
              }}
              className="p-1 rounded text-slate-500 hover:text-slate-800 hover:bg-white transition-colors"
              title="Previous day"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            <input
              type="date"
              value={selectedDate}
              onChange={(e) => e.target.value && setSelectedDate(e.target.value)}
              className="px-2 py-0.5 text-xs font-semibold text-slate-800 bg-transparent border-0 outline-hidden cursor-pointer"
            />
            <button
              type="button"
              onClick={() => {
                const parts = selectedDate.split('-');
                const d = new Date(parseInt(parts[0], 10), parseInt(parts[1], 10) - 1, parseInt(parts[2], 10));
                d.setDate(d.getDate() + 1);
                const yr = d.getFullYear();
                const mo = String(d.getMonth() + 1).padStart(2, '0');
                const dy = String(d.getDate()).padStart(2, '0');
                setSelectedDate(`${yr}-${mo}-${dy}`);
              }}
              className="p-1 rounded text-slate-500 hover:text-slate-800 hover:bg-white transition-colors"
              title="Next day"
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>

          {/* Quick-action pill buttons */}
          <div className="flex items-center gap-1.5 text-xs">
            <button
              type="button"
              onClick={() => setSelectedDate(todayStr)}
              className={`px-3 py-1.5 rounded-md font-semibold text-xs transition-colors ${
                selectedDate === todayStr
                  ? 'bg-primary text-white shadow-xs'
                  : 'bg-slate-100 hover:bg-slate-200 text-slate-700'
              }`}
            >
              Today
            </button>
            <button
              type="button"
              onClick={() => {
                const d = new Date();
                d.setDate(d.getDate() - 1);
                const yr = d.getFullYear();
                const mo = String(d.getMonth() + 1).padStart(2, '0');
                const dy = String(d.getDate()).padStart(2, '0');
                setSelectedDate(`${yr}-${mo}-${dy}`);
              }}
              className={`px-3 py-1.5 rounded-md font-semibold text-xs transition-colors ${
                selectedDate !== todayStr && new Date(`${selectedDate}T00:00:00`).getTime() === new Date(`${new Date(Date.now() - 86400000).toISOString().split('T')[0]}T00:00:00`).getTime()
                  ? 'bg-primary text-white shadow-xs'
                  : 'bg-slate-100 hover:bg-slate-200 text-slate-700'
              }`}
            >
              Yesterday
            </button>
          </div>
        </div>

        {/* Dynamic formatted day-of-week and sync status indicator */}
        <div className="flex items-center gap-2 text-xs">
          <span className="font-semibold text-slate-700">
            {new Date(`${selectedDate}T00:00:00`).toLocaleDateString('en-US', {
              weekday: 'long',
              month: 'short',
              day: 'numeric',
              year: 'numeric'
            })}
          </span>
          {selectedDate === todayStr ? (
            <span className="px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200 flex items-center gap-1.5">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
              Live Timetable
            </span>
          ) : selectedDate < todayStr ? (
            <span className="px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-amber-50 text-amber-800 border border-amber-200">
              Historical Log
            </span>
          ) : (
            <span className="px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-indigo-50 text-indigo-700 border border-indigo-200">
              Future Schedule
            </span>
          )}
        </div>
      </div>

      {successMessage && (
        <div className="bg-emerald-50 border border-emerald-200 text-emerald-900 px-4 py-3 rounded-lg text-xs font-medium flex items-center gap-2 animate-in fade-in">
          <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
          <span>{successMessage}</span>
        </div>
      )}

      {error && (
        <div className="bg-rose-50 border border-rose-200 text-rose-900 px-4 py-3 rounded-lg text-xs font-medium flex items-center justify-between">
          <div className="flex items-center gap-2">
            <AlertCircle className="w-4 h-4 text-rose-600 shrink-0" />
            <span>{error}</span>
          </div>
          <button
            type="button"
            onClick={fetchTodayClasses}
            className="font-bold underline text-rose-800 hover:text-rose-950 ml-4"
          >
            Retry
          </button>
        </div>
      )}

      {/* Main Grid: Classes Today vs Weekly Pacing Summary */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Classes Scheduled Today (2 cols) */}
        <div className="lg:col-span-2 space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-xs font-bold text-slate-700 uppercase tracking-wider flex items-center gap-2">
              <Clock className="w-4 h-4 text-primary" />
              Classes Scheduled For {selectedDate === todayStr ? 'Today' : new Date(`${selectedDate}T00:00:00`).toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' })}
            </h2>
            <span className="text-xs text-slate-500 font-medium">
              {classes.filter((c) => c.isLogged).length} of {classes.length} logged
            </span>
          </div>

          {loading && classes.length === 0 ? (
            <div className="p-8 text-center bg-white rounded-xl border border-slate-200 text-xs text-slate-500 animate-pulse">
              Loading timetable for {selectedDate === todayStr ? 'today' : selectedDate}...
            </div>
          ) : classes.length === 0 ? (
            <div className="p-8 text-center bg-white rounded-xl border border-slate-200 text-xs text-slate-500">
              No teaching allocations found for your account in the active term.
            </div>
          ) : (
            <div className="space-y-4">
              {classes.map((cls) => {
                const progressPct = cls.totalPlanned > 0
                  ? Math.round((cls.completedSoFar / cls.totalPlanned) * 100)
                  : 0;

                return (
                  <div
                    key={cls.offeringId}
                    className={`bg-white rounded-xl border transition-all p-5 shadow-xs ${
                      cls.isLogged
                        ? 'border-emerald-200 bg-emerald-50/10'
                        : 'border-slate-200/80 hover:border-primary/50'
                    }`}
                  >
                    <div className="flex items-start justify-between gap-4">
                      <div className="space-y-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-primary text-white">
                            {cls.courseCode}
                          </span>
                          <span className="text-xs font-semibold text-slate-700">{cls.section}</span>
                          {cls.subSection && cls.subSection !== 'ALL' && (
                            <span className="text-[11px] font-bold px-2 py-0.5 rounded bg-blue-100 text-blue-800 border border-blue-200">
                              Batch {cls.subSection}
                            </span>
                          )}
                          <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-slate-100 text-slate-600 uppercase">
                            {cls.component}
                          </span>
                          {cls.ltp && (
                            <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-slate-100 text-slate-600">
                              LTP: {cls.ltp}
                            </span>
                          )}
                        </div>

                        <h3 className="text-base font-bold text-slate-900 mt-1">{cls.courseName}</h3>

                        <div className="flex flex-wrap items-center gap-3 text-xs text-slate-500 mt-1">
                          <div className="flex items-center gap-1.5">
                            <Clock className="w-3.5 h-3.5 text-slate-400" />
                            <span>{cls.timeSlot}</span>
                          </div>
                          {cls.room && (
                            <div className="flex items-center gap-1.5">
                              <MapPin className="w-3.5 h-3.5 text-slate-400" />
                              <span>{cls.room}</span>
                            </div>
                          )}
                          <button
                            type="button"
                            onClick={() => setCourseForDescription(cls)}
                            className="text-primary hover:underline font-medium flex items-center gap-1"
                          >
                            <BookOpen className="w-3 h-3" /> Course Details
                          </button>
                        </div>
                      </div>

                      {cls.isLogged ? (
                        <div className="flex items-center gap-2">
                          <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-emerald-100/80 text-emerald-800 text-xs font-semibold shrink-0">
                            <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                            Logged for {selectedDate === todayStr ? 'Today' : selectedDate}
                          </div>
                          <button
                            type="button"
                            onClick={() => handleDeleteLog(cls)}
                            className="p-1.5 rounded-lg text-slate-400 hover:text-rose-600 hover:bg-rose-50 transition-colors"
                            title="Remove session log"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      ) : (
                        <button
                          type="button"
                          onClick={() => handleQuickLog(cls)}
                          className="flex items-center gap-1.5 px-4 py-2 rounded-lg bg-primary text-white text-xs font-bold shadow-xs hover:bg-primary-hover transition-colors shrink-0"
                        >
                          <PlusCircle className="w-4 h-4" />
                          1-Tap Log Session
                        </button>
                      )}
                    </div>

                    {/* Pre-selected / Running Syllabus Topic Banner */}
                    <div className="mt-4 p-3 bg-slate-50 border border-slate-200/80 rounded-md">
                      <div className="flex items-center justify-between">
                        <div className="text-[10px] font-bold text-slate-500 uppercase tracking-wider">
                          Current Running Topic &bull; Unit {cls.currentUnitNumber || 1}
                        </div>
                        <span className="text-[10px] font-semibold text-slate-500">{cls.unitTitle}</span>
                      </div>
                      <div className="text-xs font-semibold text-slate-900 mt-1 flex items-start gap-1.5">
                        <span className="text-primary font-bold">&rsaquo;</span>
                        <span>{cls.nextTopic}</span>
                      </div>
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
            <h3 className="font-sans text-sm font-bold text-slate-900 uppercase tracking-wider flex items-center gap-2">
              <Sparkles className="w-4 h-4 text-amber-500" />
              This Week&rsquo;s Target Pacing
            </h3>

            <div className="space-y-3">
              {classes.map((cls) => {
                const targetTopicsThisWeek = Math.min(cls.totalPlanned, Math.max(1, Math.round(cls.totalPlanned * (11 / 14))));
                const isBehind = cls.completedSoFar < targetTopicsThisWeek;
                const delta = targetTopicsThisWeek - cls.completedSoFar;

                return (
                  <div key={cls.offeringId} className="p-3 rounded-md bg-slate-50 border border-slate-200">
                    <div className="flex items-center justify-between text-xs">
                      <span className="font-bold text-slate-900">
                        {cls.courseCode} ({cls.section}
                        {cls.subSection && cls.subSection !== 'ALL' ? ` · ${cls.subSection}` : ''})
                      </span>
                      <span
                        className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${
                          isBehind
                            ? 'bg-amber-100 text-amber-800'
                            : 'bg-emerald-100 text-emerald-800'
                        }`}
                      >
                        {isBehind ? `Behind by ${delta} topic${delta > 1 ? 's' : ''}` : 'On Track'}
                      </span>
                    </div>
                    <div className="text-[11px] text-slate-500 mt-1">
                      Expected by Wk 11: {targetTopicsThisWeek} topics | Covered: {cls.completedSoFar}
                    </div>
                  </div>
                );
              })}
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
            <h3 className="font-sans text-sm font-bold text-slate-900 uppercase tracking-wider flex items-center gap-2">
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

      {/* Course Description Slide-Over Modal */}
      {courseForDescription && (
        <CourseDescriptionModal
          course={courseForDescription}
          onClose={() => setCourseForDescription(null)}
        />
      )}

      {/* Unlocked, Highly-Usable Quick Session Log Modal */}
      {selectedClass && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-2xs animate-in fade-in">
          <div className="bg-white rounded-lg border border-border shadow-2xl max-w-lg w-full p-6 animate-in zoom-in-95 duration-150 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between pb-3 border-b border-border">
              <div>
                <span className="text-xs font-bold text-primary uppercase tracking-wider">Quick Session Log</span>
                <h3 className="font-sans text-lg font-bold text-slate-900 mt-0.5">
                  {selectedClass.courseCode} &mdash; {selectedClass.courseName}
                </h3>
                <div className="text-[11px] text-slate-500">
                  {selectedClass.section} &bull; {selectedClass.timeSlot} &bull; Date: <span className="font-semibold text-slate-700">{selectedDate}</span>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setSelectedClass(null)}
                className="text-slate-400 hover:text-slate-700 text-sm font-bold p-1 rounded-md"
              >
                &times;
              </button>
            </div>

            <form onSubmit={handleSaveLog} className="space-y-4 mt-4 text-xs">
              {/* 1. Session Status */}
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
                <>
                  {/* 2. LTP Component & Sub-section Selector */}
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                    <div>
                      <label className="block font-bold text-slate-700 mb-1 flex items-center justify-between">
                        <span>LTP Component</span>
                        <span className="text-[10px] text-slate-400 font-normal">1-click override</span>
                      </label>
                      <div className="grid grid-cols-3 gap-1.5 p-1 bg-slate-100 rounded-md border border-slate-200">
                        {[
                          { id: 'LECTURE', label: 'Lecture (L)', defaultHours: 1 },
                          { id: 'TUTORIAL', label: 'Tutorial (T)', defaultHours: 1 },
                          { id: 'PRACTICAL', label: 'Practical (P)', defaultHours: 2 }
                        ].map((comp) => (
                          <button
                            key={comp.id}
                            type="button"
                            onClick={() => {
                              setSelectedComponent(comp.id);
                              setLecturesTaken(comp.defaultHours);
                            }}
                            className={`py-1.5 px-2 rounded text-[11px] font-bold transition-all text-center ${
                              selectedComponent === comp.id
                                ? 'bg-primary text-white shadow-xs'
                                : 'text-slate-600 hover:text-slate-900 hover:bg-white/60'
                            }`}
                          >
                            {comp.label}
                          </button>
                        ))}
                      </div>
                    </div>

                    <div>
                      <label className="block font-bold text-slate-700 mb-1 flex items-center justify-between">
                        <span>Section / Batch</span>
                        <span className="text-[10px] text-slate-400 font-normal">All sections supported</span>
                      </label>
                      {(() => {
                        const rawSec = selectedClass.section || 'A';
                        const cleanSec = rawSec.replace(/^(sec|section|div|division)\s*/i, '').trim().toUpperCase() || 'A';
                        const otherSecs = ['A', 'B', 'C'].filter((s) => s !== cleanSec);
                        return (
                          <select
                            value={selectedSubSection}
                            onChange={(e) => setSelectedSubSection(e.target.value)}
                            className="w-full px-3 py-2 border border-border rounded-md text-xs bg-white focus:ring-1 focus:ring-primary focus:border-primary outline-hidden font-medium"
                          >
                            <optgroup label={`Section ${cleanSec} (Assigned)`}>
                              <option value="ALL">Whole Section {cleanSec} (Theory / Full Class)</option>
                              <option value={`${cleanSec}1`}>Batch {cleanSec}1 (Lab / Tutorial)</option>
                              <option value={`${cleanSec}2`}>Batch {cleanSec}2 (Lab / Tutorial)</option>
                              <option value={`${cleanSec}3`}>Batch {cleanSec}3 (Lab / Tutorial)</option>
                            </optgroup>
                            {otherSecs.map((s) => (
                              <optgroup key={s} label={`Section ${s}`}>
                                <option value={`SEC_${s}_ALL`}>Whole Section {s}</option>
                                <option value={`${s}1`}>Batch {s}1</option>
                                <option value={`${s}2`}>Batch {s}2</option>
                              </optgroup>
                            ))}
                            <optgroup label="Other">
                              <option value="COMBINED">Combined (All Sections / Elective)</option>
                            </optgroup>
                          </select>
                        );
                      })()}
                    </div>
                  </div>

                  {/* 3. Academic Unit Running Dropdown */}
                  <div>
                    <label className="block font-bold text-slate-700 mb-1 flex items-center justify-between">
                      <span>Academic Unit Running</span>
                      <span className="text-[10px] text-primary font-normal">
                        Select unit currently taught
                      </span>
                    </label>
                    <select
                      value={selectedUnit}
                      onChange={(e) => handleUnitChange(parseInt(e.target.value, 10))}
                      className="w-full px-3 py-2 border border-border rounded-md text-xs bg-white font-medium focus:ring-1 focus:ring-primary focus:border-primary outline-hidden"
                    >
                      {selectedClass.availableUnits && selectedClass.availableUnits.length > 0 ? (
                        selectedClass.availableUnits.map((u) => (
                          <option key={u.unitNumber} value={u.unitNumber}>
                            {u.unitTitle} ({u.topics.length} topics)
                          </option>
                        ))
                      ) : (
                        [1, 2, 3, 4, 5].map((num) => (
                          <option key={num} value={num}>
                            Unit {num}
                          </option>
                        ))
                      )}
                    </select>
                  </div>

                  {/* 4. Topic Selection from Syllabus */}
                  <div>
                    <label className="block font-bold text-slate-700 mb-1">
                      Select Topic from Unit {selectedUnit} Syllabus
                    </label>
                    <select
                      value={selectedTopicId ? String(selectedTopicId) : ''}
                      onChange={(e) => handleTopicDropdownSelect(e.target.value)}
                      className="w-full px-3 py-2 border border-border rounded-md text-xs bg-white focus:ring-1 focus:ring-primary focus:border-primary outline-hidden"
                    >
                      <option value="">-- Choose from Unit {selectedUnit} Topics --</option>
                      {getTopicsForCurrentModalUnit().map((t) => (
                        <option key={t.id} value={t.id}>
                          {t.topicName} (Week {t.targetWeek})
                        </option>
                      ))}
                    </select>
                  </div>

                  {/* 5. Flexible Editable / Custom Topic Input */}
                  <div>
                    <label className="block font-bold text-slate-700 mb-1 flex items-center justify-between">
                      <span>Specific Topic / Lab Taught (Editable)</span>
                      <span className="text-[10px] text-slate-400 font-normal">
                        Free text or edit above selection
                      </span>
                    </label>
                    <input
                      type="text"
                      required
                      placeholder="e.g. TCP Congestion Control: AIMD, Slow Start & Fast Recovery"
                      value={customTopic}
                      onChange={(e) => setCustomTopic(e.target.value)}
                      className="w-full px-3 py-2 border border-border rounded-md text-xs focus:ring-1 focus:ring-primary focus:border-primary outline-hidden font-medium"
                    />
                  </div>

                  {/* 6. Lectures Taken / Duration */}
                  <div>
                    <label className="block font-bold text-slate-700 mb-1">
                      Lectures / Contact Hours Conducted
                    </label>
                    <select
                      value={lecturesTaken}
                      onChange={(e) => setLecturesTaken(parseInt(e.target.value, 10))}
                      className="w-full px-3 py-2 border border-border rounded-md text-xs bg-white focus:ring-1 focus:ring-primary focus:border-primary outline-hidden"
                    >
                      <option value={1}>1 Lecture (1 Contact Hour)</option>
                      <option value={2}>2 Lectures (2-Hour Lab / Block Period)</option>
                      <option value={3}>3 Lectures (Intensive Workshop)</option>
                    </select>
                  </div>
                </>
              )}

              {sessionStatus !== 'HELD' && (
                <div>
                  <label className="block font-bold text-slate-700 mb-1">
                    Reason for {sessionStatus.toLowerCase()}
                  </label>
                  <select
                    value={reasonNotHeld}
                    onChange={(e) => setReasonNotHeld(e.target.value)}
                    className="w-full px-3 py-2 border border-border rounded-md text-xs bg-white focus:ring-1 focus:ring-primary focus:border-primary outline-hidden"
                  >
                    <option value="Institutional Holiday / Event">Institutional Holiday / Event</option>
                    <option value="Faculty On Duty (OD) / Conference">Faculty On Duty (OD) / Conference</option>
                    <option value="Medical Leave">Medical Leave</option>
                    <option value="Rescheduled Timetable Swap">Rescheduled Timetable Swap</option>
                  </select>
                </div>
              )}

              {/* Teaching Note / Homework */}
              <div>
                <label className="block font-bold text-slate-700 mb-1">
                  Teaching Notes &amp; Observations (Optional)
                </label>
                <input
                  type="text"
                  placeholder="e.g. Distributed assignment set #3 and discussed Wireshark packet traces"
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  className="w-full px-3 py-2 border border-border rounded-md text-xs focus:ring-1 focus:ring-primary focus:border-primary outline-hidden"
                />
              </div>

              {/* Action Buttons */}
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
