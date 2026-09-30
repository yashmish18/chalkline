import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { FacultyDto, SkillDto } from '../types/directory';
import { useAuth } from '../context/AuthContext';
import { TagInput } from '../components/TagInput';
import { Search, Plus, GraduationCap, Building2, Tag, X, Edit3, Check } from 'lucide-react';

export function FacultyDirectoryView() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [selectedSkill, setSelectedSkill] = useState<string>('');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);

  // Form state
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [designation, setDesignation] = useState('Assistant Professor');
  const [qualifications, setQualifications] = useState('');
  const [department, setDepartment] = useState('Information Technology');
  const [formSkillTags, setFormSkillTags] = useState<string[]>([]);

  // Editing skills inline
  const [editingFacultyId, setEditingFacultyId] = useState<number | null>(null);
  const [editingSkills, setEditingSkills] = useState<string[]>([]);

  const { data: skills = [] } = useQuery<SkillDto[]>({
    queryKey: ['skills'],
    queryFn: async () => {
      const res = await fetch('/api/skills');
      if (!res.ok) throw new Error('Failed to fetch skills');
      return res.json();
    },
  });

  const { data: facultyList = [], isLoading } = useQuery<FacultyDto[]>({
    queryKey: ['faculty', selectedSkill],
    queryFn: async () => {
      const url = selectedSkill
        ? `/api/faculty?skill=${encodeURIComponent(selectedSkill)}`
        : '/api/faculty';
      const res = await fetch(url);
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

  const updateSkillsMutation = useMutation({
    mutationFn: async ({ facultyId, skills }: { facultyId: number; skills: string[] }) => {
      const res = await fetch(`/api/faculty/${facultyId}/skills-by-name`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ skills }),
      });
      if (!res.ok) throw new Error('Failed to update faculty skills');
      return res.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['faculty'] });
      queryClient.invalidateQueries({ queryKey: ['skills'] });
      setEditingFacultyId(null);
    }
  });

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

  const startEditSkills = (faculty: FacultyDto) => {
    setEditingFacultyId(faculty.id);
    setEditingSkills(faculty.skills.map((s) => s.name));
  };

  const saveEditSkills = (facultyId: number) => {
    updateSkillsMutation.mutate({ facultyId, skills: editingSkills });
  };

  const isCoordinator = user?.role === 'COORDINATOR';
  const existingSkillNames = skills.map((s) => s.name);

  return (
    <div className="space-y-6">
      {/* Header & Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="font-serif text-2xl font-bold text-slate-900 tracking-tight">Faculty Directory</h2>
          <p className="text-sm text-slate-600 mt-0.5">
            Academic qualifications, structured expertise tags, and department profiles.
          </p>
        </div>
        {isCoordinator && (
          <button
            type="button"
            onClick={() => setIsAddModalOpen(true)}
            className="inline-flex items-center gap-2 px-4 py-2 bg-primary text-white text-sm font-medium rounded-md hover:bg-primary-hover shadow-xs transition-colors self-start sm:self-auto"
          >
            <Plus className="w-4 h-4" />
            Add Faculty
          </button>
        )}
      </div>

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
              className="w-full pl-9 pr-4 py-2 text-sm bg-slate-50 border border-border rounded-md focus:bg-white focus:outline-hidden focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
            />
          </div>
          {selectedSkill && (
            <button
              type="button"
              onClick={() => setSelectedSkill('')}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-slate-600 bg-slate-100 hover:bg-slate-200 rounded-md transition-colors"
            >
              Clear filter: <span className="font-semibold text-primary">{selectedSkill}</span>
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
                ? 'bg-primary text-white border-primary font-semibold'
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
                    ? 'bg-primary text-white border-primary font-semibold'
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
          <h3 className="font-serif text-lg font-bold text-slate-800">No Faculty Found</h3>
          <p className="text-sm text-slate-500 max-w-sm mx-auto mt-1">
            {selectedSkill
              ? `No faculty members currently match the skill "${selectedSkill}".`
              : 'No faculty profiles found matching your search query.'}
          </p>
          {selectedSkill && (
            <button
              type="button"
              onClick={() => setSelectedSkill('')}
              className="mt-4 px-3 py-1.5 text-xs font-medium text-primary bg-primary/10 rounded-md hover:bg-primary/20 transition-colors"
            >
              Reset Skill Filter
            </button>
          )}
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {filteredFaculty.map((f) => {
            const isEditing = editingFacultyId === f.id;
            return (
              <div
                key={f.id}
                className="bg-white rounded-lg border border-border p-5 shadow-xs hover:border-slate-300 transition-all flex flex-col justify-between"
              >
                <div>
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <h3 className="font-serif text-lg font-bold text-slate-900 leading-snug">{f.name}</h3>
                      <p className="text-xs font-semibold text-primary mt-0.5">{f.designation}</p>
                    </div>
                    <div className="h-9 w-9 rounded-full bg-slate-100 border border-slate-200 flex items-center justify-center text-xs font-bold text-slate-700 shrink-0">
                      {f.name.split(' ').map((n) => n[0]).slice(0, 2).join('')}
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

                  {/* Skills tags */}
                  <div className="mt-4 pt-3 border-t border-slate-100">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
                        Skills &amp; Expertise:
                      </span>
                      {isCoordinator && !isEditing && (
                        <button
                          type="button"
                          onClick={() => startEditSkills(f)}
                          className="text-[11px] text-primary hover:underline flex items-center gap-1 font-medium"
                        >
                          <Edit3 className="w-3 h-3" /> Edit Tags
                        </button>
                      )}
                    </div>

                    {isEditing ? (
                      <div className="space-y-2">
                        <TagInput
                          tags={editingSkills}
                          onChange={setEditingSkills}
                          suggestions={existingSkillNames}
                          placeholder="Type skill & press Enter..."
                        />
                        <div className="flex items-center justify-end gap-2">
                          <button
                            type="button"
                            onClick={() => setEditingFacultyId(null)}
                            className="px-2.5 py-1 text-xs text-slate-600 hover:bg-slate-100 rounded"
                          >
                            Cancel
                          </button>
                          <button
                            type="button"
                            onClick={() => saveEditSkills(f.id)}
                            disabled={updateSkillsMutation.isPending}
                            className="px-3 py-1 text-xs bg-primary text-white rounded font-medium flex items-center gap-1 shadow-xs"
                          >
                            <Check className="w-3 h-3" /> Save
                          </button>
                        </div>
                      </div>
                    ) : (
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
                    )}
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
            <h3 className="font-serif text-xl font-bold text-slate-900 mb-1">Add Faculty Member</h3>
            <p className="text-xs text-slate-500 mb-5">Create a faculty profile with normalized skill tags.</p>

            <form onSubmit={handleCreateFaculty} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Full Name</label>
                <input
                  type="text"
                  required
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="e.g. Dr. Ramesh Kumar"
                  className="w-full px-3 py-2 text-sm border border-border rounded-md focus:outline-hidden focus:ring-2 focus:ring-primary/20 focus:border-primary"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Institutional Email</label>
                <input
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="ramesh.kumar@iet.edu"
                  className="w-full px-3 py-2 text-sm border border-border rounded-md focus:outline-hidden focus:ring-2 focus:ring-primary/20 focus:border-primary"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">Designation</label>
                  <select
                    value={designation}
                    onChange={(e) => setDesignation(e.target.value)}
                    className="w-full px-3 py-2 text-sm border border-border rounded-md focus:outline-hidden focus:ring-2 focus:ring-primary/20 focus:border-primary"
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
                    className="w-full px-3 py-2 text-sm border border-border rounded-md focus:outline-hidden focus:ring-2 focus:ring-primary/20 focus:border-primary"
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
                  className="w-full px-3 py-2 text-sm border border-border rounded-md focus:outline-hidden focus:ring-2 focus:ring-primary/20 focus:border-primary"
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
                  className="px-4 py-2 text-xs font-medium bg-primary text-white rounded-md hover:bg-primary-hover transition-colors disabled:opacity-50"
                >
                  {addFacultyMutation.isPending ? 'Saving...' : 'Save Faculty'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
