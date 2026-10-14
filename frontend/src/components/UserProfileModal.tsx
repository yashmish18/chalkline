import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../context/AuthContext';
import { FacultyProfileDto } from '../types/directory';
import {
  X,
  Building2,
  GraduationCap,
  Shield,
  LogOut
} from 'lucide-react';

interface UserProfileModalProps {
  onClose: () => void;
}

export function UserProfileModal({ onClose }: UserProfileModalProps) {
  const { user, logout } = useAuth();

  const { data: profile } = useQuery<FacultyProfileDto>({
    queryKey: ['faculty-me-profile', user?.id],
    queryFn: async () => {
      const res = await fetch('/api/faculty/me/profile', { credentials: 'include' });
      if (!res.ok) {
        // If not a faculty member or endpoint fails, return null
        return null as unknown as FacultyProfileDto;
      }
      return res.json();
    },
    enabled: !!user,
  });

  const initials = user?.name
    ? user.name
        .replace(/^(Dr|Prof|Mr|Ms|Mrs)\.?\s+/i, '')
        .split(' ')
        .filter(Boolean)
        .slice(0, 2)
        .map((n) => n[0])
        .join('')
        .toUpperCase()
    : 'U';

  const departmentName = profile?.department || '—';
  const designation = profile?.designation || (user?.role === 'COORDINATOR' ? 'Academic Coordinator' : 'Faculty Member');

  const handleLogout = async () => {
    try {
      await logout();
      onClose();
    } catch (err) {
      console.error('Logout error:', err);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-2xs animate-in fade-in">
      <div className="bg-white rounded-lg border border-border shadow-2xl max-w-lg w-full overflow-hidden">
        {/* Modal Header */}
        <div className="p-6 border-b border-border flex items-start justify-between bg-slate-50/70">
          <div className="flex items-center gap-4">
            <div className="w-14 h-14 rounded-full bg-slate-100 ring-2 ring-slate-200 text-slate-800 flex items-center justify-center font-bold text-lg shrink-0 shadow-inner">
              {initials}
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="font-sans text-lg font-bold text-slate-900 leading-tight">{user?.name}</h2>
                <span className="px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider bg-slate-900 text-white">
                  {user?.role}
                </span>
              </div>
              <p className="text-xs font-semibold text-slate-600 mt-0.5">{designation}</p>
              <p className="text-[11px] text-slate-500 font-mono mt-0.5">{user?.email}</p>
            </div>
          </div>

          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-md text-slate-400 hover:text-slate-700 hover:bg-slate-200 transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Modal Body */}
        <div className="p-6 space-y-5 text-xs">
          {/* Department & Identity Details */}
          <div className="grid grid-cols-2 gap-3 p-3.5 rounded-lg border border-border bg-slate-50/50">
            <div>
              <span className="text-[10px] uppercase font-semibold text-slate-500 tracking-wider">Department</span>
              <div className="font-semibold text-slate-900 flex items-center gap-1.5 mt-1">
                <Building2 className="w-3.5 h-3.5 text-slate-500" />
                <span>{departmentName}</span>
              </div>
            </div>

            <div>
              <span className="text-[10px] uppercase font-semibold text-slate-500 tracking-wider">Qualifications</span>
              <div className="font-semibold text-slate-900 flex items-center gap-1.5 mt-1">
                <GraduationCap className="w-3.5 h-3.5 text-slate-500" />
                <span>{profile?.qualification || 'PhD / Masters in Discipline'}</span>
              </div>
            </div>
          </div>

          {/* If Faculty: Teaching Load & Compliance Highlights */}
          {profile && profile.load && (
            <div className="space-y-3">
              <h3 className="text-xs font-bold uppercase tracking-wider text-slate-600">
                Academic Load & Deliverables Summary
              </h3>

              <div className="grid grid-cols-3 gap-2.5 text-center">
                <div className="p-3 rounded-lg border border-border bg-white shadow-2xs">
                  <div className="text-base font-bold font-mono text-slate-900">
                    {profile.load.totalHours}h
                  </div>
                  <div className="text-[10px] text-slate-500 mt-0.5">
                    Weekly Total {profile.load.pblHours ? `(${profile.load.pblHours}h PBL)` : ''}
                  </div>
                </div>

                <div className="p-3 rounded-lg border border-border bg-white shadow-2xs">
                  <div className="text-base font-bold font-mono text-slate-900">
                    {profile.courses.length}
                  </div>
                  <div className="text-[10px] text-slate-500 mt-0.5">Assigned Courses</div>
                </div>

                <div className="p-3 rounded-lg border border-border bg-white shadow-2xs">
                  <div className="text-base font-bold font-mono text-slate-900">
                    {profile.compliance?.complianceRate ?? 100}%
                  </div>
                  <div className="text-[10px] text-slate-500 mt-0.5">Compliance Rate</div>
                </div>
              </div>

              {profile.skills && profile.skills.length > 0 && (
                <div>
                  <span className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider">
                    Skills & Competencies
                  </span>
                  <div className="flex flex-wrap gap-1.5 mt-1.5">
                    {profile.skills.map((s) => (
                      <span
                        key={s.id}
                        className="px-2 py-0.5 rounded-full text-[10px] font-medium bg-slate-100 text-slate-700 border border-slate-200"
                      >
                        {s.name}
                      </span>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Account Security Information */}
          <div className="p-3.5 rounded-lg border border-border bg-slate-50/50 space-y-2">
            <div className="flex items-center gap-2 text-slate-800 font-bold">
              <Shield className="w-4 h-4 text-emerald-600" />
              <span>Authentication & Security</span>
            </div>
            <p className="text-[11px] text-slate-500">
              Identity authenticated via institutional directory. Role permissions are enforced server-side.
            </p>
          </div>
        </div>

        {/* Modal Footer */}
        <div className="p-4 border-t border-border flex items-center justify-between bg-slate-50">
          <button
            type="button"
            onClick={handleLogout}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold text-rose-700 bg-rose-50 hover:bg-rose-100 border border-rose-200 transition-colors"
          >
            <LogOut className="w-3.5 h-3.5" />
            <span>Sign Out</span>
          </button>

          <button
            type="button"
            onClick={onClose}
            className="px-4 py-1.5 text-xs font-bold text-white bg-slate-900 rounded-md hover:bg-slate-800 shadow-xs transition-colors"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
