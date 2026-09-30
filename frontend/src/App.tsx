import { useState, useEffect } from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
import { Sidebar } from './components/Sidebar';
import { NotificationCenter } from './components/NotificationCenter';
import { DevUserSwitcher } from './components/DevUserSwitcher';
import { FacultyDirectoryView } from './views/FacultyDirectoryView';
import { TeachingAllocationsView } from './views/TeachingAllocationsView';
import { DeadlinesView } from './views/DeadlinesView';
import { TrackingDashboardView } from './views/TrackingDashboardView';
import { CoordinatorOverviewView } from './views/CoordinatorOverviewView';
import { TodayView } from './views/TodayView';
import { MyCoursesView } from './views/MyCoursesView';
import { TimetableView } from './views/TimetableView';

function MainAppShell() {
  const { user } = useAuth();
  const isCoordinator = user?.role === 'COORDINATOR';

  const [activeTab, setActiveTab] = useState<string>(isCoordinator ? 'overview' : 'today');
  const [isCollapsed, setIsCollapsed] = useState(false);
  const [activeTermName, setActiveTermName] = useState('Odd 2026-27');
  const [unreadCount, setUnreadCount] = useState(0);

  // Sync default tab whenever the active role changes
  useEffect(() => {
    setActiveTab(isCoordinator ? 'overview' : 'today');
  }, [user?.role]);

  // Fetch active term name
  useEffect(() => {
    fetch('/api/terms/active')
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => {
        if (data?.name) {
          setActiveTermName(data.name);
        }
      })
      .catch(() => {});
  }, []);

  // Fetch unread count for badge
  useEffect(() => {
    if (!user) return;
    const fetchUnread = () => {
      fetch('/api/notifications/unread-count')
        .then((res) => (res.ok ? res.json() : null))
        .then((data) => {
          if (data && typeof data.unreadCount === 'number') {
            setUnreadCount(data.unreadCount);
          }
        })
        .catch(() => {});
    };
    fetchUnread();
    const interval = setInterval(fetchUnread, 45000);
    return () => clearInterval(interval);
  }, [user]);

  const getSectionTitle = () => {
    switch (activeTab) {
      case 'overview':
        return 'Department Overview & Compliance Analytics';
      case 'today':
        return "Today's Academic Session Log";
      case 'timetable':
        return isCoordinator ? 'Department Master Timetable & Class Schedule' : 'My Weekly Class Schedule & Timetable';
      case 'my-courses':
        return 'My Allocated Courses & Syllabus Pacing';
      case 'deadlines':
        return isCoordinator ? 'Administrative Deadlines & Cycles' : 'Assigned Deadlines & Deliverables';
      case 'faculty':
        return 'Department Faculty Directory & Skills';
      case 'academics':
        return 'Academic Curriculum & Teaching Allocation Matrix';
      case 'compliance':
        return 'Submissions Compliance Audit Roster';
      case 'audit':
        return 'Departmental Security & Audit Trail';
      default:
        return 'Academic Workspace';
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 font-sans flex text-slate-800 antialiased selection:bg-primary selection:text-white">
      {/* Persistent Left Sidebar */}
      <Sidebar
        currentTab={activeTab}
        onSelectTab={setActiveTab}
        isCollapsed={isCollapsed}
        onToggleCollapse={() => setIsCollapsed(!isCollapsed)}
        activeTermName={activeTermName}
        unreadNotificationsCount={unreadCount}
      />

      {/* Main Content Area */}
      <div className={`flex-1 flex flex-col min-w-0 transition-all duration-200 ${isCollapsed ? 'pl-18' : 'pl-64'}`}>
        {/* Top Utility Header */}
        <header className="h-16 bg-white border-b border-border sticky top-0 z-30 px-6 flex items-center justify-between shadow-2xs">
          <div className="flex items-center gap-3">
            <h1 className="font-serif text-lg font-bold text-slate-900 tracking-tight">{getSectionTitle()}</h1>
            <span className="hidden sm:inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold bg-slate-100 text-slate-700 border border-slate-200">
              {activeTermName}
            </span>
          </div>

          <div className="flex items-center gap-3">
            {/* Top Notification Bell */}
            <NotificationCenter />

            {/* Quick Dev Switcher */}
            <DevUserSwitcher />
          </div>
        </header>

        {/* View Content */}
        <main className="flex-1 max-w-7xl w-full mx-auto p-6 md:p-8">
          {activeTab === 'overview' && isCoordinator && <CoordinatorOverviewView />}
          {activeTab === 'compliance' && isCoordinator && <TrackingDashboardView />}
          {activeTab === 'audit' && isCoordinator && <TrackingDashboardView />}
          {activeTab === 'academics' && <TeachingAllocationsView />}
          {activeTab === 'faculty' && <FacultyDirectoryView />}
          {activeTab === 'deadlines' && <DeadlinesView />}
          {activeTab === 'today' && <TodayView onNavigate={(t) => setActiveTab(t)} />}
          {activeTab === 'timetable' && <TimetableView />}
          {activeTab === 'my-courses' && <MyCoursesView />}
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
