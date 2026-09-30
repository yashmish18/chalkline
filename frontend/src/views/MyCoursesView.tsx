import { useState, useEffect, useCallback } from 'react';
import { CourseProgressDto, SyllabusTopicProgressDto, TodayClassDto } from '../types/academics';
import {
  BookOpen,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  ListOrdered,
  AlertCircle,
  RefreshCw,
  Activity,
  Info
} from 'lucide-react';
import { CourseDescriptionModal } from '../components/CourseDescriptionModal';

export function MyCoursesView() {
  const [courses, setCourses] = useState<CourseProgressDto[]>([]);
  const [activeOfferingId, setActiveOfferingId] = useState<number | null>(null);
  const [expandedUnit, setExpandedUnit] = useState<number | null>(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showOverviewModal, setShowOverviewModal] = useState(false);

  const fetchMyCourses = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch('/api/academics/my-courses', {
        credentials: 'include',
        headers: { 'Accept': 'application/json' }
      });
      if (!res.ok) {
        throw new Error(`Failed to load courses (${res.status})`);
      }
      const data: CourseProgressDto[] = await res.json();
      setCourses(data);
      if (data.length > 0) {
        setActiveOfferingId((prev) => (prev && data.some((c) => c.offeringId === prev) ? prev : data[0].offeringId));
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to fetch course syllabus progress';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchMyCourses();
  }, [fetchMyCourses]);

  const activeCourse = courses.find((c) => c.offeringId === activeOfferingId) || courses[0];

  // Group topics by unit for the active course
  const unitsMap = new Map<number, { title: string; topics: SyllabusTopicProgressDto[] }>();
  if (activeCourse && activeCourse.topics) {
    activeCourse.topics.forEach((t) => {
      if (!unitsMap.has(t.unitNumber)) {
        unitsMap.set(t.unitNumber, { title: t.unitTitle, topics: [] });
      }
      unitsMap.get(t.unitNumber)!.topics.push(t);
    });
  }

  const courseForModal: TodayClassDto | null = activeCourse
    ? {
        offeringId: activeCourse.offeringId,
        courseCode: activeCourse.courseCode,
        courseName: activeCourse.courseName,
        courseDescription: activeCourse.courseDescription,
        ltp: activeCourse.ltp || '3-0-0',
        section: activeCourse.section,
        subSection: activeCourse.subSection,
        component: activeCourse.component,
        timeSlot: 'Regular Schedule',
        currentUnitNumber: expandedUnit || 1,
        nextTopicId: null,
        nextTopic: '',
        unitTitle: '',
        targetWeek: activeCourse.currentWeek,
        totalPlanned: activeCourse.totalTopics,
        completedSoFar: activeCourse.coveredTopics,
        isLogged: false,
        availableUnits: Array.from(unitsMap.entries()).map(([uNum, uData]) => ({
          unitNumber: uNum,
          unitTitle: uData.title,
          topics: uData.topics.map((t) => ({
            id: t.id,
            courseId: t.courseId,
            unitNumber: t.unitNumber,
            unitTitle: t.unitTitle,
            topicName: t.topicName,
            targetWeek: t.targetWeek,
            lectureOrder: t.lectureOrder,
          })),
        })),
      }
    : null;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="bg-white rounded-lg border border-border p-6 shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 text-xs font-bold text-primary tracking-wide uppercase font-sans">
            <BookOpen className="w-4 h-4" />
            Syllabus as Data — Course Coverage Engine
          </div>
          <h1 className="font-serif text-2xl font-bold text-slate-900 mt-1">
            {activeCourse ? `${activeCourse.courseCode} — ${activeCourse.courseName}` : 'My Allocated Courses'}
          </h1>
          <p className="text-xs text-slate-600 mt-1">
            Track planned vs covered topics unit-by-unit. Coverage % and conduct pacing are derived automatically from session logs.
          </p>

          {activeCourse && (
            <div className="flex flex-wrap items-center gap-2 mt-3">
              <span className="px-2.5 py-0.5 rounded text-[11px] font-mono font-bold bg-slate-100 text-slate-800 border border-slate-200">
                LTP: {activeCourse.ltp || '3-0-0'}
              </span>
              <span className="px-2.5 py-0.5 rounded text-[11px] font-bold bg-blue-50 text-blue-800 border border-blue-200">
                {activeCourse.component}
              </span>
              {activeCourse.subSection && activeCourse.subSection !== 'ALL' && (
                <span className="px-2.5 py-0.5 rounded text-[11px] font-bold bg-indigo-50 text-indigo-800 border border-indigo-200">
                  Lab Batch {activeCourse.subSection}
                </span>
              )}
              <button
                type="button"
                onClick={() => setShowOverviewModal(true)}
                className="inline-flex items-center gap-1.5 px-3 py-1 rounded text-xs font-bold text-primary hover:bg-primary/10 border border-primary/20 transition-colors"
              >
                <Info className="w-3.5 h-3.5" />
                Course Overview &amp; Objectives
              </button>
            </div>
          )}
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={fetchMyCourses}
            className="p-2 border border-border rounded-md hover:bg-slate-50 text-slate-600 hover:text-slate-900 transition-colors mr-1"
            title="Refresh courses"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin text-primary' : ''}`} />
          </button>

          {courses.map((c) => (
            <button
              key={c.offeringId}
              type="button"
              onClick={() => {
                setActiveOfferingId(c.offeringId);
                setExpandedUnit(1);
              }}
              className={`px-3.5 py-2 rounded-md text-xs font-bold transition-all ${
                activeOfferingId === c.offeringId
                  ? 'bg-primary text-white shadow-xs'
                  : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
              }`}
            >
              {c.courseCode} ({c.section})
            </button>
          ))}
        </div>
      </div>

      {error && (
        <div className="bg-rose-50 border border-rose-200 text-rose-900 px-4 py-3 rounded-lg text-xs font-medium flex items-center justify-between">
          <div className="flex items-center gap-2">
            <AlertCircle className="w-4 h-4 text-rose-600 shrink-0" />
            <span>{error}</span>
          </div>
          <button
            type="button"
            onClick={fetchMyCourses}
            className="font-bold underline text-rose-800 hover:text-rose-950 ml-4"
          >
            Retry
          </button>
        </div>
      )}

      {loading && courses.length === 0 ? (
        <div className="p-8 text-center bg-white rounded-lg border border-border text-xs text-slate-500 animate-pulse">
          Loading assigned courses and syllabus data...
        </div>
      ) : courses.length === 0 ? (
        <div className="p-8 text-center bg-white rounded-lg border border-border text-xs text-slate-500">
          No courses assigned to your faculty profile in the active term.
        </div>
      ) : activeCourse ? (
        <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
          {/* Progress Summary Card (1 col) */}
          <div className="space-y-4">
            <div className="bg-white rounded-lg border border-border p-5 shadow-xs space-y-4">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">Syllabus Health</span>
                <span
                  className={`text-[10px] font-bold px-2 py-0.5 rounded-full border ${
                    activeCourse.pacingStatus === 'ON_TRACK'
                      ? 'bg-emerald-50 text-emerald-800 border-emerald-200'
                      : activeCourse.pacingStatus === 'AHEAD'
                      ? 'bg-blue-50 text-blue-800 border-blue-200'
                      : 'bg-amber-50 text-amber-800 border-amber-200'
                  }`}
                >
                  {activeCourse.pacingLabel}
                </span>
              </div>

              <div className="text-center py-2">
                <div className="font-serif text-4xl font-bold text-slate-900">
                  {activeCourse.coveragePercentage}%
                </div>
                <div className="text-xs text-slate-500 mt-1">Syllabus Covered</div>
              </div>

              <div className="w-full h-2 bg-slate-100 rounded-full overflow-hidden">
                <div
                  className={`h-full rounded-full transition-all ${
                    activeCourse.coveragePercentage < 60 ? 'bg-amber-500' : 'bg-primary'
                  }`}
                  style={{ width: `${activeCourse.coveragePercentage}%` }}
                ></div>
              </div>

              <div className="border-t border-border pt-3 space-y-2 text-xs">
                <div className="flex justify-between text-slate-600">
                  <span>Topics Covered:</span>
                  <span className="font-bold text-slate-900">
                    {activeCourse.coveredTopics} / {activeCourse.totalTopics}
                  </span>
                </div>
                <div className="flex justify-between text-slate-600">
                  <span>Expected by Wk {activeCourse.currentWeek}:</span>
                  <span className="font-bold text-slate-900">
                    {activeCourse.expectedTopicsByCurrentWeek} topics
                  </span>
                </div>
                <div className="flex justify-between text-slate-600">
                  <span>Conduct Rate:</span>
                  <span className="font-bold text-slate-900">
                    {activeCourse.conductRatePercentage}% ({activeCourse.heldSessions}/{activeCourse.totalSessions} held)
                  </span>
                </div>
                <div className="flex justify-between text-slate-600">
                  <span>Term &amp; Weight:</span>
                  <span className="font-bold text-slate-900">
                    {activeCourse.credits} Cr ({activeCourse.component})
                  </span>
                </div>
                <div className="flex justify-between text-slate-600">
                  <span>LTP Format:</span>
                  <span className="font-mono font-bold text-slate-900">
                    {activeCourse.ltp || '3-0-0'}
                  </span>
                </div>
                {activeCourse.subSection && activeCourse.subSection !== 'ALL' && (
                  <div className="flex justify-between text-slate-600">
                    <span>Assigned Batch:</span>
                    <span className="font-bold text-indigo-700">
                      Batch {activeCourse.subSection}
                    </span>
                  </div>
                )}
              </div>
            </div>

            {/* Quick Metrics Tile */}
            <div className="bg-slate-50 rounded-lg border border-slate-200 p-4 space-y-3">
              <div className="flex items-center gap-2 text-xs font-bold text-slate-700">
                <Activity className="w-4 h-4 text-primary" />
                <span>Pacing Assessment</span>
              </div>
              <p className="text-[11px] text-slate-600 leading-relaxed">
                {activeCourse.pacingStatus === 'BEHIND'
                  ? `Course pacing is lagging behind the syllabus master by ${Math.abs(activeCourse.topicDelta)} topic(s). Extra tutorial or remedial session recommended.`
                  : activeCourse.pacingStatus === 'AHEAD'
                  ? `Course is progressing ahead of schedule by ${activeCourse.topicDelta} topic(s). Good opportunity for deep-dive lab exercises.`
                  : `Course is tracking precisely on schedule for Week ${activeCourse.currentWeek}.`}
              </p>
            </div>
          </div>

          {/* Unit-by-Unit Syllabus Topics (3 cols) */}
          <div className="lg:col-span-3 space-y-3">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-bold font-serif text-slate-900 uppercase tracking-wider flex items-center gap-2">
                <ListOrdered className="w-4 h-4 text-primary" />
                Syllabus Units &amp; Topic Master
              </h2>
              <span className="text-xs text-slate-500 font-medium">
                {unitsMap.size} units configured
              </span>
            </div>

            {Array.from(unitsMap.entries()).map(([unitNum, unitData]) => {
              const isExpanded = expandedUnit === unitNum;
              const unitTopicsCount = unitData.topics.length;
              const unitCoveredCount = unitData.topics.filter((t) => t.isCovered).length;
              const isFullyCovered = unitTopicsCount > 0 && unitCoveredCount === unitTopicsCount;

              return (
                <div key={unitNum} className="bg-white rounded-lg border border-border overflow-hidden shadow-xs">
                  <button
                    type="button"
                    onClick={() => setExpandedUnit(isExpanded ? null : unitNum)}
                    className="w-full px-5 py-4 flex items-center justify-between bg-white hover:bg-slate-50 transition-colors text-left"
                  >
                    <div className="flex items-center gap-3">
                      <span
                        className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold ${
                          isFullyCovered
                            ? 'bg-emerald-100 text-emerald-800'
                            : 'bg-primary/10 text-primary'
                        }`}
                      >
                        {unitNum}
                      </span>
                      <div>
                        <div className="text-xs font-bold text-slate-900">{unitData.title}</div>
                        <div className="text-[11px] text-slate-500">
                          {unitCoveredCount} of {unitTopicsCount} topics completed
                        </div>
                      </div>
                    </div>

                    <div className="flex items-center gap-3">
                      {isFullyCovered && (
                        <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200">
                          Unit Completed
                        </span>
                      )}
                      {isExpanded ? (
                        <ChevronDown className="w-4 h-4 text-slate-400" />
                      ) : (
                        <ChevronRight className="w-4 h-4 text-slate-400" />
                      )}
                    </div>
                  </button>

                  {isExpanded && (
                    <div className="border-t border-border bg-slate-50/50 p-4 space-y-2">
                      {unitData.topics.map((topic) => (
                        <div
                          key={topic.id}
                          className="flex items-center justify-between p-2.5 rounded bg-white border border-border/80 text-xs"
                        >
                          <div className="flex items-center gap-2.5">
                            {topic.isCovered ? (
                              <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
                            ) : (
                              <div className="w-4 h-4 rounded-full border-2 border-slate-300 shrink-0"></div>
                            )}
                            <span className={topic.isCovered ? 'text-slate-800 font-medium' : 'text-slate-500'}>
                              {topic.lectureOrder}. {topic.topicName}
                            </span>
                          </div>

                          <div className="flex items-center gap-3 text-slate-500 text-[11px] shrink-0">
                            <span>Target: Week {topic.targetWeek}</span>
                            <span
                              className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${
                                topic.isCovered
                                  ? 'bg-emerald-100 text-emerald-800'
                                  : 'bg-slate-100 text-slate-600'
                              }`}
                            >
                              {topic.isCovered ? 'Covered' : 'Pending'}
                            </span>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      ) : null}

      {/* Course Overview & LTP Breakdown Modal */}
      {showOverviewModal && courseForModal && (
        <CourseDescriptionModal
          course={courseForModal}
          onClose={() => setShowOverviewModal(false)}
        />
      )}
    </div>
  );
}
