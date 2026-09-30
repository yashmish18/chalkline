import { LogOut, UserCheck } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { NotificationCenter } from './NotificationCenter';

interface NavbarProps {
  onNavigate?: (view: string) => void;
}

export function Navbar({ onNavigate }: NavbarProps) {
  const { user, login, logout } = useAuth();

  return (
    <header className="border-b border-border bg-white sticky top-0 z-30 shadow-sm">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
        {/* Left: Brand / Institution */}
        <div className="flex items-center gap-3">
          <div className="h-10 w-10 rounded-md bg-primary flex items-center justify-center text-white font-sans font-bold text-xl shadow-inner">
            IET
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="font-sans text-lg font-bold text-slate-900 tracking-tight">Faculty Dashboard</span>
              <span className="text-[10px] font-semibold uppercase tracking-wider px-2 py-0.5 rounded-full bg-slate-100 text-slate-700 border border-slate-200">
                Department Portal
              </span>
            </div>
            <p className="text-xs text-slate-500 font-sans">Institute of Engineering & Technology</p>
          </div>
        </div>

        {/* Right: Actions, Notifications & Profile */}
        <div className="flex items-center gap-3">
          {user ? (
            <>
              {/* Notification Center */}
              <NotificationCenter onNavigate={onNavigate} />

              {/* User Pill */}
              <div className="flex items-center gap-2 pl-2 border-l border-slate-200">
                <div className="h-8 w-8 rounded-full bg-slate-100 border border-slate-300 flex items-center justify-center text-slate-700 font-semibold text-xs">
                  {user.name.split(' ').map((n) => n[0]).slice(0, 2).join('')}
                </div>
                <div className="hidden sm:block text-left">
                  <div className="text-xs font-semibold text-slate-900 leading-tight">{user.name}</div>
                  <div className="text-[11px] text-slate-500">{user.email}</div>
                </div>
                <span
                  className={`text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-full border ${
                    user.role === 'COORDINATOR'
                      ? 'bg-amber-50 text-amber-800 border-amber-300'
                      : 'bg-blue-50 text-blue-800 border-blue-300'
                  }`}
                >
                  {user.role}
                </span>

                {/* Logout Button */}
                <button
                  type="button"
                  onClick={() => logout()}
                  title="Sign out"
                  className="p-1.5 text-slate-400 hover:text-rose-600 rounded-md hover:bg-rose-50 transition-colors ml-1"
                >
                  <LogOut className="w-4 h-4" />
                </button>
              </div>
            </>
          ) : (
            /* Quick Dev Login Bar when unauthenticated */
            <div className="flex items-center gap-2">
              <span className="text-xs text-slate-500 flex items-center gap-1">
                <UserCheck className="w-3.5 h-3.5 text-slate-400" /> Sign in as:
              </span>
              <button
                type="button"
                onClick={() => login('coordinator@iet.edu')}
                className="px-3 py-1.5 text-xs font-medium bg-primary text-white rounded-md hover:bg-primary-hover shadow-sm transition-colors"
              >
                Coordinator
              </button>
              <button
                type="button"
                onClick={() => login('amit.verma@iet.edu')}
                className="px-3 py-1.5 text-xs font-medium bg-white text-slate-700 border border-slate-300 rounded-md hover:bg-slate-50 transition-colors"
              >
                Faculty
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
