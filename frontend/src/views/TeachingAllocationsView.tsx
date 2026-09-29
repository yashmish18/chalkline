import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { CourseOfferingDto, TeachingAssignmentDto } from '../types/academics';
import { FacultyDto } from '../types/directory';
import { useAuth } from '../context/AuthContext';
import { BookOpen, Plus, User, Layers, X, Calendar } from 'lucide-react';

export function TeachingAllocationsView() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [selectedSemester, setSelectedSemester] = useState<number | ''>('');
  const [isAssignModalOpen, setIsAssignModalOpen] = useState(false);

  // Form state
  const [selectedOfferingId, setSelectedOfferingId] = useState<number | ''>('');
  const [selectedFacultyId, setSelectedFacultyId] = useState<number | ''>('');
  const [component, setComponent] = useState<'THEORY' | 'LAB'>('THEORY');

  const { data: offerings = [] } = useQuery<CourseOfferingDto[]>({
    queryKey: ['offerings', selectedSemester],
    queryFn: async () => {
      const url = selectedSemester ? `/api/offerings?semester=${selectedSemester}` : '/api/offerings';
      const res = await fetch(url);
      if (!res.ok) throw new Error('Failed to fetch offerings');
      return res.json();
    },
  });

  const { data: assignments = [], isLoading } = useQuery<TeachingAssignmentDto[]>({
    queryKey: ['teaching-assignments'],
    queryFn: async () => {
      const res = await fetch('/api/teaching-assignments');
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

  const createAssignmentMutation = useMutation({
    mutationFn: async (payload: { offeringId: number; facultyId: number; component: string }) => {
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
      setIsAssignModalOpen(false);
      setSelectedOfferingId('');
      setSelectedFacultyId('');
    },
  });

  const handleCreateAssignment = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedOfferingId || !selectedFacultyId) return;

    createAssignmentMutation.mutate({
      offeringId: Number(selectedOfferingId),
      facultyId: Number(selectedFacultyId),
      component,
    });
  };

  const isCoordinator = user?.role === 'COORDINATOR';

  const filteredAssignments = selectedSemester
    ? assignments.filter((a) => a.offering.semester === Number(selectedSemester))
    : assignments;

  return (
    <div className="space-y-6">
      {/* Header & Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="font-serif text-2xl font-bold text-slate-900 tracking-tight">Teaching Allocations</h2>
          <p className="text-sm text-slate-600 mt-0.5">
            Course allocation matrix: faculty mapped to courses, academic year, semester, section, and component.
          </p>
        </div>
        {isCoordinator && (
          <button
            type="button"
            onClick={() => setIsAssignModalOpen(true)}
            className="inline-flex items-center gap-2 px-4 py-2 bg-primary text-white text-sm font-medium rounded-md hover:bg-primary-hover shadow-xs transition-colors self-start sm:self-auto"
          >
            <Plus className="w-4 h-4" />
            Assign Teaching Course
          </button>
        )}
      </div>

      {/* Filter by Semester */}
      <div className="bg-white rounded-lg border border-border p-4 shadow-xs flex items-center justify-between flex-wrap gap-4">
        <div className="flex items-center gap-2">
          <Calendar className="w-4 h-4 text-slate-500" />
          <span className="text-xs font-semibold text-slate-700 uppercase tracking-wider">Semester Filter:</span>
          <div className="flex items-center gap-1.5 ml-2">
            <button
              type="button"
              onClick={() => setSelectedSemester('')}
              className={`px-3 py-1 text-xs rounded-full border transition-all ${
                selectedSemester === ''
                  ? 'bg-primary text-white border-primary font-semibold'
                  : 'bg-slate-50 text-slate-700 border-border hover:bg-slate-100'
              }`}
            >
              All Semesters
            </button>
            {[3, 4, 5, 6, 7, 8].map((sem) => (
              <button
                key={sem}
                type="button"
                onClick={() => setSelectedSemester(selectedSemester === sem ? '' : sem)}
                className={`px-3 py-1 text-xs rounded-full border transition-all ${
                  selectedSemester === sem
                    ? 'bg-primary text-white border-primary font-semibold'
                    : 'bg-slate-50 text-slate-700 border-border hover:bg-slate-100'
                }`}
              >
                Sem {sem}
              </button>
            ))}
          </div>
        </div>

        <div className="text-xs text-slate-500 font-medium">
          Showing <span className="font-semibold text-slate-800">{filteredAssignments.length}</span> allocations
        </div>
      </div>

      {/* Allocation Table */}
      <div className="bg-white rounded-lg border border-border shadow-xs overflow-hidden">
        {isLoading ? (
          <div className="p-8 text-center text-slate-500 text-sm">Loading teaching allocation matrix...</div>
        ) : filteredAssignments.length === 0 ? (
          <div className="p-12 text-center">
            <BookOpen className="w-10 h-10 text-slate-300 mx-auto mb-2" />
            <h3 className="font-serif text-base font-bold text-slate-800">No Allocations Found</h3>
            <p className="text-xs text-slate-500 mt-1">No course allocations recorded for the selected filter.</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-border text-left">
              <thead className="bg-slate-50 text-[11px] font-semibold text-slate-600 uppercase tracking-wider">
                <tr>
                  <th className="px-5 py-3">Course Code</th>
                  <th className="px-5 py-3">Course Title</th>
                  <th className="px-5 py-3">Academic Term</th>
                  <th className="px-5 py-3">Section</th>
                  <th className="px-5 py-3">Component</th>
                  <th className="px-5 py-3">Assigned Faculty</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border text-sm">
                {filteredAssignments.map((a) => {
                  const isCurrentFaculty = user?.name === a.facultyName;
                  return (
                    <tr
                      key={a.id}
                      className={`hover:bg-slate-50/70 transition-colors ${
                        isCurrentFaculty ? 'bg-blue-50/40 font-medium' : ''
                      }`}
                    >
                      <td className="px-5 py-3.5 font-mono text-xs font-semibold text-slate-800">
                        {a.offering.course.code}
                      </td>
                      <td className="px-5 py-3.5 font-medium text-slate-900">
                        {a.offering.course.name}
                        {isCurrentFaculty && (
                          <span className="ml-2 text-[10px] px-1.5 py-0.5 rounded bg-blue-100 text-blue-800 font-semibold">
                            My Course
                          </span>
                        )}
                      </td>
                      <td className="px-5 py-3.5 text-xs text-slate-600">
                        {a.offering.academicYear} · Sem {a.offering.semester} (Yr {a.offering.yearOfStudy})
                      </td>
                      <td className="px-5 py-3.5 text-xs text-slate-700">
                        Sec {a.offering.section}
                      </td>
                      <td className="px-5 py-3.5">
                        <span
                          className={`text-xs px-2.5 py-0.5 rounded-full font-semibold border ${
                            a.component === 'LAB'
                              ? 'bg-purple-50 text-purple-700 border-purple-200'
                              : 'bg-emerald-50 text-emerald-700 border-emerald-200'
                          }`}
                        >
                          {a.component}
                        </span>
                      </td>
                      <td className="px-5 py-3.5 text-xs text-slate-800 flex items-center gap-2">
                        <div className="h-6 w-6 rounded-full bg-slate-100 border border-slate-300 flex items-center justify-center text-[10px] font-bold text-slate-700">
                          <User className="w-3.5 h-3.5 text-slate-500" />
                        </div>
                        {a.facultyName}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

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
            <h3 className="font-serif text-xl font-bold text-slate-900 mb-1">Assign Course to Faculty</h3>
            <p className="text-xs text-slate-500 mb-5">Map a faculty member to a specific course section and component.</p>

            <form onSubmit={handleCreateAssignment} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Course Offering (Year &amp; Section)</label>
                <select
                  required
                  value={selectedOfferingId}
                  onChange={(e) => setSelectedOfferingId(Number(e.target.value))}
                  className="w-full px-3 py-2 text-sm border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
                >
                  <option value="">Select Course Offering...</option>
                  {offerings.map((o) => (
                    <option key={o.id} value={o.id}>
                      {o.course.code} — {o.course.name} (Sem {o.semester} Sec {o.section}, {o.academicYear})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Assigned Faculty Member</label>
                <select
                  required
                  value={selectedFacultyId}
                  onChange={(e) => setSelectedFacultyId(Number(e.target.value))}
                  className="w-full px-3 py-2 text-sm border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
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
                <label className="block text-xs font-semibold text-slate-700 mb-1">Component</label>
                <div className="grid grid-cols-2 gap-3">
                  <button
                    type="button"
                    onClick={() => setComponent('THEORY')}
                    className={`py-2 px-3 text-xs font-semibold rounded-md border flex items-center justify-center gap-2 transition-all ${
                      component === 'THEORY'
                        ? 'bg-primary text-white border-primary shadow-xs'
                        : 'bg-slate-50 text-slate-700 border-border hover:bg-slate-100'
                    }`}
                  >
                    <BookOpen className="w-3.5 h-3.5" /> THEORY
                  </button>
                  <button
                    type="button"
                    onClick={() => setComponent('LAB')}
                    className={`py-2 px-3 text-xs font-semibold rounded-md border flex items-center justify-center gap-2 transition-all ${
                      component === 'LAB'
                        ? 'bg-primary text-white border-primary shadow-xs'
                        : 'bg-slate-50 text-slate-700 border-border hover:bg-slate-100'
                    }`}
                  >
                    <Layers className="w-3.5 h-3.5" /> LAB
                  </button>
                </div>
              </div>

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
                  disabled={createAssignmentMutation.isPending}
                  className="px-4 py-2 text-xs font-medium bg-primary text-white rounded-md hover:bg-primary-hover transition-colors disabled:opacity-50"
                >
                  {createAssignmentMutation.isPending ? 'Assigning...' : 'Confirm Assignment'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
