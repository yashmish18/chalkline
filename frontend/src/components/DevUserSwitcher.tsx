import { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { Users, LogOut } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { FacultyDto } from '../types/directory';

export function DevUserSwitcher() {
  const { user, login, logout, bypassLogin } = useAuth();
  const [isSigningOut, setIsSigningOut] = useState(false);

  const { data: facultyList = [] } = useQuery<FacultyDto[]>({
    queryKey: ['faculty-list-switcher'],
    queryFn: async () => {
      const res = await fetch('/api/faculty', { credentials: 'include' });
      if (!res.ok) return [];
      return res.json();
    },
    staleTime: 30000,
  });

  const handleLogout = async () => {
    setIsSigningOut(true);
    try {
      await logout(); // Session clears → fetchMe returns 401 → LoginScreen renders
    } finally {
      setIsSigningOut(false);
    }
  };

  /**
   * The account email the server actually created for a faculty member. Deriving it from the
   * name was fragile (e.g. "(Visiting Faculty)" suffixes), which broke the switch-to-faculty flow.
   */
  const accountEmail = (faculty: FacultyDto): string => {
    const email = (faculty.email ?? '').trim();
    return email;
  };

  const dynamicAccounts = [
    { email: 'coordinator@iet.edu', name: 'Coordinator', role: 'COORDINATOR' },
    ...facultyList
      .filter(f => !f.name.toLowerCase().includes('coordinator') && accountEmail(f).length > 0)
      .map(f => ({ email: accountEmail(f), name: f.name, role: 'FACULTY' })),
  ];

  return (
    <div className="flex items-center gap-1.5 bg-[#F6F5F1] p-1 rounded-lg border border-[#E6E4DD] text-xs">
      <div className="flex items-center gap-1.5 px-2 text-[#6B6F76] font-medium text-[11px] hidden md:flex">
        <Users className="w-3.5 h-3.5 text-[#0B4747]" />
        <span>Switch to:</span>
      </div>

      <button
        type="button"
        onClick={() => login('coordinator@iet.edu')}
        className={`px-2.5 py-1 rounded-md text-[11px] font-medium transition-all ${
          user?.role === 'COORDINATOR'
            ? 'bg-[#0F5C5C]/20 text-[#0B4747] font-semibold border border-[#0F5C5C]/40'
            : 'text-[#6B6F76] hover:text-[#0B4747]'
        }`}
      >
        Coordinator
      </button>

      <select
        value={user?.role === 'FACULTY' ? user.email : ''}
        onChange={(e) => {
          if (e.target.value) login(e.target.value);
        }}
        title="Switch to a faculty member's account"
        aria-label="Switch faculty account"
        className="max-w-[200px] bg-white border border-[#E6E4DD] rounded px-2 py-1 text-[11px] text-[#16181D] focus:outline-none focus:border-[#0F5C5C]"
      >
        <option value="" disabled>
          {user?.role === 'FACULTY' ? 'Choose faculty…' : `Choose faculty (${dynamicAccounts.length - 1})`}
        </option>
        {dynamicAccounts.filter(a => a.role === 'FACULTY').map((account) => (
          <option key={account.email} value={account.email}>
            {account.name}
          </option>
        ))}
      </select>

      <button
        type="button"
        onClick={() => bypassLogin()}
        title="Instant coordinator sign-in (bypass)"
        className="ml-1 px-2 py-1 rounded-md text-[10px] font-bold uppercase tracking-wider text-[#0B4747] hover:bg-[#0F5C5C]/15 transition-colors"
      >
        Bypass
      </button>
      <button
        type="button"
        onClick={handleLogout}
        disabled={isSigningOut}
        title="Sign out and return to the login screen"
        className="ml-1 p-1.5 rounded-md text-[#6B6F76] hover:text-[#0B4747] hover:bg-white/5 transition-colors disabled:opacity-50"
      >
        <LogOut className="w-3.5 h-3.5" />
      </button>
    </div>
  );
}
