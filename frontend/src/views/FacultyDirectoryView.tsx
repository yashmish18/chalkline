import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { FacultyDto, SkillDto, DepartmentDto } from '../types/directory';
import { useAuth } from '../context/AuthContext';
import { TagInput } from '../components/TagInput';
import { Search, Plus, GraduationCap, Building2, Tag, X, FileSpreadsheet, Trash2, ChevronRight } from 'lucide-react';
import { ImportSpreadsheetModal } from '../components/ImportSpreadsheetModal';
import { FacultyDetailView } from './FacultyDetailView';
import { useAppConfig } from '../lib/appConfig';

export function FacultyDirectoryView() {
  const { user } = useAuth();
  const appConfig = useAppConfig();
  const queryClient = useQueryClient();
  const [selectedSkill, setSelectedSkill] = useState<string>('');
  const [selectedDepartmentId, setSelectedDepartmentId] = useState<number | ''>('');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [isImportModalOpen, setIsImportModalOpen] = useState(false);

  // Form state
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [designation, setDesignation] = useState('Assistant Professor');
  const [qualifications, setQualifications] = useState('');
  const [department, setDepartment] = useState('');
  const [formSkillTags, setFormSkillTags] = useState<string[]>([]);

  // Profile drawer
  const [selectedFacultyId, setSelectedFacultyId] = useState<number | null>(null);

  // Success/error banner for the destructive actions (delete one / delete all / wipe everything)
  const [actionNotice, setActionNotice] = useState<{ kind: 'ok' | 'err'; text: string } | null>(null);

  const { data: skills = [] } = useQuery<SkillDto[]>({
    queryKey: ['skills'],
    queryFn: async () => {
      const res = await fetch('/api/skills');
      if (!res.ok) throw new Error('Failed to fetch skills');
      return res.json();
    },
  });

  const { data: departments = [] } = useQuery<DepartmentDto[]>({
    queryKey: ['departments'],
    queryFn: async () => {
      const res = await fetch('/api/departments');
      if (!res.ok) throw new Error('Failed to fetch departments');
      return res.json();
    },
  });

  const { data: facultyList = [], isLoading } = useQuery<FacultyDto[]>({
    queryKey: ['faculty', selectedSkill, selectedDepartmentId],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (selectedSkill) params.set('skill', selectedSkill);
      if (selectedDepartmentId) params.set('departmentId', String(selectedDepartmentId));
      const qs = params.toString();
      const res = await fetch(qs ? `/api/faculty?${qs}` : '/api/faculty');
      if (!res.ok) throw new Error('Failed to fetch faculty directory');
      return res.json();
    },
  });

  const addFacultyMutation = useMutation({
    mutationFn: async (payload: {
      name: string;
      email: string;
      designation: string;
      qualifications: string;
      department: string;
      skillNames: string[];
    }) => {
      // 1. Create faculty
      const res = await fetch('/api/faculty', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: payload.name,
          email: payload.email,
          designation: payload.designation,
          qualifications: payload.qualifications,
          department: payload.department,
          skillIds: []
        }),
      });
      if (!res.ok) {
        const error = await res.json();
        throw new Error(error.detail || 'Failed to add faculty');
      }
      const created = await res.json();

      // 2. Assign skills by name if any
      if (payload.skillNames.length > 0) {
        await fetch(`/api/faculty/${created.id}/skills-by-name`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ skills: payload.skillNames }),
        });
      }
      return created;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['faculty'] });
      queryClient.invalidateQueries({ queryKey: ['skills'] });
      setIsAddModalOpen(false);
      setName('');
      setEmail('');
      setQualifications('');
      setFormSkillTags([]);
    },
  });

  const deleteFacultyMutation = useMutation({
    mutationFn: async (id: number) => {
      const res = await fetch(`/api/faculty/${id}`, {
        method: 'DELETE',
      });
      if (!res.ok) {
        const error = await res.json().catch(() => ({}));
        throw new Error(error.detail || 'Failed to delete faculty member');
      }
      return true;
    },
    onSuccess: (_data, id) => {
      setActionNotice({ kind: 'ok', text: `Faculty member #${id} deleted, along with their sign-in account.` });
      queryClient.clear();
    },
    onError: (err: Error) => setActionNotice({ kind: 'err', text: err.message }),
  });

  const handleDeleteFaculty = (id: number, facultyName: string) => {      if (window.confirm(`Are you sure you want to delete ${facultyName}? This removes their profile, teaching allocations AND their sign-in account.`)) {
      deleteFacultyMutation.mutate(id);
    }
  };

  const deleteAllFacultyMutation = useMutation({
    mutationFn: async () => {
      const res = await fetch('/api/faculty', {
        method: 'DELETE',
      });
      if (!res.ok) {
        const error = await res.json().catch(() => ({}));
        throw new Error(error.detail || 'Failed to delete faculty members');
      }
      return (await res.json()) as { deleted: number };
    },
    onSuccess: (data) => {
      setActionNotice({
        kind: 'ok',
        text: `Deleted ${data.deleted} faculty ${data.deleted === 1 ? 'profile' : 'profiles'} and their sign-in accounts.`,
      });
      // Clear every cached query so no screen keeps showing the deleted rows.
      queryClient.clear();
    },
    onError: (err: Error) => setActionNotice({ kind: 'err', text: err.message }),
  });

  const wipeAllMutation = useMutation({
    mutationFn: async () => {
      const res = await fetch('/api/admin/data', { method: 'DELETE', credentials: 'include' });
      if (!res.ok) {
        const error = await res.json().catch(() => ({}));
        if (res.status === 404) {
          throw new Error('The server does not offer data deletion. It may be running an older version (restart the backend), or deletion is switched off on this server.');
        }
        throw new Error(error.detail || `Failed to delete data (${res.status})`);
      }
      return (await res.json()) as Record<string, number>;
    },
    onSuccess: () => {
      setActionNotice({
        kind: 'ok',
        text: 'Everything was deleted. Only your coordinator sign-in remains. Set the semester dates again, then import the allocation sheet.',
      });
      queryClient.clear();
    },
    onError: (err: Error) => setActionNotice({ kind: 'err', text: err.message }),
  });

  const handleDeleteAllFaculty = () => {
    const count = facultyList.length;
    const confirmed = window.confirm(
      `Delete all ${count} faculty profiles AND their sign-in accounts? The coordinator account is kept.`,
    );
    if (!confirmed) return;
    if (window.confirm('Last chance — this cannot be undone. Continue?')) {
      deleteAllFacultyMutation.mutate();
    }
  };

  const handleWipeAllData = () => {
    const confirmed = window.confirm(
      'Delete EVERYTHING? Courses, sections, allocations, faculty and their sign-in accounts, departments, timetable, class logs, syllabi, assessments, remedial plans, deadlines, submissions, the semester settings and every uploaded file. Only your coordinator sign-in is kept so you can log back in.',
    );
    if (!confirmed) return;
    if (window.confirm('Last chance — this cannot be undone. Continue?')) {
      wipeAllMutation.mutate();
    }
  };

  const filteredFaculty = facultyList.filter((f) => {
    if (!searchQuery) return true;
    const query = searchQuery.toLowerCase();
    return (
      f.name.toLowerCase().includes(query) ||
      f.designation.toLowerCase().includes(query) ||
      f.qualifications.toLowerCase().includes(query) ||
      f.skills.some((s) => s.name.toLowerCase().includes(query))
    );
  });

  const handleCreateFaculty = (e: React.FormEvent) => {
    e.preventDefault();
    addFacultyMutation.mutate({
      name,
      email,
      designation,
      qualifications,
      department,
      skillNames: formSkillTags,
    });
  };

  const isCoordinator = user?.role === 'COORDINATOR';
  const existingSkillNames = skills.map((s) => s.name);

  if (selectedFacultyId !== null) {
    return (
      <FacultyDetailView
        facultyId={selectedFacultyId}
        onBack={() => setSelectedFacultyId(null)}
      />
    );
  }

  return (
    <div className="space-y-6">
      {/* Header & Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="font-sans text-2xl font-bold text-slate-900 tracking-tight">Faculty Directory</h2>
          <p className="text-sm text-slate-600 mt-0.5">
            Academic qualifications, structured expertise tags, and department profiles.
          </p>
        </div>
        {isCoordinator && (
          <div className="flex items-center gap-2.5 self-start sm:self-auto">
            {appConfig.destructiveActions && (<>
            <button
              type="button"
              onClick={handleWipeAllData}
              disabled={wipeAllMutation.isPending || deleteAllFacultyMutation.isPending || deleteFacultyMutation.isPending}
              className="inline-flex items-center gap-2 px-3.5 py-2 bg-rose-600 text-white text-sm font-medium rounded-md hover:bg-rose-700 shadow-xs transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <Trash2 className="w-4 h-4" />
              {wipeAllMutation.isPending ? 'Deleting…' : 'Delete all data'}
            </button>
            <button
              type="button"
              onClick={handleDeleteAllFaculty}
              disabled={deleteAllFacultyMutation.isPending || wipeAllMutation.isPending || facultyList.length === 0}
              className="inline-flex items-center gap-2 px-3.5 py-2 bg-white border border-red-200 text-red-600 text-sm font-medium rounded-md hover:bg-red-50 shadow-xs transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <Trash2 className="w-4 h-4" />
              {deleteAllFacultyMutation.isPending ? 'Deleting…' : 'Delete all faculty'}
            </button>
            </>)}
            <button
              type="button"
              onClick={() => setIsImportModalOpen(true)}
              className="inline-flex items-center gap-2 px-3.5 py-2 bg-white border border-border text-slate-700 text-sm font-medium rounded-md hover:bg-slate-50 shadow-xs transition-colors"
            >
              <FileSpreadsheet className="w-4 h-4 text-emerald-600" />
              Import CSV / Excel
            </button>
            <button
              type="button"
              onClick={() => setIsAddModalOpen(true)}
              className="inline-flex items-center gap-2 px-4 py-2 bg-slate-900 text-white text-sm font-medium rounded-md hover:bg-slate-800 shadow-xs transition-colors"
            >
              <Plus className="w-4 h-4" />
              Add Faculty
            </button>
          </div>
        )}
      </div>

      {/* Outcome of the delete buttons — errors are never silent */}
      {actionNotice && (
        <div
          role={actionNotice.kind === 'err' ? 'alert' : 'status'}
          className={`flex items-start justify-between gap-4 rounded-lg border p-3.5 text-[13px] ${
            actionNotice.kind === 'err'
              ? 'border-rose-200 bg-rose-50 text-rose-700'
              : 'border-emerald-200 bg-emerald-50 text-emerald-800'
          }`}
        >
          <p>{actionNotice.text}</p>
          <button
            type="button"
            onClick={() => setActionNotice(null)}
            className="shrink-0 font-medium underline underline-offset-2"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Filter Bar */}
      <div className="bg-white rounded-lg border border-border p-4 shadow-xs space-y-3">
        <div className="flex flex-col sm:flex-row items-center gap-3">
          <div className="relative flex-1 w-full">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search faculty by name, qualification, or skill..."
              className="w-full pl-9 pr-4 py-2 text-sm bg-slate-50 border border-border rounded-md focus:bg-white focus:outline-none focus:ring-2 focus:ring-slate-900 focus:border-slate-900 transition-all"
            />
          </div>
          {/* Department filter */}
          <select
            value={selectedDepartmentId}
            onChange={(e) => setSelectedDepartmentId(e.target.value ? Number(e.target.value) : '')}
            className="px-3 py-2 text-sm bg-slate-50 border border-border rounded-md focus:bg-white focus:outline-none focus:ring-2 focus:ring-slate-900 focus:border-slate-900 transition-all"
            aria-label="Filter by department"
          >
            <option value="">All Departments</option>
            {departments.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name}
              </option>
            ))}
          </select>

          {selectedSkill && (
            <button
              type="button"
              onClick={() => setSelectedSkill('')}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-slate-600 bg-slate-100 hover:bg-slate-200 rounded-md transition-colors"
            >
              Clear filter: <span className="font-semibold text-blue-600">{selectedSkill}</span>
              <X className="w-3.5 h-3.5" />
            </button>
          )}
        </div>

        {/* Skill Filter Chips */}
        <div className="flex items-center gap-1.5 overflow-x-auto pt-1 pb-0.5">
          <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider mr-1 shrink-0 flex items-center gap-1">
            <Tag className="w-3 h-3" /> Filter by Skill:
          </span>
          <button
            type="button"
            onClick={() => setSelectedSkill('')}
            className={`px-2.5 py-1 text-xs rounded-full border transition-all shrink-0 ${
              !selectedSkill
                ? 'bg-slate-900 text-white border-slate-900 font-semibold'
                : 'bg-slate-50 text-slate-700 border-border hover:bg-slate-100'
            }`}
          >
            All Skills
          </button>
          {skills.map((skill) => {
            const isSelected = selectedSkill === skill.name;
            return (
              <button
                key={skill.id}
                type="button"
                onClick={() => setSelectedSkill(isSelected ? '' : skill.name)}
                className={`px-2.5 py-1 text-xs rounded-full border transition-all shrink-0 ${
                  isSelected
                    ? 'bg-slate-900 text-white border-slate-900 font-semibold'
                    : 'bg-slate-50 text-slate-700 border-border hover:bg-slate-100'
                }`}
              >
                {skill.name}
              </button>
            );
          })}
        </div>
      </div>

      {/* Grid of Faculty Cards */}
      {isLoading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-48 rounded-lg bg-slate-100 animate-pulse border border-border" />
          ))}
        </div>
      ) : filteredFaculty.length === 0 ? (
        <div className="bg-white rounded-lg border border-border p-12 text-center">
          <GraduationCap className="w-12 h-12 text-slate-300 mx-auto mb-3" />
          <h3 className="font-sans text-lg font-bold text-slate-800">No Faculty Found</h3>
          <p className="text-sm text-slate-500 max-w-sm mx-auto mt-1">
            {selectedSkill
              ? `No faculty members currently match the skill "${selectedSkill}".`
              : 'No faculty profiles found matching your search query.'}
          </p>
          {selectedSkill && (
            <button
              type="button"
              onClick={() => setSelectedSkill('')}
              className="mt-4 px-3 py-1.5 text-xs font-medium text-blue-600 bg-blue-50 hover:bg-blue-100 rounded-md transition-colors"
            >
              Reset Skill Filter
            </button>
          )}
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {filteredFaculty.map((f) => {
            return (
              <div
                key={f.id}
                role="button"
                tabIndex={0}
                onClick={() => setSelectedFacultyId(f.id)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    setSelectedFacultyId(f.id);
                  }
                }}
                className="group bg-white rounded-lg border border-border p-5 shadow-xs hover:border-slate-300 transition-all flex flex-col justify-between cursor-pointer"
                aria-label={`Open full profile for ${f.name}`}
              >
                <div>
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <h3 className="font-sans text-lg font-bold text-slate-900 leading-snug">{f.name}</h3>
                      <p className="text-xs font-semibold text-slate-600 mt-0.5">{f.designation}</p>
                    </div>
                    <div className="flex items-center gap-2">
                      {isCoordinator && (
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            handleDeleteFaculty(f.id, f.name);
                          }}
                          className="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-md transition-colors"
                          title="Delete faculty profile"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      )}
                      <div className="h-9 w-9 rounded-full bg-slate-100 border border-slate-200 flex items-center justify-center text-xs font-bold text-slate-700 shrink-0">
                        {f.name.split(' ').map((n) => n[0]).slice(0, 2).join('')}
                      </div>
                    </div>
                  </div>

                  <div className="mt-3 space-y-1.5 text-xs text-slate-600">
                    <div className="flex items-center gap-2">
                      <GraduationCap className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                      <span className="truncate">{f.qualifications}</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <Building2 className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                      <span>{f.department}</span>
                    </div>
                  </div>

                  {/* Skills tags — read-only on the card; edit from the profile */}
                  <div className="mt-4 pt-3 border-t border-slate-100">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
                        Skills &amp; Expertise:
                      </span>
                      <span className="text-[11px] text-slate-400 group-hover:text-blue-600 flex items-center gap-0.5 font-medium transition-colors">
                        Full profile
                        <ChevronRight className="w-3 h-3" />
                      </span>
                    </div>

                    <div className="flex flex-wrap gap-1.5">
                      {f.skills.length > 0 ? (
                        f.skills.map((skill) => (
                          <span
                            key={skill.id}
                            className={`text-xs px-2.5 py-0.5 rounded-md border font-medium ${
                              selectedSkill === skill.name
                                ? 'bg-amber-50 text-amber-900 border-amber-300 font-semibold'
                                : 'bg-slate-50 text-slate-700 border-slate-200'
                            }`}
                          >
                            {skill.name}
                          </span>
                        ))
                      ) : (
                        <span className="text-xs text-slate-400 italic">No skills recorded</span>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Add Faculty Modal */}
      {isAddModalOpen && (
        <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-lg border border-border shadow-xl max-w-md w-full p-6 relative animate-in fade-in zoom-in-95 duration-150">
            <button
              type="button"
              onClick={() => setIsAddModalOpen(false)}
              className="absolute top-4 right-4 text-slate-400 hover:text-slate-600 p-1"
            >
              <X className="w-4 h-4" />
            </button>
            <h3 className="font-sans text-xl font-bold text-slate-900 mb-1">Add Faculty Member</h3>
            <p className="text-xs text-slate-500 mb-5">Create a faculty profile with normalized skill tags.</p>

            <form onSubmit={handleCreateFaculty} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Full Name</label>
                <input
                  type="text"
                  required
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="e.g. Faculty 3"
                  className="w-full px-3 py-2 text-sm border border-border rounded-md focus:outline-hidden focus:ring-2 focus:ring-slate-900/20 focus:border-slate-900"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Institutional Email</label>
                <input
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="faculty3@iet.edu"
                  className="w-full px-3 py-2 text-sm border border-border rounded-md focus:outline-hidden focus:ring-2 focus:ring-slate-900/20 focus:border-slate-900"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">Designation</label>
                  <select
                    value={designation}
                    onChange={(e) => setDesignation(e.target.value)}
                    className="w-full px-3 py-2 text-sm border border-border rounded-md focus:outline-hidden focus:ring-2 focus:ring-slate-900/20 focus:border-slate-900"
                  >
                    <option value="Professor">Professor</option>
                    <option value="Associate Professor">Associate Professor</option>
                    <option value="Assistant Professor">Assistant Professor</option>
                    <option value="Adjunct Faculty">Adjunct Faculty</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">Department</label>
                  <input
                    type="text"
                    value={department}
                    onChange={(e) => setDepartment(e.target.value)}
                    className="w-full px-3 py-2 text-sm border border-border rounded-md focus:outline-hidden focus:ring-2 focus:ring-slate-900/20 focus:border-slate-900"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Qualifications</label>
                <input
                  type="text"
                  required
                  value={qualifications}
                  onChange={(e) => setQualifications(e.target.value)}
                  placeholder="e.g. Ph.D. in Computer Networks"
                  className="w-full px-3 py-2 text-sm border border-border rounded-md focus:outline-hidden focus:ring-2 focus:ring-slate-900/20 focus:border-slate-900"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1.5">
                  Skill Tags (Autosuggested &amp; Normalized)
                </label>
                <TagInput
                  tags={formSkillTags}
                  onChange={setFormSkillTags}
                  suggestions={existingSkillNames}
                  placeholder="Type skills (e.g. Machine Learning, Networks) and press Enter..."
                />
              </div>

              {addFacultyMutation.isError && (
                <div className="text-xs text-rose-600 bg-rose-50 p-2.5 rounded border border-rose-200">
                  {addFacultyMutation.error.message}
                </div>
              )}

              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setIsAddModalOpen(false)}
                  className="px-4 py-2 text-xs font-medium text-slate-600 hover:bg-slate-100 rounded-md transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={addFacultyMutation.isPending}
                  className="px-4 py-2 text-xs font-semibold bg-slate-900 text-white rounded-md hover:bg-slate-800 transition-colors disabled:opacity-50"
                >
                  {addFacultyMutation.isPending ? 'Saving...' : 'Save Faculty'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Bulk Faculty & Course Allocations Import Modal */}
      {isImportModalOpen && (
        <ImportSpreadsheetModal
          kind="allocations"
          onClose={() => setIsImportModalOpen(false)}
          onSuccess={() => {
            queryClient.invalidateQueries({ queryKey: ['faculty'] });
            queryClient.invalidateQueries({ queryKey: ['skills'] });
            queryClient.invalidateQueries({ queryKey: ['teaching-assignments'] });
            queryClient.invalidateQueries({ queryKey: ['faculty-ltp-load'] });
          }}
        />
      )}
    </div>
  );
}
