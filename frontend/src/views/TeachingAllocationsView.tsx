import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { CourseDto, CourseOfferingDto, FacultyLtpLoadDto, TeachingAssignmentDto } from '../types/academics';
import { FacultyDto, DepartmentDto } from '../types/directory';
import { useAuth } from '../context/AuthContext';
import {
  BookOpen,
  Plus,
  User,
  Layers,
  X,
  FileSpreadsheet,
  Trash2,
  AlertTriangle,
  CheckCircle2,
  Upload,
  Search,
  SlidersHorizontal,
  Users,
  BarChart3,
  RotateCcw,
} from 'lucide-react';

/** Weekly hours the course LTP plans for a given allocation component. */
function plannedHoursFor(course: CourseDto | undefined, component: string): number {
  if (!course) return 0;
  const l = course.lectureHours ?? 0;
  const t = course.tutorialHours ?? 0;
  const p = course.practicalHours ?? 0;
  switch ((component || '').toUpperCase()) {
    case 'ALL':
      return l + t + p;
    case 'LECTURE':
      return l;
    case 'TUTORIAL':
      return t;
    case 'PRACTICAL':
    case 'LAB':
      return p;
    case 'THEORY':
      return l + t;
    default:
      return 0;
  }
}
import { ImportSpreadsheetModal } from '../components/ImportSpreadsheetModal';
import { CourseAllocationGroups } from '../components/CourseAllocationGroups';
import { SyllabusImportModal } from '../components/SyllabusImportModal';
import { useAppConfig } from '../lib/appConfig';

