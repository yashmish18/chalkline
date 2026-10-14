import { useState, useEffect, useCallback, useMemo } from 'react';
import { CourseProgressDto, SyllabusTopicProgressDto, TodayClassDto } from '../types/academics';
import { AcademicDocumentDto, AcademicDocumentCategory } from '../types/documents';
import { CourseDescriptionModal } from '../components/CourseDescriptionModal';
import { UploadAcademicDocumentModal } from '../components/UploadAcademicDocumentModal';
import {
  BookOpen,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  ListOrdered,
  AlertCircle,
  RefreshCw,
  Info,
  ArrowLeft,
  Upload,
  FileText,
  Download,
  Search,
  FileCheck,
} from 'lucide-react';
import { sectionLabel, batchLabel } from '../lib/labels';
import { AssessmentsAndRemedial } from '../components/AssessmentsAndRemedial';

export function MyCoursesView() {
  const [courses, setCourses] = useState<CourseProgressDto[]>([]);
  const [activeOfferingId, setActiveOfferingId] = useState<number | null>(null);
  const [activeTab, setActiveTab] = useState<'syllabus' | 'documents' | 'overview'>('syllabus');
  const [expandedUnit, setExpandedUnit] = useState<number | null>(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchTerm, setSearchTerm] = useState('');

  // Course Document (CD) and uploads
  const [courseDocuments, setCourseDocuments] = useState<AcademicDocumentDto[]>([]);
  const [loadingDocs, setLoadingDocs] = useState(false);
  const [isUploadModalOpen, setIsUploadModalOpen] = useState(false);
  const [uploadCategory, setUploadCategory] = useState<AcademicDocumentCategory>('COURSE_FILE');
  const [uploadSuccessMsg, setUploadSuccessMsg] = useState<string | null>(null);

  // Overview modal
  const [showOverviewModal, setShowOverviewModal] = useState(false);

  const fetchMyCourses = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch('/api/academics/my-courses', {
        credentials: 'include',
        headers: { Accept: 'application/json' },
      });
      if (!res.ok) {
        throw new Error(`Failed to load courses (${res.status})`);
      }
      const data: CourseProgressDto[] = await res.json();
      setCourses(data);
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

  const activeCourse = useMemo(
    () => courses.find((c) => c.offeringId === activeOfferingId) ?? null,
    [courses, activeOfferingId]
  );

  // Fetch course documents when an active course is selected
  const fetchDocumentsForCourse = useCallback(async (courseId: number) => {
    setLoadingDocs(true);
    try {
      const res = await fetch(`/api/academic-documents?courseId=${courseId}`, {
        credentials: 'include',
        headers: { Accept: 'application/json' },
      });
      if (res.ok) {
        const data: AcademicDocumentDto[] = await res.json();
        setCourseDocuments(data);
      }
    } catch {
      // non-fatal
    } finally {
      setLoadingDocs(false);
    }
  }, []);

  useEffect(() => {
    if (activeCourse?.courseId) {
      fetchDocumentsForCourse(activeCourse.courseId);
    } else {
      setCourseDocuments([]);
    }
  }, [activeCourse?.courseId, fetchDocumentsForCourse]);

  const openCourse = (offeringId: number) => {
    setActiveOfferingId(offeringId);
    setActiveTab('syllabus');
    setExpandedUnit(1);
    setUploadSuccessMsg(null);
  };

  const closeCourse = () => {
    setActiveOfferingId(null);
    setActiveTab('syllabus');
    setExpandedUnit(1);
    setUploadSuccessMsg(null);
  };

  // Group topics by unit for active course
  const unitsMap = useMemo(() => {
    const map = new Map<number, { title: string; topics: SyllabusTopicProgressDto[] }>();
    if (activeCourse && activeCourse.topics) {
      activeCourse.topics.forEach((t) => {
        if (!map.has(t.unitNumber)) {
          map.set(t.unitNumber, { title: t.unitTitle, topics: [] });
        }
        map.get(t.unitNumber)!.topics.push(t);
      });
    }
    return map;
  }, [activeCourse]);

  const courseForModal: TodayClassDto | null = activeCourse
    ? {
        offeringId: activeCourse.offeringId,
        courseId: activeCourse.courseId,
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

  // Filtered courses for matrix table
  const filteredCourses = useMemo(() => {
    if (!searchTerm.trim()) return courses;
    const q = searchTerm.toLowerCase();
    return courses.filter(
      (c) =>
        c.courseCode.toLowerCase().includes(q) ||
        c.courseName.toLowerCase().includes(q) ||
        c.section.toLowerCase().includes(q)
    );
  }, [courses, searchTerm]);

  // Aggregate stats across allocated courses
  const totalAllocated = courses.length;
  const totalTopicsOverall = courses.reduce((acc, c) => acc + (c.totalTopics || 0), 0);
  const coveredTopicsOverall = courses.reduce((acc, c) => acc + (c.coveredTopics || 0), 0);
  const overallCoveragePct =
    totalTopicsOverall > 0 ? Math.round((coveredTopicsOverall / totalTopicsOverall) * 100) : 0;
  const totalSessionsOverall = courses.reduce((acc, c) => acc + (c.totalSessions || 0), 0);
  const heldSessionsOverall = courses.reduce((acc, c) => acc + (c.heldSessions || 0), 0);
  const overallConductPct =
    totalSessionsOverall > 0 ? Math.round((heldSessionsOverall / totalSessionsOverall) * 100) : 0;

  const pacingBadge = (status: CourseProgressDto['pacingStatus'], label: string) => {
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
  };

  const formatFileSize = (bytes: number) => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`;
  };

  const getDocCategoryBadge = (cat: AcademicDocumentCategory) => {
    switch (cat) {
      case 'COURSE_FILE':
        return <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-indigo-50 text-indigo-700 border border-indigo-200">Course File (CD)</span>;
      case 'COURSE_SYLLABUS':
        return <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-blue-50 text-blue-700 border border-blue-200">Course Syllabus</span>;
      case 'MID_SEM_QUESTION_BANK':
        return <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-purple-50 text-purple-700 border border-purple-200">Question Bank</span>;
      case 'CONTINUOUS_ASSESSMENT':
        return <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-teal-50 text-teal-700 border border-teal-200">Assessment</span>;
      default:
        return <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-slate-100 text-slate-700 border border-slate-200">{cat}</span>;
    }
  };

  return (
    <div className="space-y-4 max-w-7xl mx-auto">
      {/* --------------------------------------------------------------------- */}
      {/* 1. Header & Quick Action Strip                                       */}
      {/* --------------------------------------------------------------------- */}
      <div className="bg-white rounded-lg border border-border p-4 shadow-2xs flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <BookOpen className="w-4 h-4 text-blue-600" />
            <h1 className="text-lg font-bold text-slate-900 tracking-tight font-sans">
              {activeCourse
                ? `${activeCourse.courseCode} — ${activeCourse.courseName}`
                : 'Course Coverage & Syllabus Portfolio'}
            </h1>
            {activeCourse && (
              <span className="text-xs font-semibold px-2 py-0.5 rounded bg-slate-100 text-slate-700 border border-slate-200 font-mono">
                {sectionLabel(activeCourse.section, null, activeCourse.subSection)}
              </span>
            )}
          </div>
          <p className="text-xs text-slate-500 mt-0.5">
            {activeCourse
              ? 'Unit-by-unit syllabus delivery, lecture pacing, and Course Document (CD / Lab Manual / Dossier) repository.'
              : 'Institutional curriculum pacing matrix across all your allocated course sections for the active term.'}
          </p>
        </div>

        <div className="flex items-center gap-2 self-start sm:self-auto">
          {activeCourse ? (
            <>
              <button
                type="button"
                onClick={closeCourse}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold text-slate-700 bg-white border border-border hover:bg-slate-50 transition-colors cursor-pointer"
              >
                <ArrowLeft className="w-3.5 h-3.5" />
                All Courses
              </button>
              <button
                type="button"
                onClick={() => {
                  setUploadCategory('COURSE_FILE');
                  setIsUploadModalOpen(true);
                }}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-bold text-white bg-slate-900 hover:bg-slate-800 transition-colors shadow-2xs cursor-pointer"
              >
                <Upload className="w-3.5 h-3.5" />
                Upload Course Doc (CD)
              </button>
            </>
          ) : (
            <>
              <button
                type="button"
                onClick={() => {
                  setUploadCategory('COURSE_FILE');
                  setIsUploadModalOpen(true);
                }}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-bold text-white bg-slate-900 hover:bg-slate-800 transition-colors shadow-2xs cursor-pointer"
              >
                <Upload className="w-3.5 h-3.5" />
                Upload Document (CD / Syllabus)
              </button>
              <button
                type="button"
                onClick={fetchMyCourses}
                className="p-1.5 border border-border rounded-md hover:bg-slate-50 text-slate-600 hover:text-slate-900 transition-colors"
                title="Refresh courses"
              >
                <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin text-blue-600' : ''}`} />
              </button>
            </>
          )}
        </div>
      </div>

      {uploadSuccessMsg && (
        <div className="bg-emerald-50 border border-emerald-200 text-emerald-900 px-4 py-2.5 rounded-lg text-xs font-medium flex items-center justify-between animate-in fade-in">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
            <span>{uploadSuccessMsg}</span>
          </div>
          <button
            type="button"
            onClick={() => setUploadSuccessMsg(null)}
            className="text-emerald-700 hover:text-emerald-950 text-xs font-bold"
          >
            ✕
          </button>
        </div>
      )}

      {error && (
        <div className="bg-rose-50 border border-rose-200 text-rose-900 px-4 py-2.5 rounded-lg text-xs font-medium flex items-center justify-between">
          <div className="flex items-center gap-2">
            <AlertCircle className="w-4 h-4 text-rose-600 shrink-0" />
            <span>{error}</span>
          </div>
          <button
            type="button"
            onClick={fetchMyCourses}
            className="font-bold underline text-rose-800 hover:text-rose-950 ml-4 cursor-pointer"
          >
            Retry
          </button>
        </div>
      )}

      {/* --------------------------------------------------------------------- */}
      {/* 2. Main View Body: All Courses Table vs Active Course Cockpit        */}
      {/* --------------------------------------------------------------------- */}
      {!activeCourse ? (
        /* ================================================================= */
        /* ALL ALLOCATED COURSES COCKPIT MATRIX (HIGH-DENSITY, NO CHUNKY CARDS)*/
        /* ================================================================= */
        <div className="space-y-4">
          {/* Executive Portfolio KPI Strip */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            <div className="bg-white rounded-lg border border-border p-3 shadow-2xs">
              <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider block">
                Allocated Courses
              </span>
              <div className="font-sans text-2xl font-bold text-slate-900 mt-0.5">
                {totalAllocated} <span className="text-xs font-normal text-slate-500">Offerings</span>
              </div>
              <div className="text-[11px] text-slate-500 mt-1">Across active semester term</div>
            </div>

            <div className="bg-white rounded-lg border border-border p-3 shadow-2xs">
              <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider block">
                Overall Syllabus Progress
              </span>
              <div className="font-sans text-2xl font-bold text-slate-900 mt-0.5">
                {overallCoveragePct}%
              </div>
              <div className="w-full bg-slate-100 rounded-full h-1 mt-1.5 overflow-hidden">
                <div
                  className="bg-slate-900 h-1 rounded-full transition-all"
                  style={{ width: `${overallCoveragePct}%` }}
                />
              </div>
              <div className="text-[11px] text-slate-500 mt-1">
                {coveredTopicsOverall} of {totalTopicsOverall} topics covered
              </div>
            </div>

            <div className="bg-white rounded-lg border border-border p-3 shadow-2xs">
              <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider block">
                Session Conduct Rate
              </span>
              <div className="font-sans text-2xl font-bold text-slate-900 mt-0.5">
                {overallConductPct}%
              </div>
              <div className="w-full bg-slate-100 rounded-full h-1 mt-1.5 overflow-hidden">
                <div
                  className="bg-emerald-600 h-1 rounded-full transition-all"
                  style={{ width: `${overallConductPct}%` }}
                />
              </div>
              <div className="text-[11px] text-slate-500 mt-1">
                {heldSessionsOverall} of {totalSessionsOverall} sessions held
              </div>
            </div>

            <div className="bg-white rounded-lg border border-border p-3 shadow-2xs">
              <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider block">
                Institutional CD Self-Service
              </span>
              <div className="font-sans text-xs font-semibold text-slate-700 mt-1">
                Course Files, Syllabi &amp; QB
              </div>
              <p className="text-[11px] text-slate-500 mt-1">
                Faculty can attach syllabus &amp; CD documents directly per offering.
              </p>
            </div>
          </div>

          {/* Table Container */}
          <div className="bg-white rounded-lg border border-border shadow-2xs overflow-hidden">
            <div className="p-3 border-b border-border flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-slate-50/50">
              <div className="flex items-center gap-2">
                <ListOrdered className="w-4 h-4 text-blue-600" />
                <h2 className="text-xs font-bold text-slate-900 uppercase tracking-wider">
                  Assigned Courses &amp; Curriculum Delivery Matrix
                </h2>
                <span className="text-[11px] font-mono text-slate-500">
                  ({filteredCourses.length} of {courses.length})
                </span>
              </div>

              {/* Search filter */}
              <div className="relative">
                <Search className="w-3.5 h-3.5 text-slate-400 absolute left-2.5 top-1/2 -translate-y-1/2" />
                <input
                  type="text"
                  placeholder="Filter by code, title, section..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  className="pl-8 pr-3 py-1 text-xs border border-border rounded-md bg-white focus:outline-none focus:ring-1 focus:ring-slate-900 w-56"
                />
              </div>
            </div>

            {loading && courses.length === 0 ? (
              <div className="p-8 text-center text-xs text-slate-500 animate-pulse">
                Loading assigned courses and syllabus matrix...
              </div>
            ) : filteredCourses.length === 0 ? (
              <div className="p-8 text-center text-xs text-slate-500">
                {courses.length === 0
                  ? 'No courses assigned to your faculty profile in the active term.'
                  : 'No course matches your search filter.'}
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-border text-left">
                  <thead className="bg-slate-50 text-[11px] font-semibold text-slate-600 uppercase tracking-wider">
                    <tr>
                      <th className="px-4 py-2.5">Course Code &amp; Title</th>
                      <th className="px-3 py-2.5">Section / Batch</th>
                      <th className="px-3 py-2.5">Component &amp; LTP</th>
                      <th className="px-3 py-2.5">Syllabus Coverage</th>
                      <th className="px-3 py-2.5">Teaching Conduct</th>
                      <th className="px-3 py-2.5">Pacing Status</th>
                      <th className="px-4 py-2.5 text-right">Action</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border text-xs">
                    {filteredCourses.map((c) => (
                      <tr
                        key={c.offeringId}
                        onClick={() => openCourse(c.offeringId)}
                        className="hover:bg-slate-50/80 transition-colors cursor-pointer group"
                      >
                        {/* Course Code & Title */}
                        <td className="px-4 py-3">
                          <div className="flex items-center gap-2">
                            <span className="font-mono font-bold text-slate-900 text-xs">
                              {c.courseCode}
                            </span>
                            <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-slate-100 text-slate-700 border border-slate-200">
                              {c.credits} Cr
                            </span>
                          </div>
                          <div className="text-xs font-semibold text-slate-800 truncate max-w-sm mt-0.5 group-hover:text-blue-700 transition-colors">
                            {c.courseName}
                          </div>
                        </td>

                        {/* Section / Batch */}
                        <td className="px-3 py-3 whitespace-nowrap">
                          <span className="font-medium text-slate-900">{sectionLabel(c.section)}</span>
                          {batchLabel(c.subSection) && (
                            <span className="ml-1.5 chip chip-info">{batchLabel(c.subSection)}</span>
                          )}
                        </td>

                        {/* Component & LTP */}
                        <td className="px-3 py-3 whitespace-nowrap">
                          <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-slate-100 text-slate-700 border border-slate-200">
                            {c.component}
                          </span>
                          <span className="font-mono text-[11px] text-slate-500 ml-2">
                            {c.ltp || '3-0-0'}
                          </span>
                        </td>

                        {/* Syllabus Coverage */}
                        <td className="px-3 py-3 whitespace-nowrap">
                          <div className="flex items-center gap-2">
                            <span className="font-mono font-bold text-slate-900 w-9 text-right">
                              {c.coveragePercentage}%
                            </span>
                            <div className="w-20 bg-slate-100 rounded-full h-1.5 overflow-hidden">
                              <div
                                className={`h-1.5 rounded-full ${
                                  c.coveragePercentage < 50 ? 'bg-amber-500' : 'bg-slate-900'
                                }`}
                                style={{ width: `${c.coveragePercentage}%` }}
                              />
                            </div>
                            <span className="text-[11px] text-slate-500 font-mono">
                              {c.coveredTopics}/{c.totalTopics}
                            </span>
                          </div>
                        </td>

                        {/* Teaching Conduct */}
                        <td className="px-3 py-3 whitespace-nowrap">
                          <div className="flex items-center gap-2">
                            <span className="font-mono font-bold text-slate-900 w-9 text-right">
                              {c.conductRatePercentage}%
                            </span>
                            <div className="w-16 bg-slate-100 rounded-full h-1.5 overflow-hidden">
                              <div
                                className="bg-emerald-600 h-1.5 rounded-full"
                                style={{ width: `${c.conductRatePercentage}%` }}
                              />
                            </div>
                            <span className="text-[11px] text-slate-500 font-mono">
                              {c.heldSessions}/{c.totalSessions}
                            </span>
                          </div>
                        </td>

                        {/* Pacing Status */}
                        <td className="px-3 py-3 whitespace-nowrap">
                          {pacingBadge(c.pacingStatus, c.pacingLabel)}
                        </td>

                        {/* Action */}
                        <td className="px-4 py-3 text-right whitespace-nowrap">
                          <button
                            type="button"
                            onClick={(e) => {
                              e.stopPropagation();
                              openCourse(c.offeringId);
                            }}
                            className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-semibold rounded bg-slate-100 hover:bg-slate-200 text-slate-800 transition-colors"
                          >
                            <span>Open Syllabus &amp; CD</span>
                            <ChevronRight className="w-3.5 h-3.5 text-slate-500" />
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      ) : (
        /* ================================================================= */
        /* ACTIVE COURSE COCKPIT: SYLLABUS PACING, CD REPOSITORY & SPEC      */
        /* ================================================================= */
        <div className="space-y-4">
          {/* Top 4-Metric Summary Bar */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            <div className="bg-white rounded-lg border border-border p-3 shadow-2xs">
              <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider block">
                Syllabus Progress
              </span>
              <div className="flex items-baseline gap-2 mt-0.5">
                <span className="font-sans text-2xl font-bold text-slate-900">
                  {activeCourse.coveragePercentage}%
                </span>
                <span className="text-xs text-slate-500">
                  ({activeCourse.coveredTopics} of {activeCourse.totalTopics} topics)
                </span>
              </div>
              <div className="w-full bg-slate-100 rounded-full h-1.5 mt-2 overflow-hidden">
                <div
                  className={`h-1.5 rounded-full ${
                    activeCourse.coveragePercentage < 50 ? 'bg-amber-500' : 'bg-slate-900'
                  }`}
                  style={{ width: `${activeCourse.coveragePercentage}%` }}
                />
              </div>
            </div>

            <div className="bg-white rounded-lg border border-border p-3 shadow-2xs">
              <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider block">
                Session Conduct Rate
              </span>
              <div className="flex items-baseline gap-2 mt-0.5">
                <span className="font-sans text-2xl font-bold text-slate-900">
                  {activeCourse.conductRatePercentage}%
                </span>
                <span className="text-xs text-slate-500">
                  ({activeCourse.heldSessions} of {activeCourse.totalSessions} held)
                </span>
              </div>
              <div className="w-full bg-slate-100 rounded-full h-1.5 mt-2 overflow-hidden">
                <div
                  className="bg-emerald-600 h-1.5 rounded-full"
                  style={{ width: `${activeCourse.conductRatePercentage}%` }}
                />
              </div>
            </div>

            <div className="bg-white rounded-lg border border-border p-3 shadow-2xs">
              <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider block">
                Curriculum Pacing
              </span>
              <div className="mt-1">
                {pacingBadge(activeCourse.pacingStatus, activeCourse.pacingLabel)}
              </div>
              <div className="text-[11px] text-slate-500 mt-1.5">
                {activeCourse.pacingStatus === 'BEHIND'
                  ? `Lagging by ${Math.abs(activeCourse.topicDelta)} topic(s)`
                  : activeCourse.pacingStatus === 'AHEAD'
                  ? `Ahead by ${activeCourse.topicDelta} topic(s)`
                  : 'On schedule with curriculum master'}
              </div>
            </div>

            <div className="bg-white rounded-lg border border-border p-3 shadow-2xs">
              <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider block">
                Course Documents (CD)
              </span>
              <div className="flex items-baseline gap-2 mt-0.5">
                <span className="font-sans text-2xl font-bold text-slate-900">
                  {courseDocuments.length}
                </span>
                <span className="text-xs text-slate-500">Attached Files</span>
              </div>
              <div className="text-[11px] text-slate-500 mt-1.5">
                Course File, Syllabus, Question Bank &amp; Rubrics
              </div>
            </div>
          </div>

          {/* Segmented Tab Navigation Strip */}
          <div className="bg-white rounded-lg border border-border p-1.5 shadow-2xs flex items-center justify-between">
            <div className="flex items-center gap-1">
              <button
                type="button"
                onClick={() => setActiveTab('syllabus')}
                className={`inline-flex items-center gap-2 px-3 py-1.5 rounded-md text-xs font-bold transition-all cursor-pointer ${
                  activeTab === 'syllabus'
                    ? 'bg-slate-900 text-white shadow-2xs'
                    : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
                }`}
              >
                <ListOrdered className="w-3.5 h-3.5" />
                <span>Syllabus Units &amp; Topics</span>
                <span
                  className={`px-1.5 py-0.2 rounded-full text-[10px] font-mono ${
                    activeTab === 'syllabus' ? 'bg-slate-700 text-white' : 'bg-slate-200 text-slate-700'
                  }`}
                >
                  {unitsMap.size} Units
                </span>
              </button>

              <button
                type="button"
                onClick={() => setActiveTab('documents')}
                className={`inline-flex items-center gap-2 px-3 py-1.5 rounded-md text-xs font-bold transition-all cursor-pointer ${
                  activeTab === 'documents'
                    ? 'bg-slate-900 text-white shadow-2xs'
                    : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
                }`}
              >
                <FileText className="w-3.5 h-3.5" />
                <span>Course Documents &amp; CD Repository</span>
                <span
                  className={`px-1.5 py-0.2 rounded-full text-[10px] font-mono ${
                    activeTab === 'documents' ? 'bg-slate-700 text-white' : 'bg-slate-200 text-slate-700'
                  }`}
                >
                  {courseDocuments.length}
                </span>
              </button>

              <button
                type="button"
                onClick={() => setActiveTab('overview')}
                className={`inline-flex items-center gap-2 px-3 py-1.5 rounded-md text-xs font-bold transition-all cursor-pointer ${
                  activeTab === 'overview'
                    ? 'bg-slate-900 text-white shadow-2xs'
                    : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
                }`}
              >
                <Info className="w-3.5 h-3.5" />
                <span>Course Specifications &amp; LTP</span>
              </button>
            </div>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setShowOverviewModal(true)}
                className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded text-xs font-semibold text-slate-700 hover:bg-slate-100 transition-colors cursor-pointer"
              >
                <Info className="w-3.5 h-3.5 text-slate-500" />
                <span>LTP Modal</span>
              </button>
            </div>
          </div>

          {/* TAB 1: SYLLABUS UNITS & TOPICS */}
          {activeTab === 'syllabus' && (
            <div className="space-y-3">
              {unitsMap.size === 0 ? (
                <div className="bg-white rounded-lg border border-dashed border-border p-8 text-center">
                  <BookOpen className="w-8 h-8 text-slate-300 mx-auto mb-2" />
                  <h3 className="text-xs font-bold text-slate-800">No Syllabus Topics Configured</h3>
                  <p className="text-xs text-slate-500 mt-1 max-w-md mx-auto">
                    Topics for this course have not been entered into the curriculum database. You can upload
                    the course syllabus PDF or Course File (CD) directly to provide the syllabus record.
                  </p>
                  <button
                    type="button"
                    onClick={() => {
                      setUploadCategory('COURSE_SYLLABUS');
                      setIsUploadModalOpen(true);
                    }}
                    className="mt-3 inline-flex items-center gap-1.5 px-3 py-1.5 bg-slate-900 text-white rounded-md text-xs font-bold hover:bg-slate-800 cursor-pointer"
                  >
                    <Upload className="w-3.5 h-3.5" />
                    Upload Course Syllabus PDF
                  </button>
                </div>
              ) : (
                Array.from(unitsMap.entries()).map(([unitNum, unitData]) => {
                  const isExpanded = expandedUnit === unitNum;
                  const unitTopicsCount = unitData.topics.length;
                  const unitCoveredCount = unitData.topics.filter((t) => t.isCovered).length;
                  const unitPct =
                    unitTopicsCount > 0 ? Math.round((unitCoveredCount / unitTopicsCount) * 100) : 0;
                  const isFullyCovered = unitTopicsCount > 0 && unitCoveredCount === unitTopicsCount;

                  return (
                    <div
                      key={unitNum}
                      className="bg-white rounded-lg border border-border shadow-2xs overflow-hidden"
                    >
                      <button
                        type="button"
                        onClick={() => setExpandedUnit(isExpanded ? null : unitNum)}
                        className="w-full px-4 py-3 flex items-center justify-between bg-white hover:bg-slate-50/80 transition-colors text-left cursor-pointer"
                      >
                        <div className="flex items-center gap-3">
                          <span
                            className={`w-6 h-6 rounded flex items-center justify-center text-xs font-bold font-mono ${
                              isFullyCovered
                                ? 'bg-emerald-100 text-emerald-800 border border-emerald-300'
                                : 'bg-slate-100 text-slate-800 border border-slate-200'
                            }`}
                          >
                            U{unitNum}
                          </span>
                          <div>
                            <div className="text-xs font-bold text-slate-900">{unitData.title}</div>
                            <div className="text-[11px] text-slate-500 flex items-center gap-2 mt-0.5">
                              <span>
                                {unitCoveredCount} of {unitTopicsCount} topics completed ({unitPct}%)
                              </span>
                              <span>•</span>
                              <div className="w-16 bg-slate-100 rounded-full h-1 overflow-hidden">
                                <div
                                  className={`h-1 rounded-full ${
                                    isFullyCovered ? 'bg-emerald-600' : 'bg-slate-900'
                                  }`}
                                  style={{ width: `${unitPct}%` }}
                                />
                              </div>
                            </div>
                          </div>
                        </div>

                        <div className="flex items-center gap-3">
                          {isFullyCovered && (
                            <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-emerald-50 text-emerald-700 border border-emerald-200">
                              Completed
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
                        <div className="border-t border-border bg-slate-50/40 p-3 space-y-1.5">
                          <div className="grid grid-cols-12 text-[10px] font-bold text-slate-500 uppercase tracking-wider px-3 py-1">
                            <span className="col-span-1">Lec #</span>
                            <span className="col-span-8">Topic Name</span>
                            <span className="col-span-2 text-center">Target Week</span>
                            <span className="col-span-1 text-right">Status</span>
                          </div>

                          {unitData.topics.map((topic) => (
                            <div
                              key={topic.id}
                              className={`grid grid-cols-12 items-center px-3 py-2 rounded border text-xs transition-colors ${
                                topic.isCovered
                                  ? 'bg-white border-emerald-100 hover:border-emerald-200'
                                  : 'bg-white border-border hover:border-slate-300'
                              }`}
                            >
                              <div className="col-span-1 font-mono text-[11px] text-slate-500 font-semibold">
                                #{topic.lectureOrder}
                              </div>

                              <div className="col-span-8 flex items-center gap-2">
                                {topic.isCovered ? (
                                  <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600 shrink-0" />
                                ) : (
                                  <div className="w-3.5 h-3.5 rounded-full border border-slate-300 shrink-0" />
                                )}
                                <span
                                  className={`truncate ${
                                    topic.isCovered ? 'text-slate-800 font-medium' : 'text-slate-600'
                                  }`}
                                >
                                  {topic.topicName}
                                </span>
                              </div>

                              <div className="col-span-2 text-center text-[11px] font-mono text-slate-500">
                                Week {topic.targetWeek}
                              </div>

                              <div className="col-span-1 text-right">
                                <span
                                  className={`px-1.5 py-0.2 rounded text-[10px] font-bold ${
                                    topic.isCovered
                                      ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                                      : 'bg-slate-100 text-slate-500 border border-slate-200'
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
                })
              )}
            </div>
          )}

          {/* TAB 2: COURSE DOCUMENTS (CD) & REPOSITORY */}
          {activeTab === 'documents' && (
            <div className="space-y-4">
              <div className="bg-white rounded-lg border border-border shadow-2xs overflow-hidden">
                <div className="p-3.5 border-b border-border flex items-center justify-between bg-slate-50/50">
                  <div className="flex items-center gap-2">
                    <FileText className="w-4 h-4 text-blue-600" />
                    <div>
                      <h2 className="text-xs font-bold text-slate-900 uppercase tracking-wider">
                        Institutional Course Dossier (CD) &amp; Academic Documents
                      </h2>
                      <p className="text-[11px] text-slate-500">
                        Official Course File, Syllabus Outlines, Lab Manuals, and Continuous Assessment Question Banks.
                      </p>
                    </div>
                  </div>

                  <button
                    type="button"
                    onClick={() => {
                      setUploadCategory('COURSE_FILE');
                      setIsUploadModalOpen(true);
                    }}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-bold text-white bg-slate-900 hover:bg-slate-800 transition-colors shadow-2xs cursor-pointer"
                  >
                    <Upload className="w-3.5 h-3.5" />
                    Upload New Document
                  </button>
                </div>

                {loadingDocs ? (
                  <div className="p-8 text-center text-xs text-slate-500 animate-pulse">
                    Loading course dossier documents...
                  </div>
                ) : courseDocuments.length === 0 ? (
                  <div className="p-10 text-center space-y-2">
                    <FileCheck className="w-8 h-8 text-slate-300 mx-auto" />
                    <h3 className="text-xs font-bold text-slate-800">
                      No Course Documents Attached Yet
                    </h3>
                    <p className="text-xs text-slate-500 max-w-sm mx-auto">
                      Attach the official Course File (CD), Syllabus Master PDF, Continuous Assessment rubrics,
                      or Question Bank for this course offering.
                    </p>
                    <button
                      type="button"
                      onClick={() => {
                        setUploadCategory('COURSE_FILE');
                        setIsUploadModalOpen(true);
                      }}
                      className="mt-2 inline-flex items-center gap-1.5 px-3 py-1.5 bg-slate-900 text-white rounded-md text-xs font-bold hover:bg-slate-800 cursor-pointer"
                    >
                      <Upload className="w-3.5 h-3.5" />
                      Upload Course Document (CD)
                    </button>
                  </div>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="min-w-full divide-y divide-border text-left">
                      <thead className="bg-slate-50 text-[11px] font-semibold text-slate-600 uppercase tracking-wider">
                        <tr>
                          <th className="px-4 py-2.5">Category</th>
                          <th className="px-4 py-2.5">Title &amp; File Details</th>
                          <th className="px-3 py-2.5">Uploaded By</th>
                          <th className="px-3 py-2.5">Date</th>
                          <th className="px-3 py-2.5">Size</th>
                          <th className="px-4 py-2.5 text-right">Download</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-border text-xs">
                        {courseDocuments.map((doc) => (
                          <tr key={doc.id} className="hover:bg-slate-50/70 transition-colors">
                            <td className="px-4 py-3 whitespace-nowrap">
                              {getDocCategoryBadge(doc.category)}
                            </td>

                            <td className="px-4 py-3">
                              <div className="font-bold text-slate-900">{doc.title}</div>
                              <div className="text-[11px] text-slate-500 font-mono mt-0.5">
                                {doc.fileName}
                              </div>
                              {doc.description && (
                                <p className="text-[11px] text-slate-600 mt-1 max-w-md">
                                  {doc.description}
                                </p>
                              )}
                            </td>

                            <td className="px-3 py-3 whitespace-nowrap text-slate-700 font-medium">
                              {doc.uploadedByName || 'Faculty Member'}
                            </td>

                            <td className="px-3 py-3 whitespace-nowrap text-slate-500 text-[11px]">
                              {new Date(doc.createdAt).toLocaleDateString()}
                            </td>

                            <td className="px-3 py-3 whitespace-nowrap text-slate-500 font-mono text-[11px]">
                              {formatFileSize(doc.fileSize)}
                            </td>

                            <td className="px-4 py-3 text-right whitespace-nowrap">
                              <a
                                href={`/api/academic-documents/${doc.id}/download`}
                                target="_blank"
                                rel="noreferrer"
                                download
                                className="inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-semibold rounded bg-slate-100 hover:bg-slate-200 text-slate-800 transition-colors"
                              >
                                <Download className="w-3.5 h-3.5" />
                                <span>Download</span>
                              </a>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* TAB 3: COURSE SPECIFICATIONS & LTP */}
          {activeTab === 'overview' && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="bg-white rounded-lg border border-border p-4 shadow-2xs space-y-3">
                <div className="flex items-center gap-2 text-xs font-bold text-slate-800 uppercase tracking-wider pb-2 border-b border-border">
                  <BookOpen className="w-4 h-4 text-blue-600" />
                  <span>Course Catalog Details</span>
                </div>
                <div className="space-y-2 text-xs">
                  <div className="flex justify-between py-1 border-b border-border/50">
                    <span className="text-slate-500">Course Code</span>
                    <span className="font-mono font-bold text-slate-900">{activeCourse.courseCode}</span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-border/50">
                    <span className="text-slate-500">Course Name</span>
                    <span className="font-semibold text-slate-900">{activeCourse.courseName}</span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-border/50">
                    <span className="text-slate-500">Allocated Section</span>
                    <span className="font-medium text-slate-900">
                      {sectionLabel(activeCourse.section, null, activeCourse.subSection)}
                    </span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-border/50">
                    <span className="text-slate-500">Component</span>
                    <span className="font-bold text-slate-900">{activeCourse.component}</span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-border/50">
                    <span className="text-slate-500">Credits</span>
                    <span className="font-bold text-slate-900">{activeCourse.credits} Credits</span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-border/50">
                    <span className="text-slate-500">LTP Weekly Structure</span>
                    <span className="font-mono font-bold text-slate-900">{activeCourse.ltp || '3-0-0'}</span>
                  </div>
                  <div className="flex justify-between py-1">
                    <span className="text-slate-500">Active Term</span>
                    <span className="font-medium text-slate-900">{activeCourse.termName || 'Active Semester'}</span>
                  </div>
                </div>
              </div>

              <div className="bg-white rounded-lg border border-border p-4 shadow-2xs space-y-3">
                <div className="flex items-center gap-2 text-xs font-bold text-slate-800 uppercase tracking-wider pb-2 border-b border-border">
                  <Info className="w-4 h-4 text-blue-600" />
                  <span>Course Description &amp; Scope</span>
                </div>
                <p className="text-xs text-slate-600 leading-relaxed">
                  {activeCourse.courseDescription ||
                    'No catalog description currently registered for this course offering. The coordinator or course coordinator attaches the official syllabus outline.'}
                </p>
                <div className="pt-3 border-t border-border flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => setShowOverviewModal(true)}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded bg-slate-100 hover:bg-slate-200 text-slate-800 transition-colors cursor-pointer"
                  >
                    <BookOpen className="w-3.5 h-3.5" />
                    <span>View Full Syllabus LTP Drawer</span>
                  </button>
                </div>
              </div>
            </div>
          )}

          <AssessmentsAndRemedial
            offeringId={activeCourse.offeringId}
            students={0}
            topicDelta={activeCourse.totalTopics > 0 ? activeCourse.topicDelta : 0}
          />
        </div>
      )}

      {/* --------------------------------------------------------------------- */}
      {/* 3. Upload Academic Document Modal                                    */}
      {/* --------------------------------------------------------------------- */}
      {isUploadModalOpen && (
        <UploadAcademicDocumentModal
          initialCategory={uploadCategory}
          initialCourseId={activeCourse?.courseId}
          courses={courses.map((c) => ({
            id: c.courseId,
            code: c.courseCode,
            name: c.courseName,
            type: 'THEORY',
          }))}
          onClose={() => setIsUploadModalOpen(false)}
          onSuccess={(newDoc) => {
            setCourseDocuments((prev) => [newDoc, ...prev]);
            setIsUploadModalOpen(false);
            setUploadSuccessMsg(`Successfully uploaded ${newDoc.title} (${newDoc.category}).`);
          }}
        />
      )}

      {/* --------------------------------------------------------------------- */}
      {/* 4. Course Overview & LTP Breakdown Modal                              */}
      {/* --------------------------------------------------------------------- */}
      {showOverviewModal && courseForModal && (
        <CourseDescriptionModal
          course={courseForModal}
          onClose={() => setShowOverviewModal(false)}
        />
      )}
    </div>
  );
}
