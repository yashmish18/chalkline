import { useAuth } from '../context/AuthContext';
import { Users } from 'lucide-react';

export function DevUserSwitcher() {
  const { user, login } = useAuth();

  const demoAccounts = [
    { email: 'coordinator@iet.edu', name: 'Dr. Rajesh Sharma', role: 'COORDINATOR' },
    { email: 'amit.verma@iet.edu', name: 'Dr. Amit Verma', role: 'FACULTY' },
    { email: 'priya.nair@iet.edu', name: 'Prof. Priya Nair', role: 'FACULTY' },
  ];

  return (
    <div className="bg-slate-100 border-b border-slate-200 px-4 py-1.5 text-xs flex items-center justify-between text-slate-600">
      <div className="flex items-center gap-2">
        <Users className="w-3.5 h-3.5 text-slate-500" />
        <span className="font-semibold text-slate-700">Developer Role Switcher:</span>
      </div>
      <div className="flex items-center gap-2">
        {demoAccounts.map((account) => {
          const isCurrent = user?.email === account.email;
          return (
            <button
              key={account.email}
              type="button"
              onClick={() => login(account.email)}
              className={`px-2 py-0.5 rounded text-[11px] font-medium transition-all ${
                isCurrent
                  ? 'bg-primary text-white shadow-xs font-semibold'
                  : 'bg-white text-slate-700 hover:bg-slate-200 border border-slate-300'
              }`}
            >
              {account.name.split(' ')[1]} ({account.role.toLowerCase()})
            </button>
          );
        })}
      </div>
    </div>
  );
}
