import { useState, useEffect, useCallback, useMemo } from 'react';
import { TodayClassDto, SyllabusTopicDto, CourseProgressDto } from '../types/academics';
import { FacultyProfileDto } from '../types/directory';
import { DeadlineAssignmentDto } from '../types/deadlines';
import { CourseDescriptionModal } from '../components/CourseDescriptionModal';
import {
  CheckCircle2,
  Check,
  AlertCircle,
  ChevronLeft,
  ChevronRight,
  RefreshCw,
} from 'lucide-react';
import { sectionLabel, componentLabel, batchLabel, localDateString } from '../lib/labels';

export function TodayView({ onNavigate }: { onNavigate?: (tab: string) => void }) {
  const [classes, setClasses] = useState<TodayClassDto[]>([]);
  const [facultyProfile, setFacultyProfile] = useState<FacultyProfileDto | null>(null);
  const [deadlines, setDeadlines] = useState<DeadlineAssignmentDto[]>([]);
  const [allCourses, setAllCourses] = useState<CourseProgressDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const todayStr = localDateString();

  // Modal states for Quick Session Log
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
  const [selectedDate, setSelectedDate] = useState<string>(todayStr);

  // Classes can only be logged from the semester start date onwards.
  const [termStart, setTermStart] = useState<string | null>(null);
  const [termError, setTermError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await fetch('/api/terms/active', { credentials: 'include' });
        if (res.ok) {
          const term = await res.json();
          if (!cancelled) setTermStart(term?.startDate ?? null);
        }
      } catch {
        // Term dates are advisory here; the server enforces the rule anyway.
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const fetchProfileDeadlinesAndCourses = useCallback(async () => {
    try {
      const [profRes, deadRes, coursesRes] = await Promise.all([
        fetch('/api/faculty/me/profile', { credentials: 'include' }),
        fetch('/api/my-deadlines', { credentials: 'include' }),
        fetch('/api/academics/my-courses', { credentials: 'include' }),
      ]);
      if (profRes.ok) {
        setFacultyProfile(await profRes.json());
      }
      if (deadRes.ok) {
        setDeadlines(await deadRes.json());
      }
      if (coursesRes.ok) {
        setAllCourses(await coursesRes.json());
      }
    } catch {
      // non-critical telemetry
    }
  }, []);

  const fetchClassesForDate = useCallback(async (dateStr: string) => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch(`/api/teaching-sessions/today?date=${dateStr}`, {
        credentials: 'include',
        headers: { Accept: 'application/json' },
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
    fetchProfileDeadlinesAndCourses();
  }, [fetchClassesForDate, fetchProfileDeadlinesAndCourses, selectedDate]);

  useEffect(() => {
    fetchClassesForDate(selectedDate);
    fetchProfileDeadlinesAndCourses();
  }, [fetchClassesForDate, fetchProfileDeadlinesAndCourses, selectedDate]);

  const [isQuickSubmitting, setIsQuickSubmitting] = useState<number | null>(null);
  const [logAllGroup, setLogAllGroup] = useState<number | null>(null);

  /** One-tap log: marks the class held with its next planned topic. Shared by single and bulk log. */
  const postHeld = async (cls: TodayClassDto) => {
    const payload = {
      offeringId: cls.offeringId,
      sessionDate: selectedDate,
      timeSlot: cls.timeSlot,
      room: cls.room,
      sessionType: cls.component || 'LECTURE',
      subSection: cls.subSection || 'ALL',
      status: 'HELD',
      unitNumber: cls.currentUnitNumber,
      topicIds: cls.nextTopicId ? [cls.nextTopicId] : [],
      customTopicName: cls.nextTopicId ? null : 'Scheduled session',
      lecturesTaken: cls.component === 'PRACTICAL' || cls.component === 'LAB' ? 2 : 1,
      reasonNotHeld: null,
      note: null,
    };
    const res = await fetch('/api/teaching-sessions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify(payload),
    });
    if (!res.ok) {
      const errorData = await res.json().catch(() => null);
      throw new Error(errorData?.detail || `Failed to log session (${res.status})`);
    }
  };

  const handleOneClickConfirm = async (cls: TodayClassDto) => {
    setIsQuickSubmitting(cls.offeringId);
    setSuccessMessage(null);
    setError(null);
    try {
      await postHeld(cls);
      setSuccessMessage(`Recorded ${cls.courseCode} as held${cls.nextTopicId ? ` — ${cls.nextTopic}` : ''}.`);
      fetchClassesForDate(selectedDate);
      fetchProfileDeadlinesAndCourses();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Could not log class');
    } finally {
      setIsQuickSubmitting(null);
    }
  };

  /** "Log all held": records every still-unlogged component of a section in one action. */
  const handleLogAllHeld = async (group: TodayClassDto[]) => {
    const pending = group.filter((c) => !c.isLogged);
    if (pending.length === 0) return;
    setLogAllGroup(group[0].offeringId);
    setSuccessMessage(null);
    setError(null);
    try {
      for (const cls of pending) {
        await postHeld(cls);
      }
      setSuccessMessage(
        `Recorded ${pending.length} class${pending.length > 1 ? 'es' : ''} for ${group[0].courseCode}.`
      );
      fetchClassesForDate(selectedDate);
      fetchProfileDeadlinesAndCourses();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Could not log the classes');
    } finally {
      setLogAllGroup(null);
    }
  };

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

  const handleTopicSelection = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const val = e.target.value;
    if (val === 'CUSTOM') {
      setSelectedTopicId(null);
      setCustomTopic('');
    } else {
      const topicId = parseInt(val, 10);
      setSelectedTopicId(topicId);
      const allTopics = getTopicsForCurrentModalUnit();
      const match = allTopics.find((t) => t.id === topicId);
      if (match) {
        setCustomTopic(match.topicName);
      }
    }
  };

  const handleSaveLog = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedClass) return;

    setIsSubmitting(true);
    setSuccessMessage(null);

    const payload = {
      offeringId: selectedClass.offeringId,
      sessionDate: selectedDate,
      timeSlot: selectedClass.timeSlot,
      room: selectedClass.room,
      sessionType: selectedComponent,
      subSection: selectedSubSection,
      status: sessionStatus,
      unitNumber: sessionStatus === 'HELD' ? selectedUnit : null,
      topicIds: sessionStatus === 'HELD' && selectedTopicId ? [selectedTopicId] : [],
      customTopicName: sessionStatus === 'HELD' && !selectedTopicId && customTopic.trim() ? customTopic.trim() : null,
      lecturesTaken: sessionStatus === 'HELD' ? lecturesTaken : 0,
      reasonNotHeld: sessionStatus !== 'HELD' ? reasonNotHeld : null,
      note: note.trim().length > 0 ? note.trim() : null,
    };

    try {
      const res = await fetch('/api/teaching-sessions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify(payload),
      });

      if (!res.ok) {
        const errorData = await res.json().catch(() => null);
        throw new Error(errorData?.detail || `Failed to log session (${res.status})`);
      }

      setSuccessMessage(
        `Session successfully recorded for ${selectedClass.courseCode} (${sessionStatus})`
      );
      setSelectedClass(null);
      fetchClassesForDate(selectedDate);
      fetchProfileDeadlinesAndCourses();
      setTimeout(() => setSuccessMessage(null), 5000);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Error recording teaching session';
      alert(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  const getTopicsForCurrentModalUnit = (): SyllabusTopicDto[] => {
    if (!selectedClass || !selectedClass.availableUnits) return [];
    const unitObj = selectedClass.availableUnits.find((u) => u.unitNumber === selectedUnit);
    return unitObj ? unitObj.topics : [];
  };

  const totalTopicsOverall = useMemo(
    () => allCourses.reduce((acc, c) => acc + (c.totalTopics || 0), 0),
    [allCourses]
  );
  const coveredTopicsOverall = useMemo(
    () => allCourses.reduce((acc, c) => acc + (c.coveredTopics || 0), 0),
    [allCourses]
  );
  const overallCoveragePct = useMemo(
    () => (totalTopicsOverall > 0 ? Math.round((coveredTopicsOverall / totalTopicsOverall) * 100) : 0),
    [coveredTopicsOverall, totalTopicsOverall]
  );

  // Workload distribution breakdown
  const lectureHrs = facultyProfile?.load?.lectureHours ?? 0;
  const tutorialHrs = facultyProfile?.load?.tutorialHours ?? 0;
  const practicalHrs = facultyProfile?.load?.practicalHours ?? 0;
  const pblHrs = facultyProfile?.load?.pblHours ?? 0;
  const totalLoadHrs = facultyProfile?.load?.totalHours ?? (lectureHrs + tutorialHrs + practicalHrs + pblHrs);

  // Today's classes log status
  const todayLoggedCount = classes.filter((c) => c.isLogged).length;
  const todayTotalCount = classes.length;

  // Group the day by course + section: one card per section, its L/T/P components listed inside.
  const groupedClasses = useMemo(() => {
    const rank = (component: string | null | undefined) => {
      const c = (component ?? '').toUpperCase();
      if (c === 'ALL' || c.startsWith('LEC') || c === 'THEORY') return 0;
      if (c.startsWith('TUT') || c === 'T') return 1;
      if (c.startsWith('PRA') || c.startsWith('LAB') || c === 'P') return 2;
      return 3;
    };
    const groups = new Map<number, TodayClassDto[]>();
    for (const cls of classes) {
      groups.set(cls.offeringId, [...(groups.get(cls.offeringId) ?? []), cls]);
    }
    return [...groups.values()].map((items) => ({
      head: items[0],
      items: [...items].sort((a, b) => rank(a.component) - rank(b.component)),
    }));
  }, [classes]);

  const formatDate = (date: Date) =>
    `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

  const shiftDate = (days: number) => {
    const [y, m, d] = selectedDate.split('-').map(Number);
    const next = formatDate(new Date(y, m - 1, d + days));
    if (termStart && next < termStart) return;
    setSelectedDate(next);
  };

  const isBeforeTermStart = termStart !== null && selectedDate < termStart;
  const minSelectableDate = termStart && termStart > todayStr ? termStart : undefined;
  const dateHeading = new Date(`${selectedDate}T00:00:00`).toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' });
  const hasTimetable = classes.some((c) => c.scheduled !== false);
  const pendingDeadlines = deadlines.filter((d) => !d.isOverdue).slice(0, 4);
  const overdueDeadlines = deadlines.filter((d) => d.isOverdue);

  return (
    <div className="space-y-10 max-w-5xl">
      {/* Date */}
      <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4">
        <div>
          <p className="text-[13px] text-[var(--ink-subtle)]">{selectedDate === todayStr ? 'Today' : 'Viewing'}</p>
          <h2 className="font-display text-[34px] leading-tight font-medium">{dateHeading}</h2>
        </div>
        <div className="flex items-center gap-1.5">
          <button type="button" onClick={() => shiftDate(-1)} className="btn w-8 px-0" aria-label="Previous day">
            <ChevronLeft className="w-4 h-4" />
          </button>
          <input
            type="date"
            value={selectedDate}
            min={minSelectableDate}
            onChange={(e) => {
              if (!e.target.value) return;
              if (termStart && e.target.value < termStart) {
                setTermError(`The semester starts on ${termStart} — classes cannot be logged before that.`);
                return;
              }
              setTermError(null);
              setSelectedDate(e.target.value);
            }}
            className="field h-8 w-auto text-[13px]"
            aria-label="Date"
          />
          <button type="button" onClick={() => shiftDate(1)} className="btn w-8 px-0" aria-label="Next day">
            <ChevronRight className="w-4 h-4" />
          </button>
          {selectedDate !== todayStr && (
            <button type="button" onClick={() => setSelectedDate(todayStr)} className="btn">Today</button>
          )}
          <button type="button" onClick={fetchTodayClasses} className="btn w-8 px-0" aria-label="Refresh">
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {successMessage && (
        <div role="status" className="flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-2.5 text-[13px] text-emerald-800">
          <CheckCircle2 className="w-4 h-4" /> {successMessage}
        </div>
      )}
      {error && (
        <div role="alert" className="flex items-center justify-between rounded-lg border border-rose-200 bg-rose-50 px-4 py-2.5 text-[13px] text-rose-800">
          <span className="inline-flex items-center gap-2"><AlertCircle className="w-4 h-4" /> {error}</span>
          <button type="button" onClick={fetchTodayClasses} className="font-medium underline">Retry</button>
        </div>
      )}
      {(termError || isBeforeTermStart) && (
        <div role="alert" className="flex items-center gap-2 rounded-lg border border-amber-200 bg-amber-50 px-4 py-2.5 text-[13px] text-amber-900">
          <AlertCircle className="w-4 h-4" />
          {termError ?? `The semester starts on ${termStart} — classes cannot be logged before that.`}
        </div>
      )}

      {/* Three plain figures */}
      <dl className="grid grid-cols-3 gap-8 border-y border-[var(--hairline)] py-6">
        <div>
          <dt className="text-[13px] text-[var(--ink-subtle)]">Logged {selectedDate === todayStr ? 'today' : 'this day'}</dt>
          <dd className="mt-1.5 font-display text-[32px] leading-none num">
            {todayLoggedCount}<span className="text-[var(--ink-tertiary)] text-[22px]"> / {todayTotalCount}</span>
          </dd>
        </div>
        <div>
          <dt className="text-[13px] text-[var(--ink-subtle)]">Syllabus covered</dt>
          <dd className="mt-1.5 font-display text-[32px] leading-none num">
            {totalTopicsOverall > 0 ? `${overallCoveragePct}%` : '—'}
          </dd>
        </div>
        <div>
          <dt className="text-[13px] text-[var(--ink-subtle)]">Weekly load</dt>
          <dd className="mt-1.5 font-display text-[32px] leading-none num">
            {totalLoadHrs}h
            {pblHrs > 0 && <span className="ml-2 text-[14px] font-sans text-[var(--ink-subtle)]">incl. {pblHrs} PBL</span>}
          </dd>
        </div>
      </dl>

      {/* Classes */}
      <section>
        <div className="mb-3 flex items-end justify-between">
          <h3 className="font-display text-[22px] font-medium">Classes</h3>
          {onNavigate && (
            <button type="button" onClick={() => onNavigate('timetable')} className="text-[13px] text-[var(--accent)] hover:underline">
              Week view
            </button>
          )}
        </div>

        {!hasTimetable && classes.length > 0 && (
          <div className="mb-3 flex flex-col sm:flex-row sm:items-center justify-between gap-3 rounded-lg bg-[var(--surface-3)] px-4 py-3 text-[13px] text-[var(--ink-muted)]">
            <span>Your timetable isn’t uploaded yet, so every course you teach is listed. Upload it once and this list shows only each day’s classes.</span>
            {onNavigate && (
              <button type="button" onClick={() => onNavigate('timetable')} className="btn shrink-0">Upload timetable</button>
            )}
          </div>
        )}

        {loading && classes.length === 0 ? (
          <p className="card px-5 py-10 text-center text-[14px] text-[var(--ink-subtle)]">Loading…</p>
        ) : classes.length === 0 ? (
          <p className="card px-5 py-10 text-center text-[14px] text-[var(--ink-subtle)]">No classes on this day.</p>
        ) : (
          <div className="space-y-3">
            {groupedClasses.map(({ head, items }) => {
              const logged = items.filter((c) => c.isLogged).length;
              const pending = items.length - logged;
              return (
                <div key={head.offeringId} className="card overflow-hidden">
                  <div className="flex flex-wrap items-center justify-between gap-3 px-5 py-3.5 border-b border-[var(--hairline-soft)]">
                    <div className="min-w-0">
                      <button type="button" onClick={() => setCourseForDescription(head)} className="text-left">
                        <span className="font-mono text-[13px]">{head.courseCode}</span>
                        <span className="ml-2 text-[15px] font-medium hover:underline">{head.courseName}</span>
                      </button>
                      <div className="mt-0.5 text-[13px] text-[var(--ink-subtle)]">
                        {[sectionLabel(head.section), head.ltp ? `L-T-P ${head.ltp}` : null].filter(Boolean).join(' · ')}
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <span className={`chip ${pending === 0 ? 'chip-ok' : 'chip-warn'}`}>
                        {logged}/{items.length} logged
                      </span>
                      {pending > 0 && (
                        <button
                          type="button"
                          onClick={() => handleLogAllHeld(items)}
                          disabled={logAllGroup === head.offeringId || isBeforeTermStart}
                          className="btn btn-primary h-8 px-3 text-[12px] flex items-center gap-1.5"
                          title="Record every remaining component of this section as held"
                        >
                          <Check className="w-3.5 h-3.5" />
                          {logAllGroup === head.offeringId ? 'Saving…' : `Log all held (${pending})`}
                        </button>
                      )}
                    </div>
                  </div>
                  <ul className="divide-y divide-[var(--hairline-soft)]">
                    {items.map((cls, i) => (
                      <li key={`${cls.component}-${cls.subSection}-${i}`} className="px-5 py-3 grid grid-cols-[120px_minmax(0,1fr)_auto] gap-5 items-center">
                        <div className="text-[13px] num">
                          {cls.scheduled !== false ? (
                            <>
                              <div className="font-medium">{cls.timeSlot.split(' – ')[0]}</div>
                              <div className="text-[var(--ink-tertiary)]">{cls.room}</div>
                            </>
                          ) : (
                            <span className="text-[var(--ink-tertiary)]">Unscheduled</span>
                          )}
                        </div>
                        <div className="min-w-0">
                          <div className="flex items-center gap-2 text-[14px]">
                            <span className="font-medium">{componentLabel(cls.component)}</span>
                            {cls.subSection && cls.subSection !== 'ALL' && (
                              <span className="chip">{batchLabel(cls.subSection)}</span>
                            )}
                          </div>
                          <div className="mt-0.5 text-[13px] text-[var(--ink-subtle)]">
                            {[cls.totalPlanned > 0 ? `${cls.completedSoFar}/${cls.totalPlanned} topics` : null,
                              cls.nextTopic && cls.nextTopic !== 'All topics covered' ? `Next: ${cls.nextTopic}` : null]
                              .filter(Boolean).join(' · ')}
                          </div>
                        </div>
                        {cls.isLogged ? (
                          <span className="chip chip-ok"><CheckCircle2 className="w-3 h-3" /> Logged</span>
                        ) : (
                          <div className="flex items-center gap-2">
                            <button
                              type="button"
                              onClick={() => handleOneClickConfirm(cls)}
                              disabled={isQuickSubmitting === cls.offeringId || isBeforeTermStart}
                              className="btn btn-primary h-8 px-3 text-[12px] flex items-center gap-1.5"
                              title="1-click log: marks class held with next planned topic"
                            >
                              <Check className="w-3.5 h-3.5" />
                              {isQuickSubmitting === cls.offeringId ? 'Saving…' : 'Quick log'}
                            </button>
                            <button
                              type="button"
                              onClick={() => handleQuickLog(cls)}
                              className="btn btn-quiet h-8 px-2.5 text-[12px]"
                              title="Customize topic, cancel or swap"
                            >
                              Options
                            </button>
                          </div>
                        )}
                      </li>
                    ))}
                  </ul>
                </div>
              );
            })}
          </div>
        )}
      </section>

      {/* Deadlines */}
      {deadlines.length > 0 && (
        <section>
          <div className="mb-3 flex items-end justify-between">
            <h3 className="font-display text-[22px] font-medium">Deadlines</h3>
            {onNavigate && (
              <button type="button" onClick={() => onNavigate('deadlines')} className="text-[13px] text-[var(--accent)] hover:underline">All deadlines</button>
            )}
          </div>
          <ul className="card divide-y divide-[var(--hairline-soft)]">
            {[...overdueDeadlines, ...pendingDeadlines].map((d) => (
              <li key={d.id} className="px-5 py-3 flex items-center justify-between gap-4 text-[14px]">
                <span>{d.title}</span>
                <span className={`text-[13px] num ${d.isOverdue ? 'text-[var(--danger-text)]' : 'text-[var(--ink-subtle)]'}`}>
                  {d.isOverdue ? 'Overdue' : `Due ${new Date(d.dueAt).toLocaleDateString(undefined, { day: 'numeric', month: 'short' })}`}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {/* Course Description Drawer Modal */}
      {courseForDescription && (
        <CourseDescriptionModal
          course={courseForDescription}
          onClose={() => setCourseForDescription(null)}
        />
      )}

      {/* Quick Session Log Modal */}
      {selectedClass && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-2xs animate-in fade-in">
          <div className="bg-white rounded-lg border border-border shadow-2xl max-w-lg w-full p-6 animate-in zoom-in-95 duration-150 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between pb-3 border-b border-border">
              <div>
                <span className="text-xs font-bold text-blue-600 uppercase tracking-wider">
                  Session Conduct Log
                </span>
                <h3 className="text-base font-bold text-slate-900 mt-0.5">
                  {selectedClass.courseCode} — {selectedClass.courseName}
                </h3>
                <div className="text-[11px] text-slate-500">
                  Section {selectedClass.section} • {selectedClass.timeSlot} • Date:{' '}
                  <span className="font-semibold text-slate-700">{selectedDate}</span>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setSelectedClass(null)}
                className="text-slate-400 hover:text-slate-700 text-sm font-bold p-1 rounded-md cursor-pointer"
              >
                ×
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
                      className={`py-2 px-3 rounded-md font-bold text-xs border text-center transition-all cursor-pointer ${
                        sessionStatus === st
                          ? 'bg-slate-900 text-white border-slate-900 shadow-xs'
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
                      <label className="block font-bold text-slate-700 mb-1">LTP Component</label>
                      <select
                        value={selectedComponent}
                        onChange={(e) => {
                          const c = e.target.value;
                          setSelectedComponent(c);
                          if (c === 'PRACTICAL' || c === 'LAB') setLecturesTaken(2);
                          else setLecturesTaken(1);
                        }}
                        className="w-full p-2 border border-border rounded-md bg-white text-slate-800"
                      >
                        <option value="LECTURE">Lecture (L)</option>
                        <option value="TUTORIAL">Tutorial (T)</option>
                        <option value="PRACTICAL">Practical / Lab (P)</option>
                        <option value="PBL">Project-Based Learning (PBL)</option>
                      </select>
                    </div>

                    <div>
                      <label className="block font-bold text-slate-700 mb-1">Batch / Sub-Section</label>
                      <input
                        type="text"
                        value={selectedSubSection}
                        onChange={(e) => setSelectedSubSection(e.target.value)}
                        placeholder="e.g. ALL, Batch G1"
                        className="w-full p-2 border border-border rounded-md bg-white text-slate-800"
                      />
                    </div>
                  </div>

                  {/* 3. Unit Selection */}
                  {selectedClass.availableUnits && selectedClass.availableUnits.length > 0 && (
                    <div>
                      <label className="block font-bold text-slate-700 mb-1.5">Curriculum Unit</label>
                      <div className="flex flex-wrap gap-1.5">
                        {selectedClass.availableUnits.map((u) => (
                          <button
                            key={u.unitNumber}
                            type="button"
                            onClick={() => handleUnitChange(u.unitNumber)}
                            className={`px-3 py-1.5 rounded-md font-semibold text-xs border transition-colors cursor-pointer ${
                              selectedUnit === u.unitNumber
                                ? 'bg-blue-600 text-white border-blue-600'
                                : 'bg-slate-50 text-slate-700 border-border hover:bg-slate-100'
                            }`}
                          >
                            Unit {u.unitNumber}
                          </button>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* 4. Topic Selection */}
                  <div>
                    <label className="block font-bold text-slate-700 mb-1">Topic Covered</label>
                    <div className="space-y-2">
                      <select
                        value={selectedTopicId || 'CUSTOM'}
                        onChange={handleTopicSelection}
                        className="w-full p-2 border border-border rounded-md bg-white text-slate-800"
                      >
                        {getTopicsForCurrentModalUnit().map((t) => (
                          <option key={t.id} value={t.id}>
                            #{t.lectureOrder}: {t.topicName}
                          </option>
                        ))}
                        <option value="CUSTOM">Custom / Unlisted Topic</option>
                      </select>

                      <input
                        type="text"
                        value={customTopic}
                        onChange={(e) => setCustomTopic(e.target.value)}
                        placeholder="Enter topic name..."
                        required
                        className="w-full p-2 border border-border rounded-md bg-white text-slate-800"
                      />
                    </div>
                  </div>

                  {/* 5. Contact Hours Taken */}
                  <div>
                    <label className="block font-bold text-slate-700 mb-1">Contact Hours Conducted</label>
                    <div className="flex items-center gap-2">
                      {[1, 2, 3].map((num) => (
                        <button
                          key={num}
                          type="button"
                          onClick={() => setLecturesTaken(num)}
                          className={`px-4 py-2 rounded-md font-bold border transition-colors cursor-pointer ${
                            lecturesTaken === num
                              ? 'bg-slate-900 text-white border-slate-900'
                              : 'bg-white text-slate-700 border-border hover:bg-slate-50'
                          }`}
                        >
                          {num} {num === 1 ? 'Hour' : 'Hours'}
                        </button>
                      ))}
                    </div>
                  </div>
                </>
              )}

              {sessionStatus !== 'HELD' && (
                <div>
                  <label className="block font-bold text-slate-700 mb-1">Reason for Non-Conduct</label>
                  <select
                    value={reasonNotHeld}
                    onChange={(e) => setReasonNotHeld(e.target.value)}
                    className="w-full p-2 border border-border rounded-md bg-white text-slate-800"
                  >
                    <option value="Institutional Holiday / Event">Institutional Holiday / Event</option>
                    <option value="Faculty on Approved Leave">Faculty on Approved Leave</option>
                    <option value="Department Meeting">Department Meeting</option>
                    <option value="Rescheduled to Alternative Slot">Rescheduled to Alternative Slot</option>
                    <option value="Student Placement Drive">Student Placement Drive</option>
                  </select>
                </div>
              )}

              {/* Notes */}
              <div>
                <label className="block font-bold text-slate-700 mb-1">Optional Notes</label>
                <textarea
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  rows={2}
                  placeholder="e.g. Completed practical experiment 3..."
                  className="w-full p-2 border border-border rounded-md bg-white text-slate-800"
                />
              </div>

              {/* Form Buttons */}
              <div className="flex items-center justify-end gap-2 pt-3 border-t border-border">
                <button
                  type="button"
                  onClick={() => setSelectedClass(null)}
                  className="px-4 py-2 rounded-md text-slate-600 hover:bg-slate-100 font-semibold cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="px-5 py-2 bg-slate-900 text-white rounded-md font-bold hover:bg-slate-800 transition-colors shadow-2xs cursor-pointer disabled:opacity-50"
                >
                  {isSubmitting ? 'Recording…' : 'Save Session Log'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
