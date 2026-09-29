import { useState } from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
import { Navbar } from './components/Navbar';
import { DevUserSwitcher } from './components/DevUserSwitcher';
import { FacultyDirectoryView } from './views/FacultyDirectoryView';
import { TeachingAllocationsView } from './views/TeachingAllocationsView';
import { CheckCircle2, ShieldAlert, LayoutDashboard, Users, BookOpen } from 'lucide-react';

function DashboardHome() {
  const { user } = useAuth();

  return (
    <div className="space-y-6">
      {/* Welcome Banner */}
      <div className="bg-white rounded-lg border border-border p-6 shadow-xs flex items-start justify-between">
        <div>
          <h2 className="font-serif text-2xl font-bold text-slate-900 tracking-tight">
            {user ? `Welcome back, ${user.name}` : 'Welcome to IET Faculty Dashboard'}
          </h2>
          <p className="text-sm text-slate-600 mt-1">
            {user?.role === 'COORDINATOR'
              ? 'Department Coordinator Workspace: Manage faculty allocations, monitor deadlines, and audit submission compliance.'
              : user?.role === 'FACULTY'
              ? 'Faculty Academic Workspace: Track assigned courses, upcoming deadlines, and submission statuses.'
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

      {/* Quick summary cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
        <div className="bg-white rounded-lg border border-border p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Faculty Members</span>
            <Users className="w-4 h-4 text-primary" />
          </div>
          <div className="text-2xl font-bold font-serif text-slate-900 mt-2">3 Profiles</div>
          <p className="text-xs text-slate-500 mt-1">Structured skill-tagged faculty roster</p>
        </div>

        <div className="bg-white rounded-lg border border-border p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Active Offerings</span>
            <BookOpen className="w-4 h-4 text-secondary" />
          </div>
          <div className="text-2xl font-bold font-serif text-slate-900 mt-2">4 Courses</div>
          <p className="text-xs text-slate-500 mt-1">Semester 5 offerings (2026-2027)</p>
        </div>

        <div className="bg-white rounded-lg border border-border p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Allocations</span>
            <CheckCircle2 className="w-4 h-4 text-emerald-600" />
          </div>
          <div className="text-2xl font-bold font-serif text-slate-900 mt-2">4 Assigned</div>
          <p className="text-xs text-slate-500 mt-1">Theory &amp; Lab components mapped</p>
        </div>
      </div>
    </div>
  );
}

function MainNavigation() {
  const [activeTab, setActiveTab] = useState<'dashboard' | 'directory' | 'allocations'>('dashboard');

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col font-sans">
      <DevUserSwitcher />
      <Navbar />

      {/* Sub-header navigation tabs */}
      <div className="bg-white border-b border-border shadow-2xs">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex items-center gap-6">
          <button
            type="button"
            onClick={() => setActiveTab('dashboard')}
            className={`flex items-center gap-2 py-3.5 text-xs font-semibold border-b-2 transition-all ${
              activeTab === 'dashboard'
                ? 'border-primary text-primary'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <LayoutDashboard className="w-4 h-4" />
            Overview
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('directory')}
            className={`flex items-center gap-2 py-3.5 text-xs font-semibold border-b-2 transition-all ${
              activeTab === 'directory'
                ? 'border-primary text-primary'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <Users className="w-4 h-4" />
            Faculty Directory
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('allocations')}
            className={`flex items-center gap-2 py-3.5 text-xs font-semibold border-b-2 transition-all ${
              activeTab === 'allocations'
                ? 'border-primary text-primary'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <BookOpen className="w-4 h-4" />
            Teaching Allocations
          </button>
        </div>
      </div>

      <main className="flex-1 max-w-7xl w-full mx-auto p-4 sm:p-6 lg:p-8">
        {activeTab === 'dashboard' && <DashboardHome />}
        {activeTab === 'directory' && <FacultyDirectoryView />}
        {activeTab === 'allocations' && <TeachingAllocationsView />}
      </main>
    </div>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <MainNavigation />
    </AuthProvider>
  );
}
