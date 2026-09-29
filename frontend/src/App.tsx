import { AuthProvider, useAuth } from './context/AuthContext';
import { Navbar } from './components/Navbar';
import { DevUserSwitcher } from './components/DevUserSwitcher';
import { CheckCircle2, ShieldAlert } from 'lucide-react';

function DashboardContent() {
  const { user, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="flex justify-center items-center py-20 text-slate-500 text-sm">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary mr-3"></div>
        Loading dashboard session...
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Role Banner */}
      <div className="bg-white rounded-lg border border-border p-6 shadow-xs flex items-start justify-between">
        <div>
          <h2 className="font-serif text-2xl font-bold text-slate-900 tracking-tight">
            {user ? `Welcome back, ${user.name}` : 'Welcome to IET Faculty Dashboard'}
          </h2>
          <p className="text-sm text-slate-600 mt-1">
            {user?.role === 'COORDINATOR'
              ? 'Department Coordinator View: Manage faculty allocations, monitor deadlines, and audit submission compliance.'
              : user?.role === 'FACULTY'
              ? 'Faculty Academic View: Track assigned courses, upcoming deadlines, and submission statuses.'
              : 'Sign in to access your course deadlines and compliance submissions.'}
          </p>
        </div>
        {user && (
          <div className="flex items-center gap-1.5 px-3 py-1 rounded-full bg-emerald-50 text-emerald-800 border border-emerald-200 text-xs font-semibold">
            <CheckCircle2 className="w-4 h-4 text-emerald-600" />
            Session Active ({user.role})
          </div>
        )}
      </div>

      {!user && (
        <div className="bg-amber-50 rounded-lg border border-amber-200 p-6 text-amber-900 flex items-start gap-4">
          <ShieldAlert className="w-6 h-6 text-amber-600 shrink-0 mt-0.5" />
          <div>
            <h3 className="text-sm font-bold text-amber-900">Authentication Required</h3>
            <p className="text-xs text-amber-800 mt-1">
              Please choose a role from the developer switcher above to preview coordinator or faculty views.
            </p>
          </div>
        </div>
      )}
    </div>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <div className="min-h-screen bg-slate-50 flex flex-col font-sans">
        <DevUserSwitcher />
        <Navbar />
        <main className="flex-1 max-w-7xl w-full mx-auto p-4 sm:p-6 lg:p-8">
          <DashboardContent />
        </main>
      </div>
    </AuthProvider>
  );
}