export function TeachingAllocationsView() {
  const { user } = useAuth();
  const appConfig = useAppConfig();
  const queryClient = useQueryClient();
  const [selectedSemester, setSelectedSemester] = useState<number | ''>('');
  const [selectedDepartmentId, setSelectedDepartmentId] = useState<number | ''>('');
  const [isAssignModalOpen, setIsAssignModalOpen] = useState(false);
  const [isImportModalOpen, setIsImportModalOpen] = useState(false);
  const [isCourseModalOpen, setIsCourseModalOpen] = useState(false);
  /** Course just added from the Add Course form, awaiting its description upload. */
  const [createdCourse, setCreatedCourse] = useState<{ id: number; code: string; file: File | null } | null>(null);

  // Allocations board: which panel is showing, and the filters that narrow every panel.
  const [activePanel, setActivePanel] = useState<'allocations' | 'load' | 'gaps'>('allocations');
  const [searchTerm, setSearchTerm] = useState('');
  const [filterCourseId, setFilterCourseId] = useState<number | ''>('');
  const [filterFacultyId, setFilterFacultyId] = useState<number | ''>('');
  const [filterComponent, setFilterComponent] = useState<string>('');

  // New course + section form state
  const [courseCode, setCourseCode] = useState('');
  const [courseName, setCourseName] = useState('');
  const [courseCategory, setCourseCategory] = useState<'CORE' | 'DEPARTMENT_ELECTIVE' | 'OPEN_ELECTIVE'>('CORE');
  const [courseCredits, setCourseCredits] = useState('');
  const [courseLtp, setCourseLtp] = useState('3-0-0');
  const [courseDescription, setCourseDescription] = useState('');
  const [courseDescriptionFile, setCourseDescriptionFile] = useState<File | null>(null);

  // Assign-faculty form state
  const [selectedCourseId, setSelectedCourseId] = useState<number | ''>('');
  const [selectedOfferingId, setSelectedOfferingId] = useState<number | ''>('');
  const [selectedFacultyId, setSelectedFacultyId] = useState<number | ''>('');
  const [component, setComponent] = useState<'ALL' | 'LECTURE' | 'TUTORIAL' | 'PRACTICAL'>('ALL');
  const [subSection, setSubSection] = useState<string>('ALL');

  // Inline "new section" state — sections are opened where they are used, in the assign flow
  const [showNewSection, setShowNewSection] = useState(false);
  const [newSectionYear, setNewSectionYear] = useState('2026-2027');
  const [newSectionYearOfStudy, setNewSectionYearOfStudy] = useState(1);
  const [newSectionSemester, setNewSectionSemester] = useState(1);
  const [newSectionLetter, setNewSectionLetter] = useState('A');
  const [newSectionCount, setNewSectionCount] = useState('');

  const { data: departments = [] } = useQuery<DepartmentDto[]>({
    queryKey: ['departments'],
    queryFn: async () => {
      const res = await fetch('/api/departments');
      if (!res.ok) throw new Error('Failed to fetch departments');
      return res.json();
    },
  });

  const { data: offerings = [] } = useQuery<CourseOfferingDto[]>({
    queryKey: ['offerings', selectedSemester, selectedDepartmentId],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (selectedSemester) params.set('semester', String(selectedSemester));
      if (selectedDepartmentId) params.set('departmentId', String(selectedDepartmentId));
      const qs = params.toString();
      const res = await fetch(qs ? `/api/offerings?${qs}` : '/api/offerings');
      if (!res.ok) throw new Error('Failed to fetch offerings');
      return res.json();
    },
  });

  const { data: assignments = [], isLoading } = useQuery<TeachingAssignmentDto[]>({
    queryKey: ['teaching-assignments', selectedDepartmentId],
    queryFn: async () => {
      const url = selectedDepartmentId
        ? `/api/teaching-assignments?departmentId=${selectedDepartmentId}`
        : '/api/teaching-assignments';
      const res = await fetch(url);
      if (!res.ok) throw new Error('Failed to fetch teaching assignments');
      return res.json();
    },
  });

  const { data: facultyList = [] } = useQuery<FacultyDto[]>({
    queryKey: ['faculty'],
    queryFn: async () => {
      const res = await fetch('/api/faculty');
      if (!res.ok) throw new Error('Failed to fetch faculty');
      return res.json();
    },
  });

  const { data: facultyLoad = [] } = useQuery<FacultyLtpLoadDto[]>({
    queryKey: ['faculty-ltp-load'],
    queryFn: async () => {
      const res = await fetch('/api/teaching-assignments/faculty-load');
      if (!res.ok) throw new Error('Failed to fetch faculty LTP load');
      return res.json();
    },
  });

  const { data: courses = [] } = useQuery<CourseDto[]>({
    queryKey: ['courses'],
    queryFn: async () => {
      const res = await fetch('/api/courses');
      if (!res.ok) throw new Error('Failed to fetch courses');
      return res.json();
    },
  });

  const createAssignmentMutation = useMutation({
    mutationFn: async (payload: { offeringId: number; facultyId: number; component: string; subSection?: string; contactHours?: number }) => {
      const res = await fetch('/api/teaching-assignments', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      if (!res.ok) {
        const error = await res.json();
        throw new Error(error.detail || 'Failed to create assignment');
      }
      return res.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['teaching-assignments'] });
      queryClient.invalidateQueries({ queryKey: ['faculty-ltp-load'] });
      setIsAssignModalOpen(false);
      setSelectedCourseId('');
      setSelectedOfferingId('');
      setSelectedFacultyId('');
      setComponent('ALL');
      setSubSection('ALL');
      setShowNewSection(false);
    },
  });

  const deleteAssignmentMutation = useMutation({
    mutationFn: async (id: number) => {
      const res = await fetch(`/api/teaching-assignments/${id}`, {
        method: 'DELETE',
      });
      if (!res.ok) {
        const error = await res.json().catch(() => ({}));
        throw new Error(error.detail || 'Failed to delete assignment');
      }
      return true;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['teaching-assignments'] });
      queryClient.invalidateQueries({ queryKey: ['offerings'] });
      queryClient.invalidateQueries({ queryKey: ['faculty-ltp-load'] });
    },
  });

  const clearAllocationsMutation = useMutation({
    mutationFn: async (sem?: number) => {
      const url = sem !== undefined ? `/api/teaching-assignments?semester=${sem}` : '/api/teaching-assignments';
      const res = await fetch(url, { method: 'DELETE' });
      if (!res.ok) {
        const error = await res.json().catch(() => ({}));
        throw new Error(error.detail || 'Failed to clear allocations');
      }
      return true;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['teaching-assignments'] });
      queryClient.invalidateQueries({ queryKey: ['offerings'] });
      queryClient.invalidateQueries({ queryKey: ['faculty-ltp-load'] });
    },
  });

  /** Wipes the activity layer (logs, assessments, plans, timetable, deadlines) to start a clean test. */
  const resetActivityMutation = useMutation({
    mutationFn: async () => {
      const res = await fetch('/api/admin/reset-activity', { method: 'POST' });
      if (!res.ok) {
        const error = await res.json().catch(() => ({}));
        throw new Error(error.detail || 'Failed to reset activity');
      }
      return res.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries();
    },
  });

  /** Course creation is catalogue-only: sections are opened in the assign-faculty flow. */
  const createCourseMutation = useMutation({
    mutationFn: async (): Promise<CourseDto> => {
      const res = await fetch('/api/courses', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          code: courseCode.trim(),
          name: courseName.trim(),
          category: courseCategory,
          credits: courseCredits ? Number(courseCredits) : null,
          ltp: courseLtp.trim(),
          description: courseDescription.trim() || null,
        }),
      });
      if (!res.ok) {
        const error = await res.json().catch(() => ({}));
        throw new Error(error.detail || error.message || 'Failed to create course');
      }
      return res.json();
    },
    onSuccess: (created: CourseDto) => {
      queryClient.invalidateQueries({ queryKey: ['courses'] });
      setIsCourseModalOpen(false);
      setCourseCode('');
      setCourseName('');
      setCourseCredits('');
      setCourseLtp('3-0-0');
      setCourseDescription('');
      // Hand straight to the description upload so the new course's units and topics get mapped.
      setCreatedCourse({ id: created.id, code: created.code, file: courseDescriptionFile });
      setCourseDescriptionFile(null);
    },
  });

  /** Sections are created on the spot in the assign flow — one letter, or A–N in bulk. */
  const createSectionsMutation = useMutation({
    mutationFn: async (mode: 'single' | 'bulk') => {
      if (mode === 'bulk') {
        const res = await fetch('/api/offerings/bulk', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            courseId: Number(selectedCourseId),
            academicYear: newSectionYear.trim(),
            yearOfStudy: newSectionYearOfStudy,
            semester: newSectionSemester,
            sectionCount: Number(newSectionCount),
          }),
        });
        if (!res.ok) {
          const error = await res.json().catch(() => ({}));
          throw new Error(error.detail || error.message || 'Failed to create sections');
        }
        return (await res.json()) as CourseOfferingDto[];
      }
      const res = await fetch('/api/offerings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          courseId: Number(selectedCourseId),
          academicYear: newSectionYear.trim(),
          yearOfStudy: newSectionYearOfStudy,
          semester: newSectionSemester,
          section: newSectionLetter.trim().toUpperCase(),
        }),
      });
      if (!res.ok) {
        const error = await res.json().catch(() => ({}));
        throw new Error(error.detail || error.message || 'Failed to create section');
      }
      return [(await res.json()) as CourseOfferingDto];
    },
    onSuccess: (created) => {
      queryClient.invalidateQueries({ queryKey: ['offerings'] });
      if (created.length > 0) {
        setSelectedOfferingId(created[0].id);
      }
      setShowNewSection(false);
      setNewSectionCount('');
    },
  });

  const handleCreateSection = () => {
    if (Number(newSectionCount) >= 2) {
      createSectionsMutation.mutate('bulk');
    } else {
      createSectionsMutation.mutate('single');
    }
  };

  const handleDeleteAssignment = (id: number, courseName: string, facultyName: string) => {
    if (window.confirm(`Are you sure you want to remove the allocation of "${courseName}" to ${facultyName}?`)) {
      deleteAssignmentMutation.mutate(id);
    }
  };

  const handleClearAllocations = () => {
    const sem = selectedSemester ? Number(selectedSemester) : undefined;
    const msg = sem
      ? `Are you sure you want to delete ALL teaching allocations for Semester ${sem}? This will reset allocations for next semester.`
      : 'Are you sure you want to delete ALL teaching allocations across all semesters? This will completely reset allocations for next semester.';
    if (window.confirm(msg)) {
      clearAllocationsMutation.mutate(sem);
    }
  };

  const handleResetActivity = () => {
    if (window.confirm(
      'Reset all logged activity? This permanently deletes class logs, assessments, remedial plans, ' +
      'the timetable, deadlines, submissions and notifications. Allocations and syllabi are kept.'
    )) {
      resetActivityMutation.mutate();
    }
  };

  const handleCreateAssignment = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedOfferingId || !selectedFacultyId) return;

    createAssignmentMutation.mutate({
      offeringId: Number(selectedOfferingId),
      facultyId: Number(selectedFacultyId),
      component,
      subSection,
    });
  };

  const isCoordinator = user?.role === 'COORDINATOR';

  /** Every panel on this board reads the same filter set: term, department, course, faculty, component and free text. */
  const filteredAssignments = useMemo(() => {
    const term = searchTerm.trim().toLowerCase();
    return assignments.filter((a) => {
      if (selectedSemester && a.offering.semester !== Number(selectedSemester)) return false;
      if (filterCourseId !== '' && a.offering.course.id !== Number(filterCourseId)) return false;
      if (filterFacultyId !== '' && a.facultyId !== Number(filterFacultyId)) return false;
      if (filterComponent && (a.component || '').toUpperCase() !== filterComponent) return false;
      if (term) {
        const haystack =
          `${a.offering.course.code} ${a.offering.course.name} ${a.facultyName} Sec ${a.offering.section}`.toLowerCase();
        if (!haystack.includes(term)) return false;
      }
      return true;
    });
  }, [assignments, selectedSemester, filterCourseId, filterFacultyId, filterComponent, searchTerm]);

  /** Filter options come from live allocations, so a filter never offers a choice with no rows. */
  const filterCourseOptions = useMemo(() => {
    const seen = new Map<number, string>();
    assignments.forEach((a) =>
      seen.set(a.offering.course.id, `${a.offering.course.code} — ${a.offering.course.name}`)
    );
    return [...seen.entries()].sort((x, y) => x[1].localeCompare(y[1]));
  }, [assignments]);

  const filterFacultyOptions = useMemo(() => {
    const seen = new Map<number, string>();
    assignments.forEach((a) => seen.set(a.facultyId, a.facultyName));
    return [...seen.entries()].sort((x, y) => x[1].localeCompare(y[1]));
  }, [assignments]);

  const filterComponentOptions = useMemo(() => {
    const seen = new Set<string>();
    assignments.forEach((a) => {
      if (a.component) seen.add(a.component.toUpperCase());
    });
    return [...seen].sort();
  }, [assignments]);

  const activeFilterCount =
    (selectedSemester ? 1 : 0) +
    (selectedDepartmentId ? 1 : 0) +
    (filterCourseId !== '' ? 1 : 0) +
    (filterFacultyId !== '' ? 1 : 0) +
    (filterComponent ? 1 : 0) +
    (searchTerm.trim() ? 1 : 0);

  const clearFilters = () => {
    setSearchTerm('');
    setFilterCourseId('');
    setFilterFacultyId('');
    setFilterComponent('');
    setSelectedSemester('');
    setSelectedDepartmentId('');
  };

  /**
   * Load figures are aggregated server-side across the term, so they are never recomputed here —
   * we only narrow the roster to the faculty actually selected.
   */
  const visibleFacultyLoad = useMemo(() => {
    const rows = filterFacultyId === ''
      ? facultyLoad
      : facultyLoad.filter((row) => row.facultyId === Number(filterFacultyId));
    return [...rows].sort((a, b) => b.totalHours - a.totalHours);
  }, [facultyLoad, filterFacultyId]);

  const selectedOffering = offerings.find((o) => o.id === Number(selectedOfferingId));
  const plannedHours = plannedHoursFor(selectedOffering?.course, component);

  const selectedCourse = courses.find((c) => c.id === Number(selectedCourseId));
  const courseOfferings =
    selectedCourseId === '' ? [] : offerings.filter((o) => o.course.id === Number(selectedCourseId));

  /** Offerings of the chosen course, grouped by semester for the section picker. */
  const courseOfferingGroups = useMemo(() => {
    const groups = new Map<number, CourseOfferingDto[]>();
    courseOfferings.forEach((offering) => {
      const list = groups.get(offering.semester) ?? [];
      list.push(offering);
      groups.set(offering.semester, list);
    });
    return [...groups.entries()].sort((a, b) => a[0] - b[0]);
  }, [courseOfferings]);

  const nextSectionLetter = String.fromCharCode(
    Math.min('A'.charCodeAt(0) + courseOfferings.length, 'Z'.charCodeAt(0))
  );

  /** Tutorials and practicals can be split into batches; lectures stay whole-section. */
  /** Batches belong to the chosen section: C → C1, C2. Others are typed in. */
  const sectionKey = (selectedOffering?.section ?? '').toUpperCase();
  const batchOptions = sectionKey && sectionKey !== 'ALL' ? ['ALL', `${sectionKey}1`, `${sectionKey}2`] : ['ALL'];
  const componentAllowsBatches = (c: string) => c === 'TUTORIAL' || c === 'PRACTICAL' || c === 'LAB';

  const courseLtpHours = courseLtp.split('-').map((part) => Number(part) || 0);
  const courseWeeklyContact = courseLtpHours[0] + courseLtpHours[1] + courseLtpHours[2];

  // Owned hours are always derived from the course plan for the chosen component — the
  // coordinator never types a number that could silently disagree with the LTP.
  const plannedHoursLabel = selectedOffering
    ? `Course plan ${selectedOffering.course.ltp || '3-0-0'} — this assignment will own ${plannedHours}h/week`
    : 'Select a section to see the planned hours';



  /**
   * LTP reconciliation: the course owns the plan (L/T/P), so every planned component should
   * have at least one owner. Anything blue here is coursework the timetable can never deliver.
   */
  const ltpGaps = useMemo(() => {
    interface Buckets {
      offering: CourseOfferingDto;
      ownedL: number;
      ownedT: number;
      ownedP: number;
      ownedTheory: number;
      ownedAll: number;
    }

    const byOffering = new Map<number, Buckets>();
    filteredAssignments.forEach((a) => {
      const entry = byOffering.get(a.offering.id) ?? {
        offering: a.offering,
        ownedL: 0,
        ownedT: 0,
        ownedP: 0,
        ownedTheory: 0,
        ownedAll: 0,
      };
      const hours = a.contactHours ?? 0;
      switch ((a.component || '').toUpperCase()) {
        case 'LECTURE':
          entry.ownedL += hours;
          break;
        case 'TUTORIAL':
          entry.ownedT += hours;
          break;
        case 'PRACTICAL':
        case 'LAB':
          entry.ownedP += hours;
          break;
        case 'THEORY':
          entry.ownedTheory += hours;
          break;
        case 'ALL':
          entry.ownedAll += hours;
          break;
        default:
          break;
      }
      byOffering.set(a.offering.id, entry);
    });

    const gaps: { key: number; code: string; name: string; section: string; missing: string[] }[] = [];
    byOffering.forEach((entry) => {
      const course = entry.offering.course;
      const missing: string[] = [];
      if ((course.lectureHours ?? 0) > 0 && entry.ownedL === 0 && entry.ownedTheory === 0 && entry.ownedAll === 0) {
        missing.push('Lecture');
      }
      if ((course.tutorialHours ?? 0) > 0 && entry.ownedT === 0 && entry.ownedTheory === 0 && entry.ownedAll === 0) {
        missing.push('Tutorial');
      }
      if ((course.practicalHours ?? 0) > 0 && entry.ownedP === 0) {
        missing.push('Practical');
      }
      if (missing.length > 0) {
        gaps.push({
          key: entry.offering.id,
          code: course.code,
          name: course.name,
          section: entry.offering.section,
          missing,
        });
      }
    });
    return gaps;
  }, [filteredAssignments]);

  const allocatedHours = filteredAssignments.reduce((sum, a) => sum + (a.contactHours ?? 0), 0);

  return (
    <div className="space-y-6">
      {/* Header & Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="font-sans text-2xl font-bold text-slate-900 tracking-tight">Teaching Allocations</h2>
          <p className="text-sm text-slate-600 mt-0.5">
            Manage semester teaching allocations across lecture, tutorial, and lab batches.
          </p>
        </div>
        {isCoordinator && (
          <button
            type="button"
            onClick={() => setIsAssignModalOpen(true)}
            className="inline-flex items-center gap-2 px-4 py-2 bg-slate-900 text-white text-sm font-semibold rounded-md hover:bg-slate-800 shadow-xs transition-colors self-start sm:self-auto"
          >
            <Plus className="w-4 h-4" />
            Assign Faculty
          </button>
        )}
      </div>

      {/* One filter bar drives every panel below */}
      <div className="bg-white rounded-lg border border-border shadow-xs p-4 space-y-3">
        <div className="flex flex-col lg:flex-row lg:items-center gap-2.5">
          <div className="relative flex-1 min-w-[220px]">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="Search course, code, section or faculty"
              className="w-full pl-9 pr-3 py-2 text-sm bg-slate-50 border border-border rounded-md focus:bg-white focus:outline-none focus:ring-2 focus:ring-slate-900 focus:border-slate-900"
              aria-label="Search allocations"
            />
          </div>

          <div className="flex items-center gap-2 flex-wrap">
            <select
              value={selectedDepartmentId}
              onChange={(e) => setSelectedDepartmentId(e.target.value ? Number(e.target.value) : '')}
              className="px-3 py-2 text-xs bg-slate-50 border border-border rounded-md focus:bg-white focus:outline-none focus:ring-2 focus:ring-slate-900"
              aria-label="Filter by department"
            >
              <option value="">All Departments</option>
              {departments.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name}
                </option>
              ))}
            </select>

            <select
              value={selectedSemester}
              onChange={(e) => setSelectedSemester(e.target.value ? Number(e.target.value) : '')}
              className="px-3 py-2 text-xs bg-slate-50 border border-border rounded-md focus:bg-white focus:outline-none focus:ring-2 focus:ring-slate-900"
              aria-label="Filter by semester"
            >
              <option value="">All Semesters</option>
              {[1, 2, 3, 4, 5, 6, 7, 8].map((sem) => (
                <option key={sem} value={sem}>
                  Semester {sem}
                </option>
              ))}
            </select>

            <select
              value={filterCourseId}
              onChange={(e) => setFilterCourseId(e.target.value ? Number(e.target.value) : '')}
              className="px-3 py-2 text-xs bg-slate-50 border border-border rounded-md focus:bg-white focus:outline-none focus:ring-2 focus:ring-slate-900 max-w-[220px]"
              aria-label="Filter by course"
            >
              <option value="">All Courses</option>
              {filterCourseOptions.map(([id, label]) => (
                <option key={id} value={id}>
                  {label}
                </option>
              ))}
            </select>

            <select
              value={filterFacultyId}
              onChange={(e) => setFilterFacultyId(e.target.value ? Number(e.target.value) : '')}
              className="px-3 py-2 text-xs bg-slate-50 border border-border rounded-md focus:bg-white focus:outline-none focus:ring-2 focus:ring-slate-900"
              aria-label="Filter by faculty"
            >
              <option value="">All Faculty</option>
              {filterFacultyOptions.map(([id, label]) => (
                <option key={id} value={id}>
                  {label}
                </option>
              ))}
            </select>

            <select
              value={filterComponent}
              onChange={(e) => setFilterComponent(e.target.value)}
              className="px-3 py-2 text-xs bg-slate-50 border border-border rounded-md focus:bg-white focus:outline-none focus:ring-2 focus:ring-slate-900"
              aria-label="Filter by component"
            >
              <option value="">All Components</option>
              {filterComponentOptions.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>

            {activeFilterCount > 0 && (
              <button
                type="button"
                onClick={clearFilters}
                className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-semibold text-slate-600 bg-white border border-border rounded-md hover:bg-slate-50 transition-colors"
              >
                <X className="w-3.5 h-3.5" />
                Clear ({activeFilterCount})
              </button>
            )}
          </div>
        </div>

        {isCoordinator && (
          <div className="flex items-center gap-2 flex-wrap pt-3 border-t border-border">
            <span className="inline-flex items-center gap-1.5 text-[10px] font-bold text-slate-500 uppercase tracking-wider mr-1">
              <SlidersHorizontal className="w-3 h-3" />
              Manage
            </span>
            <button
              type="button"
              onClick={() => setIsCourseModalOpen(true)}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-white border border-border text-slate-700 text-xs font-medium rounded-md hover:bg-slate-50 transition-colors"
            >
              <BookOpen className="w-3.5 h-3.5 text-blue-600" />
              Add Course
            </button>
            <button
              type="button"
              onClick={() => setIsImportModalOpen(true)}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-white border border-border text-slate-700 text-xs font-medium rounded-md hover:bg-slate-50 transition-colors"
            >
              <FileSpreadsheet className="w-3.5 h-3.5 text-emerald-600" />
              Import Spreadsheet
            </button>
            <button
              type="button"
              onClick={handleClearAllocations}
              disabled={filteredAssignments.length === 0 || clearAllocationsMutation.isPending}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-white border border-border text-rose-700 text-xs font-medium rounded-md hover:bg-rose-50 transition-colors disabled:opacity-40"
              title="Delete allocations to reset for next semester"
            >
              <Trash2 className="w-3.5 h-3.5 text-rose-600" />
              {selectedSemester ? `Reset Sem ${selectedSemester}` : 'Reset All'}
            </button>
            {appConfig.destructiveActions && (
            <button
              type="button"
              onClick={handleResetActivity}
              disabled={resetActivityMutation.isPending}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-white border border-border text-rose-700 text-xs font-medium rounded-md hover:bg-rose-50 transition-colors disabled:opacity-40"
              title="Clear class logs and activity to start a clean test"
            >
              <RotateCcw className={`w-3.5 h-3.5 text-rose-600 ${resetActivityMutation.isPending ? 'animate-spin' : ''}`} />
              {resetActivityMutation.isPending ? 'Resetting…' : 'Reset activity'}
            </button>
            )}
          </div>
        )}
      </div>

      {/* At-a-glance figures for the current filter */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <div className="bg-white rounded-lg border border-border p-4 shadow-xs">
          <div className="flex items-center justify-between text-slate-500">
            <span className="text-[10px] font-bold uppercase tracking-wider">Allocations</span>
            <User className="w-3.5 h-3.5 text-slate-400" />
          </div>
          <div className="font-sans text-2xl font-bold text-slate-900 mt-1">{filteredAssignments.length}</div>
          <div className="text-[11px] text-slate-500 mt-0.5">
            {activeFilterCount > 0 ? 'matching current filters' : 'across the department'}
          </div>
        </div>

        <div className="bg-white rounded-lg border border-border p-4 shadow-xs">
          <div className="flex items-center justify-between text-slate-500">
            <span className="text-[10px] font-bold uppercase tracking-wider">Hours / week</span>
            <BarChart3 className="w-3.5 h-3.5 text-slate-400" />
          </div>
          <div className="font-sans text-2xl font-bold text-slate-900 mt-1">{allocatedHours}h</div>
          <div className="text-[11px] text-slate-500 mt-0.5">owned contact hours</div>
        </div>

        <div className="bg-white rounded-lg border border-border p-4 shadow-xs">
          <div className="flex items-center justify-between text-slate-500">
            <span className="text-[10px] font-bold uppercase tracking-wider">Faculty</span>
            <Users className="w-3.5 h-3.5 text-slate-400" />
          </div>
          <div className="font-sans text-2xl font-bold text-slate-900 mt-1">
            {new Set(filteredAssignments.map((a) => a.facultyId)).size}
          </div>
          <div className="text-[11px] text-slate-500 mt-0.5">holding allocations</div>
        </div>

        <div className="bg-white rounded-lg border border-border p-4 shadow-xs">
          <div className="flex items-center justify-between text-slate-500">
            <span className="text-[10px] font-bold uppercase tracking-wider">Coverage gaps</span>
            <AlertTriangle className={`w-3.5 h-3.5 ${ltpGaps.length > 0 ? 'text-amber-500' : 'text-emerald-500'}`} />
          </div>
          <div className="font-sans text-2xl font-bold text-slate-900 mt-1">{ltpGaps.length}</div>
          <div className="text-[11px] text-slate-500 mt-0.5">
            {ltpGaps.length > 0 ? 'offerings with unowned components' : 'every component owned'}
          </div>
        </div>
      </div>

      {/* Panel switcher — keeps the three tables from stacking up */}
      <div className="border-b border-border flex items-center gap-1">
        {([
          { id: 'allocations' as const, label: 'Allocations', count: filteredAssignments.length },
          { id: 'load' as const, label: 'Faculty Load', count: visibleFacultyLoad.length },
          { id: 'gaps' as const, label: 'Coverage Gaps', count: ltpGaps.length },
        ]).map((panel) => {
          const isActive = activePanel === panel.id;
          return (
            <button
              key={panel.id}
              type="button"
              onClick={() => setActivePanel(panel.id)}
              className={`inline-flex items-center gap-2 px-4 py-2.5 text-xs font-semibold border-b-2 -mb-px transition-colors ${
                isActive
                  ? 'border-slate-900 text-slate-900'
                  : 'border-transparent text-slate-500 hover:text-slate-900'
              }`}
            >
              {panel.label}
              <span
                className={`tabular-nums text-[10px] px-1.5 py-0.5 rounded-full ${
                  isActive ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-500'
                }`}
              >
                {panel.count}
              </span>
            </button>
          );
        })}
      </div>

      {/* LTP plan vs ownership reconciliation */}
      {activePanel === 'gaps' && (
      <div
        className={`rounded-lg border p-4 shadow-xs flex items-start gap-3 ${
          ltpGaps.length > 0
            ? 'bg-amber-50 border-amber-200'
            : 'bg-emerald-50 border-emerald-200'
        }`}
      >
        {ltpGaps.length > 0 ? (
          <AlertTriangle className="w-4 h-4 text-amber-500 shrink-0 mt-0.5" />
        ) : (
          <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0 mt-0.5" />
        )}
        <div className="min-w-0">
          <div className="text-xs font-semibold text-slate-800">
            {ltpGaps.length > 0
              ? `LTP plan not fully allocated for ${ltpGaps.length} offering${ltpGaps.length > 1 ? 's' : ''}`
              : 'Every planned LTP component has an owner'}
          </div>
          {ltpGaps.length > 0 ? (
            <div className="flex flex-wrap gap-1.5 mt-2">
              {ltpGaps.slice(0, 8).map((gap) => (
                <span
                  key={gap.key}
                  title={`${gap.name} — missing ${gap.missing.join(', ')}`}
                  className="inline-flex items-center gap-1 text-[11px] font-medium px-2 py-0.5 rounded-full border border-amber-200 bg-white text-amber-800"
                >
                  <span className="font-mono font-bold">{gap.code}</span>
                  <span className="text-slate-500">Sec {gap.section}</span>
                  <span className="text-slate-300">·</span>
                  <span>no {gap.missing.join(' / ').toLowerCase()} owner</span>
                </span>
              ))}
              {ltpGaps.length > 8 && (
                <span className="text-[11px] text-slate-500 self-center">+{ltpGaps.length - 8} more</span>
              )}
            </div>
          ) : (
            <p className="text-[11px] text-slate-500 mt-0.5">
              Course plan and faculty ownership agree across the current filter.
            </p>
          )}
        </div>
      </div>
      )}

      {/* Computed faculty weekly LTP load */}
      {activePanel === 'load' && visibleFacultyLoad.length > 0 && (
        <div className="bg-white rounded-lg border border-border shadow-xs overflow-hidden">
          <div className="px-5 py-3 border-b border-border flex items-center justify-between gap-3">
            <h3 className="font-sans text-sm font-bold text-slate-900">Faculty Weekly LTP Load</h3>
            <span className="text-[11px] text-slate-500">
              Computed from allocated component hours — never typed
            </span>
          </div>
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-border text-left">
              <thead className="bg-slate-50 text-[11px] font-semibold text-slate-600 uppercase tracking-wider">
                <tr>
                  <th className="px-5 py-2.5">Faculty</th>
                  <th className="px-5 py-2.5 text-right">Lecture</th>
                  <th className="px-5 py-2.5 text-right">Tutorial</th>
                  <th className="px-5 py-2.5 text-right">Practical</th>
                  <th className="px-5 py-2.5 text-right">PBL</th>
                  <th className="px-5 py-2.5 text-right">Total / week</th>
                  <th className="px-5 py-2.5 text-right">Courses</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border text-sm">
                {visibleFacultyLoad.map((row) => (
                  <tr key={row.facultyId} className="hover:bg-slate-50/70 transition-colors">
                    <td className="px-5 py-2.5 text-xs font-medium text-slate-900">{row.facultyName}</td>
                    <td className="px-5 py-2.5 text-right font-mono text-xs text-slate-600">
                      {row.lectureHours > 0 ? `${row.lectureHours}h` : '—'}
                    </td>
                    <td className="px-5 py-2.5 text-right font-mono text-xs text-slate-600">
                      {row.tutorialHours > 0 ? `${row.tutorialHours}h` : '—'}
                    </td>
                    <td className="px-5 py-2.5 text-right font-mono text-xs text-slate-600">
                      {row.practicalHours > 0 ? `${row.practicalHours}h` : '—'}
                    </td>
                    <td className="px-5 py-2.5 text-right font-mono text-xs text-slate-600">
                      {(row.pblHours ?? 0) > 0 ? `${row.pblHours}h` : '—'}
                    </td>
                    <td className="px-5 py-2.5 text-right font-mono text-xs font-bold text-slate-900">
                      {row.totalHours}h
                    </td>
                    <td className="px-5 py-2.5 text-right text-xs text-slate-500">{row.assignmentCount}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {activePanel === 'load' && visibleFacultyLoad.length === 0 && (
        <div className="bg-white rounded-lg border border-border p-12 text-center text-xs text-slate-500">
          No allocated hours to summarise for this filter.
        </div>
      )}

      {/* Allocations, grouped course → section → component */}
      {activePanel === 'allocations' && (
        isLoading ? (
          <p className="card p-8 text-center text-[13px] text-[var(--ink-subtle)]">Loading allocations…</p>
        ) : filteredAssignments.length === 0 ? (
          <div className="card p-12 text-center">
            <BookOpen className="w-8 h-8 text-slate-300 mx-auto mb-2" />
            <h3 className="font-display text-lg">No allocations</h3>
            <p className="text-[13px] text-[var(--ink-subtle)] mt-1">Import the allocation sheet, or clear the filters.</p>
          </div>
        ) : (
          <CourseAllocationGroups
            assignments={filteredAssignments}
            canEdit={isCoordinator}
            expandAll={activeFilterCount > 0}
            onDelete={(a) => handleDeleteAssignment(a.id, a.offering.course.name, a.facultyName)}
          />
        )
      )}

      {/* Assign Course Modal */}
      {isAssignModalOpen && (
        <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-lg border border-border shadow-xl max-w-md w-full p-6 relative animate-in fade-in zoom-in-95 duration-150">
            <button
              type="button"
              onClick={() => setIsAssignModalOpen(false)}
              className="absolute top-4 right-4 text-slate-400 hover:text-slate-600 p-1"
            >
              <X className="w-4 h-4" />
            </button>
            <h3 className="font-sans text-xl font-bold text-slate-900 mb-1">Assign Course to Faculty</h3>
            <p className="text-xs text-slate-500 mb-5">Map a faculty member to a specific course section and component.</p>

            <form onSubmit={handleCreateAssignment} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Course</label>
                <select
                  required
                  value={selectedCourseId}
                  onChange={(e) => {
                    setSelectedCourseId(Number(e.target.value));
                    setSelectedOfferingId('');
                  }}
                  className="w-full px-3 py-2 text-sm border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-slate-900 focus:border-slate-900"
                >
                  <option value="">Select Course...</option>
                  {courses.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.code} — {c.name} ({c.ltp || '3-0-0'})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <div className="flex items-center justify-between mb-1">
                  <label className="block text-xs font-semibold text-slate-700">
                    Section <span className="text-slate-400 font-normal">(semester → section)</span>
                  </label>
                  <button
                    type="button"
                    onClick={() => setShowNewSection((v) => !v)}
                    className="text-[11px] font-semibold text-[#0F5C5C] hover:text-[#0B4747]"
                  >
                    {showNewSection ? 'Cancel' : '+ New section'}
                  </button>
                </div>
                <select
                  required
                  value={selectedOfferingId}
                  onChange={(e) => setSelectedOfferingId(Number(e.target.value))}
                  className="w-full px-3 py-2 text-sm border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-slate-900 focus:border-slate-900"
                >
                  <option value="">
                    {selectedCourseId === '' ? 'Select a course first...' : 'Select Section...'}
                  </option>
                  {courseOfferingGroups.map(([semester, semesterOfferings]) => (
                    <optgroup key={semester} label={`Semester ${semester}`}>
                      {semesterOfferings.map((o) => (
                        <option key={o.id} value={o.id}>
                          Sec {o.section} · {o.academicYear} · Year {o.yearOfStudy}
                        </option>
                      ))}
                    </optgroup>
                  ))}
                </select>

                {showNewSection && selectedCourse && (
                  <div className="mt-2 rounded-md border border-border bg-slate-50 p-3 space-y-2.5">
                    <p className="text-[11px] text-slate-600">
                      Open <span className="font-mono font-semibold">{selectedCourse.code}</span> sections here —
                      no need to leave the assign flow. Bulk mode creates A, B, C… automatically.
                    </p>
                    <div className="grid grid-cols-2 gap-2">
                      <input
                        type="text"
                        required
                        placeholder="2026-2027"
                        value={newSectionYear}
                        onChange={(e) => setNewSectionYear(e.target.value)}
                        className="px-2.5 py-1.5 text-xs border border-border rounded-md font-mono focus:outline-none focus:ring-2 focus:ring-slate-900 focus:border-slate-900"
                      />
                      <select
                        value={newSectionYearOfStudy}
                        onChange={(e) => setNewSectionYearOfStudy(Number(e.target.value))}
                        className="px-2.5 py-1.5 text-xs border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-slate-900 focus:border-slate-900"
                      >
                        {[1, 2, 3, 4].map((y) => (
                          <option key={y} value={y}>
                            Year {y}
                          </option>
                        ))}
                      </select>
                      <select
                        value={newSectionSemester}
                        onChange={(e) => setNewSectionSemester(Number(e.target.value))}
                        className="px-2.5 py-1.5 text-xs border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-slate-900 focus:border-slate-900"
                      >
                        {[1, 2, 3, 4, 5, 6, 7, 8].map((s) => (
                          <option key={s} value={s}>
                            Semester {s}
                          </option>
                        ))}
                      </select>
                      <input
                        type="text"
                        required
                        maxLength={3}
                        disabled={Number(newSectionCount) >= 2}
                        placeholder={`Letter (e.g. ${nextSectionLetter})`}
                        value={newSectionLetter}
                        onChange={(e) => setNewSectionLetter(e.target.value.toUpperCase())}
                        className="px-2.5 py-1.5 text-xs border border-border rounded-md font-mono uppercase focus:outline-none focus:ring-2 focus:ring-slate-900 focus:border-slate-900 disabled:opacity-50"
                      />
                    </div>
                    <div>
                      <label className="block text-[10px] font-semibold text-slate-600 mb-1">
                        How many sections? <span className="text-slate-400 font-normal">(2+ opens A–N in bulk)</span>
                      </label>
                      <input
                        type="number"
                        min={1}
                        max={26}
                        placeholder="1"
                        value={newSectionCount}
                        onChange={(e) => setNewSectionCount(e.target.value)}
                        className="w-full px-2.5 py-1.5 text-xs border border-border rounded-md font-mono focus:outline-none focus:ring-2 focus:ring-slate-900 focus:border-slate-900"
                      />
                    </div>
                    <button
                      type="button"
                      onClick={handleCreateSection}
                      disabled={createSectionsMutation.isPending}
                      className="w-full py-1.5 text-xs font-semibold bg-slate-900 text-white rounded-md hover:bg-slate-800 transition-colors disabled:opacity-50"
                    >
                      {createSectionsMutation.isPending
                        ? 'Creating…'
                        : Number(newSectionCount) >= 2
                          ? `Create sections A–${String.fromCharCode('A'.charCodeAt(0) + Math.min(Number(newSectionCount), 26) - 1)}`
                          : `Create section ${newSectionLetter || 'A'}`}
                    </button>
                    {createSectionsMutation.isError && (
                      <p className="text-[11px] text-rose-600">{createSectionsMutation.error.message}</p>
                    )}
                  </div>
                )}
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Assigned Faculty Member</label>
                <select
                  required
                  value={selectedFacultyId}
                  onChange={(e) => setSelectedFacultyId(Number(e.target.value))}
                  className="w-full px-3 py-2 text-sm border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-slate-900 focus:border-slate-900"
                >
                  <option value="">Select Faculty...</option>
                  {facultyList.map((f) => (
                    <option key={f.id} value={f.id}>
                      {f.name} ({f.designation})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  LTP Component Owned
                </label>
                <div className="grid grid-cols-2 gap-2">
                  {([
                    { id: 'ALL', label: 'All (L + T + P)', hint: 'owns the entire course' },
                    { id: 'LECTURE', label: 'Lecture (L)', hint: 'lectures only' },
                    { id: 'TUTORIAL', label: 'Tutorial (T)', hint: 'tutorials only' },
                    { id: 'PRACTICAL', label: 'Practical (P)', hint: 'labs only' },
                  ] as const).map((option) => (
                    <button
                      key={option.id}
                      type="button"
                      onClick={() => setComponent(option.id)}
                      title={option.hint}
                      className={`py-2 px-3 text-xs font-semibold rounded-md border flex items-center justify-center gap-2 transition-all ${
                        component === option.id
                          ? 'bg-slate-900 text-white border-slate-900 shadow-xs'
                          : 'bg-slate-50 text-slate-700 border-border hover:bg-slate-100'
                      }`}
                    >
                      {option.id === 'PRACTICAL' ? (
                        <Layers className="w-3.5 h-3.5" />
                      ) : (
                        <BookOpen className="w-3.5 h-3.5" />
                      )}
                      {option.label}
                    </button>
                  ))}
                </div>
                <p className="text-[10px] text-slate-500 mt-2">{plannedHoursLabel}</p>
              </div>

              {selectedOffering && plannedHours === 0 && (
                <p className="text-[10px] text-rose-600">
                  This course plans no {component.toLowerCase()} hours — choose a different component.
                </p>
              )}

              {componentAllowsBatches(component) && selectedOffering && (
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Batch / Sub-Section
                  </label>
                  <div className="grid grid-cols-3 gap-2">
                    {batchOptions.map((b) => (
                      <button
                        key={b}
                        type="button"
                        onClick={() => setSubSection(b)}
                        className={`py-1.5 px-3 text-xs font-semibold rounded-md border text-center transition-all ${
                          subSection === b
                            ? 'bg-slate-900 text-white border-slate-900 shadow-xs'
                            : 'bg-slate-50 text-slate-700 border-border hover:bg-slate-100'
                        }`}
                      >
                        {b === 'ALL' ? 'Full Section' : `Batch ${b}`}
                      </button>
                    ))}
                  </div>
                  <input
                    type="text"
                    maxLength={10}
                    placeholder="Custom batch (e.g. B1, C2) — any number of batches"
                    value={batchOptions.includes(subSection) ? '' : subSection}
                    onChange={(e) => setSubSection(e.target.value.toUpperCase())}
                    className="mt-2 w-full px-3 py-1.5 text-xs border border-border rounded-md font-mono uppercase focus:outline-none focus:ring-2 focus:ring-slate-900 focus:border-slate-900"
                  />
                  <p className="text-[10px] text-slate-500 mt-2">
                    Full Section teaches the whole class; a batch owns one sub-section. The same
                    course + section can have a different faculty per batch or component.
                  </p>
                </div>
              )}

              {createAssignmentMutation.isError && (
                <div className="text-xs text-rose-600 bg-rose-50 p-2.5 rounded border border-rose-200">
                  {createAssignmentMutation.error.message}
                </div>
              )}

              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setIsAssignModalOpen(false)}
                  className="px-4 py-2 text-xs font-medium text-slate-600 hover:bg-slate-100 rounded-md transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={createAssignmentMutation.isPending || plannedHours === 0}
                  className="px-4 py-2 text-xs font-medium bg-slate-900 text-white rounded-md hover:bg-slate-800 transition-colors disabled:opacity-50"
                >
                  {createAssignmentMutation.isPending ? 'Assigning...' : 'Confirm Assignment'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Bulk Spreadsheet Import (courses + faculty, header auto-detection) */}
      {isImportModalOpen && (
        <ImportSpreadsheetModal
          kind="allocations"
          onClose={() => setIsImportModalOpen(false)}
          onSuccess={() => {
            queryClient.invalidateQueries({ queryKey: ['teaching-assignments'] });
            queryClient.invalidateQueries({ queryKey: ['offerings'] });
            queryClient.invalidateQueries({ queryKey: ['faculty'] });
            queryClient.invalidateQueries({ queryKey: ['courses'] });
            queryClient.invalidateQueries({ queryKey: ['faculty-ltp-load'] });
          }}
        />
      )}

      {/* Add Course (with LTP plan) + Section Offering */}
      {isCourseModalOpen && (
        <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-lg border border-border shadow-xl max-w-lg w-full p-6 relative animate-in fade-in zoom-in-95 duration-150 max-h-[90vh] overflow-y-auto">
            <button
              type="button"
              onClick={() => setIsCourseModalOpen(false)}
              className="absolute top-4 right-4 text-slate-400 hover:text-slate-600 p-1"
            >
              <X className="w-4 h-4" />
            </button>
            <h3 className="font-sans text-xl font-bold text-slate-900 mb-1">Add Course</h3>
            <p className="text-xs text-slate-500 mb-5">
              Adds the course to the catalogue with its LTP plan. Next you can upload the course
              description document to map its units and topics. Sections are opened in the Assign
              Faculty flow, where you choose how many each year needs.
            </p>

            <form
              onSubmit={(e) => {
                e.preventDefault();
                createCourseMutation.mutate();
              }}
              className="space-y-4"
            >
              <div className="text-[10px] font-bold text-slate-500 uppercase tracking-wider">Course</div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">Course Code</label>
                  <input
                    type="text"
                    required
                    placeholder="CS505"
                    value={courseCode}
                    onChange={(e) => setCourseCode(e.target.value)}
                    className="w-full px-3 py-2 text-sm border border-border rounded-md font-mono uppercase focus:outline-none focus:ring-2 focus:ring-slate-900 focus:border-slate-900"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">Course Type</label>
                  <select
                    value={courseCategory}
                    onChange={(e) =>
                      setCourseCategory(e.target.value as 'CORE' | 'DEPARTMENT_ELECTIVE' | 'OPEN_ELECTIVE')
                    }
                    className="w-full px-3 py-2 text-sm border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-slate-900 focus:border-slate-900"
                  >
                    <option value="CORE">Core</option>
                    <option value="DEPARTMENT_ELECTIVE">Department Elective</option>
                    <option value="OPEN_ELECTIVE">Open Elective</option>
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Course Name</label>
                <input
                  type="text"
                  required
                  placeholder="Advanced Algorithms"
                  value={courseName}
                  onChange={(e) => setCourseName(e.target.value)}
                  className="w-full px-3 py-2 text-sm border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-slate-900 focus:border-slate-900"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">LTP Plan</label>
                  <input
                    type="text"
                    required
                    placeholder="3-1-2"
                    value={courseLtp}
                    onChange={(e) => setCourseLtp(e.target.value)}
                    className="w-full px-3 py-2 text-sm border border-border rounded-md font-mono focus:outline-none focus:ring-2 focus:ring-slate-900 focus:border-slate-900"
                  />
                  <p className="text-[10px] text-slate-500 mt-1">
                    {courseWeeklyContact}h contact per week
                  </p>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Credits <span className="text-slate-400 font-normal">(auto if blank)</span>
                  </label>
                  <input
                    type="number"
                    min={1}
                    max={12}
                    placeholder="auto"
                    value={courseCredits}
                    onChange={(e) => setCourseCredits(e.target.value)}
                    className="w-full px-3 py-2 text-sm border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-slate-900 focus:border-slate-900"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Course Description <span className="text-slate-400 font-normal">(optional)</span>
                </label>
                <textarea
                  rows={2}
                  placeholder="Shown to faculty and used for syllabus context."
                  value={courseDescription}
                  onChange={(e) => setCourseDescription(e.target.value)}
                  className="w-full px-3 py-2 text-sm border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-slate-900 focus:border-slate-900 resize-none"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Course Description Document{' '}
                  <span className="text-slate-400 font-normal">(optional)</span>
                </label>
                <input
                  id="course-description-file"
                  type="file"
                  accept=".pdf,.doc,.docx,.txt"
                  onChange={(e) => setCourseDescriptionFile(e.target.files?.[0] ?? null)}
                  className="hidden"
                />
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => document.getElementById('course-description-file')?.click()}
                    className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-semibold text-slate-700 bg-white border border-border rounded-md hover:bg-slate-50 transition-colors shrink-0"
                  >
                    <Upload className="w-3.5 h-3.5" />
                    {courseDescriptionFile ? 'Change file' : 'Choose PDF / Word'}
                  </button>
                  {courseDescriptionFile && (
                    <span className="text-[11px] text-slate-600 truncate">{courseDescriptionFile.name}</span>
                  )}
                </div>
                <p className="text-[10px] text-slate-500 mt-1">
                  {courseDescriptionFile
                    ? 'Parsed next so its units and topics map onto this course.'
                    : 'Attach the syllabus document to map its units and topics right after the course is created.'}
                </p>
              </div>



              {createCourseMutation.isError && (
                <div className="text-xs text-rose-600 bg-rose-50 p-2.5 rounded border border-rose-200">
                  {createCourseMutation.error.message}
                </div>
              )}

              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => {
                    setCourseDescriptionFile(null);
                    setIsCourseModalOpen(false);
                  }}
                  className="px-4 py-2 text-xs font-medium text-slate-600 hover:bg-slate-100 rounded-md transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={createCourseMutation.isPending}
                  className="px-4 py-2 text-xs font-medium bg-slate-900 text-white rounded-md hover:bg-slate-800 transition-colors disabled:opacity-50"
                >
                  {createCourseMutation.isPending
                    ? 'Creating…'
                    : courseDescriptionFile
                    ? 'Create & map syllabus'
                    : 'Create Course'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Straight after Add Course: map the syllabus from the course description document. */}
      {createdCourse && !isCourseModalOpen && (
        <SyllabusImportModal
          courseId={createdCourse.id}
          courseCode={createdCourse.code}
          initialFile={createdCourse.file}
          onClose={() => setCreatedCourse(null)}
          onConfirmed={() => {
            queryClient.invalidateQueries({ queryKey: ['courses'] });
            setCreatedCourse(null);
          }}
        />
      )}
    </div>
  );
}
