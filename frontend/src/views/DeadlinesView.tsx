import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { DeadlineDto, DeadlineAssignmentDto } from '../types/deadlines';
import { FacultyDto } from '../types/directory';
import { CourseOfferingDto } from '../types/academics';
import { useAuth } from '../context/AuthContext';
import { Clock, Plus, AlertCircle, CheckCircle, Calendar, X, ChevronDown, ChevronRight, FileText } from 'lucide-react';
import { SubmissionModal } from '../components/SubmissionModal';

export function DeadlinesView() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [selectedAssignmentForModal, setSelectedAssignmentForModal] = useState<DeadlineAssignmentDto | null>(null);
  const [expandedDeadlineId, setExpandedDeadlineId] = useState<number | null>(null);

  // Form state
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [type, setType] = useState('COURSE_FILE');
  const [dueAt, setDueAt] = useState('');
  const [targetRule, setTargetRule] = useState<'ALL_FACULTY' | 'SELECTED_FACULTY' | 'BY_SEMESTER' | 'BY_OFFERING'>('ALL_FACULTY');
  const [selectedFacultyIds, setSelectedFacultyIds] = useState<number[]>([]);
  const [targetSemester, setTargetSemester] = useState<number>(5);
  const [selectedOfferingIds, setSelectedOfferingIds] = useState<number[]>([]);

  const isCoordinator = user?.role === 'COORDINATOR';

  // Fetch all deadlines for coordinator
  const { data: departmentDeadlines = [], isLoading: isDeptLoading } = useQuery<DeadlineDto[]>({
    queryKey: ['deadlines', 'department'],
    queryFn: async () => {
      const res = await fetch('/api/deadlines');
      if (!res.ok) throw new Error('Failed to fetch deadlines');
      return res.json();
    },
    enabled: isCoordinator,
  });

  // Fetch personal deadlines for faculty
  const { data: myDeadlines = [], isLoading: isMyLoading } = useQuery<DeadlineAssignmentDto[]>({
    queryKey: ['deadlines', 'my'],
    queryFn: async () => {
      const res = await fetch('/api/my-deadlines');
      if (!res.ok) throw new Error('Failed to fetch my deadlines');
      return res.json();
    },
    enabled: !isCoordinator && !!user,
  });

  const { data: facultyList = [] } = useQuery<FacultyDto[]>({
    queryKey: ['faculty'],
    queryFn: async () => {
      const res = await fetch('/api/faculty');
      if (!res.ok) throw new Error('Failed to fetch faculty');
      return res.json();
    },
    enabled: isCoordinator && isCreateModalOpen,
  });

  const { data: offerings = [] } = useQuery<CourseOfferingDto[]>({
    queryKey: ['offerings'],
    queryFn: async () => {
      const res = await fetch('/api/offerings');
      if (!res.ok) throw new Error('Failed to fetch offerings');
      return res.json();
    },
    enabled: isCoordinator && isCreateModalOpen,
  });

  // Query assignments for expanded deadline
  const { data: deadlineAssignments = [], isLoading: isAssignmentsLoading } = useQuery<DeadlineAssignmentDto[]>({
    queryKey: ['deadlines', expandedDeadlineId, 'assignments'],
    queryFn: async () => {
      const res = await fetch(`/api/deadlines/${expandedDeadlineId}/assignments`);
      if (!res.ok) throw new Error('Failed to fetch assignments');
      return res.json();
    },
    enabled: isCoordinator && expandedDeadlineId !== null,
  });

  const createDeadlineMutation = useMutation({
    mutationFn: async (payload: {
      title: string;
      description: string;
      type: string;
      dueAt: string;
      targetRule: string;
      selectedFacultyIds?: number[];
      semester?: number;
      selectedOfferingIds?: number[];
    }) => {
      const res = await fetch('/api/deadlines', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.detail || 'Failed to create deadline');
      }
      return res.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['deadlines'] });
      setIsCreateModalOpen(false);
      setTitle('');
      setDescription('');
      setDueAt('');
      setSelectedFacultyIds([]);
      setSelectedOfferingIds([]);
    },
  });

  const handleCreateDeadline = (e: React.FormEvent) => {
    e.preventDefault();
    if (!title || !dueAt) return;

    createDeadlineMutation.mutate({
      title,
      description,
      type,
      dueAt: new Date(dueAt).toISOString(),
      targetRule,
      selectedFacultyIds: targetRule === 'SELECTED_FACULTY' ? selectedFacultyIds : undefined,
      semester: targetRule === 'BY_SEMESTER' ? targetSemester : undefined,
      selectedOfferingIds: targetRule === 'BY_OFFERING' ? selectedOfferingIds : undefined,
    });
  };

  const handleToggleFaculty = (id: number) => {
    setSelectedFacultyIds((prev) =>
      prev.includes(id) ? prev.filter((fId) => fId !== id) : [...prev, id]
    );
  };

  const handleToggleOffering = (id: number) => {
    setSelectedOfferingIds((prev) =>
      prev.includes(id) ? prev.filter((oId) => oId !== id) : [...prev, id]
    );
  };

  const formatDue = (isoString: string) => {
    const d = new Date(isoString);
    return d.toLocaleString('en-IN', {
      timeZone: 'Asia/Kolkata',
      dateStyle: 'medium',
      timeStyle: 'short',
    });
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="font-serif text-2xl font-bold text-slate-900 tracking-tight">
            {isCoordinator ? 'Department Deadlines' : 'My Assigned Deadlines'}
          </h2>
          <p className="text-sm text-slate-600 mt-0.5">
            {isCoordinator
              ? 'Create, target, and monitor academic deadlines across semesters and course sections.'
              : 'Submit course files, evaluation rubrics, and marks before deadlines.'}
          </p>
        </div>
        {isCoordinator && (
          <button
            type="button"
            onClick={() => setIsCreateModalOpen(true)}
            className="inline-flex items-center gap-2 px-4 py-2 bg-primary text-white text-sm font-medium rounded-md hover:bg-primary-hover shadow-xs transition-colors self-start sm:self-auto"
          >
            <Plus className="w-4 h-4" />
            Create Deadline
          </button>
        )}
      </div>

      {/* Content for Coordinator */}
      {isCoordinator && (
        <div className="bg-white rounded-lg border border-border shadow-xs overflow-hidden">
          {isDeptLoading ? (
            <div className="p-8 text-center text-slate-500 text-sm">Loading deadlines...</div>
          ) : departmentDeadlines.length === 0 ? (
            <div className="p-12 text-center">
              <Clock className="w-10 h-10 text-slate-300 mx-auto mb-2" />
              <h3 className="font-serif text-base font-bold text-slate-800">No Deadlines Created</h3>
              <p className="text-xs text-slate-500 mt-1">Create the first department deadline to generate submission slots.</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-border text-left">
                <thead className="bg-slate-50 text-[11px] font-semibold text-slate-600 uppercase tracking-wider">
                  <tr>
                    <th className="w-8 px-3 py-3"></th>
                    <th className="px-5 py-3">Deadline Title</th>
                    <th className="px-5 py-3">Type</th>
                    <th className="px-5 py-3">Due Date (IST)</th>
                    <th className="px-5 py-3">Assignees</th>
                    <th className="px-5 py-3">Status</th>
                    <th className="px-5 py-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border text-sm">
                  {departmentDeadlines.map((d) => {
                    const isExpanded = expandedDeadlineId === d.id;
                    return (
                      <tr key={d.id} className="group">
                        <td colSpan={7} className="p-0">
                          <div
                            onClick={() => setExpandedDeadlineId(isExpanded ? null : d.id)}
                            className="flex items-center px-5 py-3.5 hover:bg-slate-50/70 transition-colors cursor-pointer"
                          >
                            <div className="w-8 shrink-0 text-slate-400">
                              {isExpanded ? (
                                <ChevronDown className="w-4 h-4 text-primary" />
                              ) : (
                                <ChevronRight className="w-4 h-4" />
                              )}
                            </div>
                            <div className="flex-1 min-w-[200px] pr-4">
                              <div className="font-semibold text-slate-900">{d.title}</div>
                              {d.description && (
                                <div className="text-xs text-slate-500 line-clamp-1">{d.description}</div>
                              )}
                            </div>
                            <div className="w-36 shrink-0 px-2">
                              <span className="text-xs px-2.5 py-0.5 rounded-full font-medium bg-slate-100 text-slate-700 border border-slate-200">
                                {d.type.replace('_', ' ')}
                              </span>
                            </div>
                            <div className="w-44 shrink-0 px-2 text-xs text-slate-700 whitespace-nowrap">
                              {formatDue(d.dueAt)}
                            </div>
                            <div className="w-36 shrink-0 px-2 text-xs text-slate-600">
                              <span className="font-semibold text-slate-800">{d.totalAssignmentsCount}</span> faculty assigned
                            </div>
                            <div className="w-28 shrink-0 px-2">
                              {d.isOverdue ? (
                                <span className="inline-flex items-center gap-1 text-xs px-2.5 py-0.5 rounded-full font-semibold bg-rose-50 text-rose-700 border border-rose-200">
                                  <AlertCircle className="w-3 h-3 text-rose-500" /> Overdue
                                </span>
                              ) : (
                                <span className="inline-flex items-center gap-1 text-xs px-2.5 py-0.5 rounded-full font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                                  <CheckCircle className="w-3 h-3 text-emerald-500" /> Active
                                </span>
                              )}
                            </div>
                            <div className="w-32 shrink-0 text-right">
                              <span className="text-xs text-primary font-medium group-hover:underline">
                                {isExpanded ? 'Hide Faculty' : 'Inspect Roster'}
                              </span>
                            </div>
                          </div>

                          {/* Expanded Assignments Panel */}
                          {isExpanded && (
                            <div className="bg-slate-50/80 border-t border-b border-slate-200 px-6 py-4 animate-in fade-in duration-150">
                              <div className="flex items-center justify-between mb-3">
                                <h4 className="text-xs font-semibold text-slate-700 uppercase tracking-wider flex items-center gap-1.5">
                                  <FileText className="w-3.5 h-3.5 text-primary" />
                                  Faculty Submission Roster ({d.title})
                                </h4>
                                <span className="text-xs text-slate-500">
                                  Click any faculty row to inspect file versions & review.
                                </span>
                              </div>

                              {isAssignmentsLoading ? (
                                <div className="p-4 text-center text-xs text-slate-500">Loading assignments...</div>
                              ) : deadlineAssignments.length === 0 ? (
                                <div className="p-4 text-center text-xs text-slate-500">No faculty assigned to this deadline.</div>
                              ) : (
                                <div className="bg-white rounded-md border border-border overflow-hidden">
                                  <table className="min-w-full divide-y divide-border text-left">
                                    <thead className="bg-slate-100/70 text-[10px] font-semibold text-slate-600 uppercase tracking-wider">
                                      <tr>
                                        <th className="px-4 py-2">Faculty Name</th>
                                        <th className="px-4 py-2">Due Date</th>
                                        <th className="px-4 py-2 text-right">Action</th>
                                      </tr>
                                    </thead>
                                    <tbody className="divide-y divide-slate-100 text-xs">
                                      {deadlineAssignments.map((a) => (
                                        <tr key={a.id} className="hover:bg-slate-50 transition-colors">
                                          <td className="px-4 py-2.5 font-medium text-slate-800">
                                            {a.facultyName}
                                          </td>
                                          <td className="px-4 py-2.5 text-slate-500">
                                            {formatDue(a.dueAt)}
                                          </td>
                                          <td className="px-4 py-2.5 text-right">
                                            <button
                                              type="button"
                                              onClick={(e) => {
                                                e.stopPropagation();
                                                setSelectedAssignmentForModal(a);
                                              }}
                                              className="inline-flex items-center gap-1 px-2.5 py-1 bg-white border border-border text-primary hover:bg-primary hover:text-white rounded text-xs font-medium transition-colors shadow-2xs"
                                            >
                                              <FileText className="w-3 h-3" />
                                              Inspect / Review
                                            </button>
                                          </td>
                                        </tr>
                                      ))}
                                    </tbody>
                                  </table>
                                </div>
                              )}
                            </div>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Content for Faculty */}
      {!isCoordinator && (
        <div className="space-y-4">
          {isMyLoading ? (
            <div className="p-8 text-center text-slate-500 text-sm">Loading your deadlines...</div>
          ) : myDeadlines.length === 0 ? (
            <div className="bg-white rounded-lg border border-border p-12 text-center">
              <CheckCircle className="w-10 h-10 text-emerald-500 mx-auto mb-2" />
              <h3 className="font-serif text-base font-bold text-slate-800">All Clear!</h3>
              <p className="text-xs text-slate-500 mt-1">You have no active deadlines pending at this time.</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {myDeadlines.map((assignment) => (
                <div
                  key={assignment.id}
                  className={`bg-white rounded-lg border p-5 shadow-xs transition-all flex flex-col justify-between ${
                    assignment.isOverdue ? 'border-rose-300 ring-1 ring-rose-200' : 'border-border'
                  }`}
                >
                  <div>
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                          {assignment.type.replace('_', ' ')}
                        </span>
                        <h3 className="font-serif text-lg font-bold text-slate-900 mt-0.5">{assignment.title}</h3>
                      </div>
                      {assignment.isOverdue ? (
                        <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-rose-50 text-rose-700 border border-rose-200 shrink-0 flex items-center gap-1">
                          <AlertCircle className="w-3 h-3" /> Overdue
                        </span>
                      ) : (
                        <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 shrink-0 flex items-center gap-1">
                          <Clock className="w-3 h-3" /> Pending
                        </span>
                      )}
                    </div>

                    {assignment.description && (
                      <p className="text-xs text-slate-600 mt-2">{assignment.description}</p>
                    )}

                    <div className="mt-4 pt-3 border-t border-slate-100 flex items-center gap-2 text-xs text-slate-600">
                      <Calendar className="w-3.5 h-3.5 text-slate-400" />
                      <span>Due: <strong className="text-slate-800">{formatDue(assignment.dueAt)}</strong></span>
                    </div>
                  </div>

                  <div className="mt-4 pt-3 flex items-center justify-between">
                    <span className="text-[11px] text-slate-500">File Versioning Active</span>
                    <button
                      type="button"
                      onClick={() => setSelectedAssignmentForModal(assignment)}
                      className="px-3 py-1.5 bg-primary text-white text-xs font-medium rounded-md hover:bg-primary-hover transition-colors shadow-xs"
                    >
                      Submit / View Submission
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Create Deadline Modal */}
      {isCreateModalOpen && (
        <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-lg border border-border shadow-xl max-w-lg w-full p-6 relative max-h-[90vh] overflow-y-auto animate-in fade-in zoom-in-95 duration-150">
            <button
              type="button"
              onClick={() => setIsCreateModalOpen(false)}
              className="absolute top-4 right-4 text-slate-400 hover:text-slate-600 p-1"
            >
              <X className="w-4 h-4" />
            </button>
            <h3 className="font-serif text-xl font-bold text-slate-900 mb-1">Create Department Deadline</h3>
            <p className="text-xs text-slate-500 mb-5">
              Set due date and target faculty or course offerings. Creating generates submission slots automatically.
            </p>

            <form onSubmit={handleCreateDeadline} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Deadline Title</label>
                <input
                  type="text"
                  required
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  placeholder="e.g. Course File & Syllabus Conduct Report"
                  className="w-full px-3 py-2 text-sm border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">Type</label>
                  <select
                    value={type}
                    onChange={(e) => setType(e.target.value)}
                    className="w-full px-3 py-2 text-sm border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
                  >
                    <option value="COURSE_FILE">Course File</option>
                    <option value="EVALUATION">Evaluation Rubric</option>
                    <option value="MARKS_UPLOAD">Marks Upload</option>
                    <option value="SYLLABUS_REPORT">Syllabus Conduct Report</option>
                    <option value="OTHER">Other Compliance Document</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">Due Date &amp; Time (IST)</label>
                  <input
                    type="datetime-local"
                    required
                    value={dueAt}
                    onChange={(e) => setDueAt(e.target.value)}
                    className="w-full px-3 py-2 text-sm border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Description / Instructions</label>
                <textarea
                  rows={2}
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Instructions for faculty regarding required sections..."
                  className="w-full px-3 py-2 text-sm border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
                />
              </div>

              {/* Targeting Rule */}
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1.5">Who does this apply to?</label>
                <div className="grid grid-cols-2 gap-2 text-xs">
                  <label className="flex items-center gap-2 p-2 border rounded-md cursor-pointer hover:bg-slate-50">
                    <input
                      type="radio"
                      name="targetRule"
                      checked={targetRule === 'ALL_FACULTY'}
                      onChange={() => setTargetRule('ALL_FACULTY')}
                    />
                    <span>All Faculty</span>
                  </label>
                  <label className="flex items-center gap-2 p-2 border rounded-md cursor-pointer hover:bg-slate-50">
                    <input
                      type="radio"
                      name="targetRule"
                      checked={targetRule === 'BY_SEMESTER'}
                      onChange={() => setTargetRule('BY_SEMESTER')}
                    />
                    <span>By Semester</span>
                  </label>
                  <label className="flex items-center gap-2 p-2 border rounded-md cursor-pointer hover:bg-slate-50">
                    <input
                      type="radio"
                      name="targetRule"
                      checked={targetRule === 'SELECTED_FACULTY'}
                      onChange={() => setTargetRule('SELECTED_FACULTY')}
                    />
                    <span>Selected Faculty</span>
                  </label>
                  <label className="flex items-center gap-2 p-2 border rounded-md cursor-pointer hover:bg-slate-50">
                    <input
                      type="radio"
                      name="targetRule"
                      checked={targetRule === 'BY_OFFERING'}
                      onChange={() => setTargetRule('BY_OFFERING')}
                    />
                    <span>By Course Offering</span>
                  </label>
                </div>
              </div>

              {/* Target rule parameter inputs */}
              {targetRule === 'BY_SEMESTER' && (
                <div className="p-3 bg-slate-50 rounded-md border border-border">
                  <label className="block text-xs font-semibold text-slate-700 mb-1">Target Semester</label>
                  <select
                    value={targetSemester}
                    onChange={(e) => setTargetSemester(Number(e.target.value))}
                    className="w-full px-3 py-1.5 text-xs border border-border rounded-md bg-white"
                  >
                    {[3, 4, 5, 6, 7, 8].map((s) => (
                      <option key={s} value={s}>
                        Semester {s} (All Faculty teaching courses in Sem {s})
                      </option>
                    ))}
                  </select>
                </div>
              )}

              {targetRule === 'SELECTED_FACULTY' && (
                <div className="p-3 bg-slate-50 rounded-md border border-border">
                  <label className="block text-xs font-semibold text-slate-700 mb-1.5">Select Faculty Assignees</label>
                  <div className="max-h-28 overflow-y-auto space-y-1">
                    {facultyList.map((f) => (
                      <label key={f.id} className="flex items-center gap-2 text-xs">
                        <input
                          type="checkbox"
                          checked={selectedFacultyIds.includes(f.id)}
                          onChange={() => handleToggleFaculty(f.id)}
                        />
                        <span>{f.name} ({f.designation})</span>
                      </label>
                    ))}
                  </div>
                </div>
              )}

              {targetRule === 'BY_OFFERING' && (
                <div className="p-3 bg-slate-50 rounded-md border border-border">
                  <label className="block text-xs font-semibold text-slate-700 mb-1.5">Select Course Offerings</label>
                  <div className="max-h-28 overflow-y-auto space-y-1">
                    {offerings.map((o) => (
                      <label key={o.id} className="flex items-center gap-2 text-xs">
                        <input
                          type="checkbox"
                          checked={selectedOfferingIds.includes(o.id)}
                          onChange={() => handleToggleOffering(o.id)}
                        />
                        <span>{o.course.code} {o.course.name} (Sem {o.semester} Sec {o.section})</span>
                      </label>
                    ))}
                  </div>
                </div>
              )}

              {createDeadlineMutation.isError && (
                <div className="text-xs text-rose-600 bg-rose-50 p-2.5 rounded border border-rose-200">
                  {createDeadlineMutation.error.message}
                </div>
              )}

              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setIsCreateModalOpen(false)}
                  className="px-4 py-2 text-xs font-medium text-slate-600 hover:bg-slate-100 rounded-md transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={createDeadlineMutation.isPending}
                  className="px-4 py-2 text-xs font-medium bg-primary text-white rounded-md hover:bg-primary-hover transition-colors disabled:opacity-50"
                >
                  {createDeadlineMutation.isPending ? 'Creating...' : 'Create & Assign Deadline'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Submission Modal for Faculty / Coordinator */}
      {selectedAssignmentForModal && (
        <SubmissionModal
          assignment={selectedAssignmentForModal}
          onClose={() => setSelectedAssignmentForModal(null)}
        />
      )}
    </div>
  );
}
