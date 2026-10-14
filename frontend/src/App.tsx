import { useState, useEffect } from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
import { Sidebar } from './components/Sidebar';
import { LoginScreen } from './components/LoginScreen';
import { CoordinatorAccessScreen } from './components/CoordinatorAccessScreen';
import { pendingSetupToken } from './lib/coordinatorSetup';
import { NotificationCenter } from './components/NotificationCenter';
import { DevUserSwitcher } from './components/DevUserSwitcher';
import { HeaderClock } from './components/HeaderClock';
import { FacultyDirectoryView } from './views/FacultyDirectoryView';
import { TeachingAllocationsView } from './views/TeachingAllocationsView';
import { DeadlinesView } from './views/DeadlinesView';
import { TrackingDashboardView } from './views/TrackingDashboardView';
import { SubmissionsView } from './views/SubmissionsView';
import { CoordinatorOverviewView } from './views/CoordinatorOverviewView';
import { TodayView } from './views/TodayView';
import { MyCoursesView } from './views/MyCoursesView';
import { TimetableView } from './views/TimetableView';
import { SemesterReportView } from './views/SemesterReportView';
import { FacultyReportBody } from './components/ReportPanels';
import { useAppConfig } from './lib/appConfig';

function MainAppShell() {
  const { user, isLoading } = useAuth();

  if (isLoading) {
    return <BootScreen />;
  }

  if (!user) {
    return <LoginScreen />;
  }

  // Opened the emailed coordinator link: choose the coordinator password first.
  const setupToken = pendingSetupToken();
  if (setupToken) {
    return <CoordinatorAccessScreen mode="setup" token={setupToken} />;
  }
  if (user.role === 'COORDINATOR' && user.coordinatorAccess && user.coordinatorAccess !== 'UNLOCKED') {
    return <CoordinatorAccessScreen mode={user.coordinatorAccess === 'LOCKED' ? 'unlock' : 'awaiting'} />;
  }

  return <DashboardShell />;
}

/** Shown while the session resolves, so the login card never flashes for a signed-in user. */
function BootScreen() {
  return (
    <div className="min-h-screen bg-[var(--paper)] flex items-center justify-center">
      <span
        className="w-5 h-5 rounded-full border-2 border-[#E6E4DD] border-t-[#0F5C5C] animate-spin"
        role="status"
        aria-label="Loading workspace"
      />
    </div>
  );
}

function DashboardShell() {
  const { user } = useAuth();
  const appConfig = useAppConfig();
  const isCoordinator = user?.role === 'COORDINATOR';

  const [activeTab, setActiveTab] = useState<string>(isCoordinator ? 'overview' : 'today');
  const [isCollapsed, setIsCollapsed] = useState(false);

  // Sync default tab whenever the active role changes
  useEffect(() => {
    setActiveTab(isCoordinator ? 'overview' : 'today');
  }, [user?.role]);

  const getSectionTitle = () => {
    switch (activeTab) {
      case 'overview':
        return 'Overview';
      case 'today':
        return "Today's Classes";
      case 'timetable':
        return 'Timetable';
      case 'my-courses':
        return 'My Courses';
      case 'deadlines':
        return 'Deadlines';
      case 'faculty':
        return 'Faculty Directory';
      case 'submissions':
        return 'Submissions';
      case 'academics':
        return 'Course Allocations';
      case 'my-report':
        return 'My report';
      case 'report':
        return 'Semester report';
      case 'compliance':
        return 'Compliance Tracking';
      default:
        return 'Academic Portal';
    }
  };

  return (
    <div className="min-h-screen bg-[var(--paper)] font-sans flex text-slate-900 antialiased">
      {/* Persistent Left Sidebar */}
      <Sidebar
        currentTab={activeTab}
        onSelectTab={setActiveTab}
        isCollapsed={isCollapsed}
        onToggleCollapse={() => setIsCollapsed(!isCollapsed)}
      />

      {/* Main Content Area */}
      <div className={`flex-1 flex flex-col min-w-0 transition-all duration-200 print:pl-0 ${isCollapsed ? 'pl-[72px]' : 'pl-64'}`}>
        {/* Top Utility Header */}
        <header className="print:hidden h-16 bg-white/90 backdrop-blur border-b border-slate-200 sticky top-0 z-30 px-6 md:px-8 flex items-center justify-between">
          <h1 className="font-display text-[22px] font-medium text-slate-900">{getSectionTitle()}</h1>

          <div className="flex items-center gap-3">
            {/* Live Clock */}
            <HeaderClock />

            {/* Top Notification Bell */}
            <NotificationCenter />

            {/* Quick Dev Switcher */}
            {appConfig.devLogin && <DevUserSwitcher />}
          </div>
        </header>

        {/* View Content — keyed by user so switching account remounts and re-fetches for them. */}
        <main className="flex-1 max-w-7xl w-full mx-auto p-6 md:p-8 print:p-0">
          <div key={user?.id ?? 'anonymous'} className="contents">
          {activeTab === 'overview' && isCoordinator && <CoordinatorOverviewView />}
          {activeTab === 'submissions' && isCoordinator && <SubmissionsView />}
          {activeTab === 'compliance' && isCoordinator && <TrackingDashboardView />}
          {activeTab === 'report' && isCoordinator && <SemesterReportView />}
          {activeTab === 'my-report' && !isCoordinator && <FacultyReportBody self />}
          {activeTab === 'academics' && <TeachingAllocationsView />}
          {activeTab === 'faculty' && <FacultyDirectoryView />}
          {activeTab === 'deadlines' && <DeadlinesView />}
          {activeTab === 'today' && <TodayView onNavigate={(t) => setActiveTab(t)} />}
          {activeTab === 'timetable' && <TimetableView />}
          {activeTab === 'my-courses' && <MyCoursesView />}
          </div>
        </main>
      </div>
    </div>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <MainAppShell />
    </AuthProvider>
  );
}
