import React from 'react';
import { useAuth } from '../context/AuthContext';
import {
  LayoutDashboard,
  CalendarCheck,
  BookOpen,
  Calendar,
  Users,
  CheckSquare,
  ShieldCheck,
  ChevronLeft,
  ChevronRight
} from 'lucide-react';

export interface NavItem {
  id: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  role?: 'COORDINATOR' | 'FACULTY';
  badgeCount?: number;
}

interface SidebarProps {
  currentTab: string;
  onSelectTab: (tabId: string) => void;
  isCollapsed: boolean;
  onToggleCollapse: () => void;
  activeTermName?: string;
  unreadNotificationsCount?: number;
}

export function Sidebar({
  currentTab,
  onSelectTab,
  isCollapsed,
  onToggleCollapse,
  activeTermName = 'Odd 2026-27',
  unreadNotificationsCount = 0,
}: SidebarProps) {
  const { user } = useAuth();
  const isCoordinator = user?.role === 'COORDINATOR';

  const coordinatorItems: NavItem[] = [
    { id: 'overview', label: 'Overview', icon: LayoutDashboard },
    { id: 'faculty', label: 'Faculty Directory', icon: Users },
    { id: 'academics', label: 'Academics & Matrix', icon: BookOpen },
    { id: 'compliance', label: 'Compliance Tracking', icon: CheckSquare },
    { id: 'deadlines', label: 'Deadlines & Cycles', icon: Calendar },
    { id: 'audit', label: 'Audit Logs', icon: ShieldCheck },
  ];

  const facultyItems: NavItem[] = [
    { id: 'today', label: 'Today (Log Class)', icon: CalendarCheck },
    { id: 'my-courses', label: 'My Courses & Syllabus', icon: BookOpen },
    {
      id: 'deadlines',
      label: 'Deadlines & Submissions',
      icon: Calendar,
      badgeCount: unreadNotificationsCount > 0 ? unreadNotificationsCount : undefined,
    },
    { id: 'faculty', label: 'Faculty Directory', icon: Users },
  ];

  const items = isCoordinator ? coordinatorItems : facultyItems;

  return (
    <aside
      className={`fixed inset-y-0 left-0 z-40 bg-white border-r border-border transition-all duration-200 flex flex-col justify-between ${
        isCollapsed ? 'w-18' : 'w-64'
      }`}
    >
      {/* Top Header */}
      <div>
        <div className="h-16 px-4 border-b border-border flex items-center justify-between">
          {!isCollapsed && (
            <div className="flex items-center gap-2.5 overflow-hidden">
              <div className="w-8 h-8 rounded-md bg-primary text-white flex items-center justify-center font-serif font-bold text-sm shrink-0 shadow-xs">
                IET
              </div>
              <div className="truncate">
                <div className="text-xs font-bold text-slate-900 tracking-tight leading-tight uppercase font-sans">
                  Academic Portal
                </div>
                <div className="text-[10px] text-primary font-medium tracking-wide flex items-center gap-1">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 inline-block"></span>
                  {activeTermName}
                </div>
              </div>
            </div>
          )}

          {isCollapsed && (
            <div className="w-8 h-8 mx-auto rounded-md bg-primary text-white flex items-center justify-center font-serif font-bold text-xs">
              IET
            </div>
          )}

          <button
            type="button"
            onClick={onToggleCollapse}
            className="p-1 rounded-md text-slate-500 hover:text-slate-800 hover:bg-slate-100 transition-colors shrink-0"
            title={isCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          >
            {isCollapsed ? <ChevronRight className="w-4 h-4" /> : <ChevronLeft className="w-4 h-4" />}
          </button>
        </div>

        {/* Navigation Items */}
        <div className="p-3 space-y-1">
          <div className="px-2 py-1.5 text-[10px] font-bold text-slate-500 uppercase tracking-wider">
            {!isCollapsed && (isCoordinator ? 'Coordinator Suite' : 'Faculty Workspace')}
          </div>

          {items.map((item) => {
            const Icon = item.icon;
            const isActive = currentTab === item.id;

            return (
              <button
                key={item.id}
                type="button"
                onClick={() => onSelectTab(item.id)}
                className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-md text-xs font-medium transition-all ${
                  isActive
                    ? 'bg-primary text-white shadow-xs font-semibold'
                    : 'text-slate-700 hover:text-slate-900 hover:bg-slate-100'
                } ${isCollapsed ? 'justify-center px-0' : ''}`}
                title={isCollapsed ? item.label : undefined}
              >
                <Icon className={`w-4 h-4 shrink-0 ${isActive ? 'text-white' : 'text-slate-500'}`} />
                {!isCollapsed && <span className="truncate">{item.label}</span>}

                {!isCollapsed && item.badgeCount !== undefined && (
                  <span
                    className={`ml-auto px-1.5 py-0.5 text-[10px] font-bold rounded-full ${
                      isActive ? 'bg-amber-400 text-slate-950' : 'bg-amber-100 text-amber-800'
                    }`}
                  >
                    {item.badgeCount}
                  </span>
                )}
              </button>
            );
          })}
        </div>
      </div>

      {/* User Footer Profile */}
      <div className="p-3 border-t border-border bg-slate-50/60">
        <div className={`flex items-center ${isCollapsed ? 'justify-center' : 'gap-2.5'}`}>
          <div className="w-8 h-8 rounded-full bg-slate-200 border border-slate-300 text-slate-700 flex items-center justify-center font-bold text-xs shrink-0">
            {user?.name ? user.name.charAt(0) : 'U'}
          </div>

          {!isCollapsed && (
            <div className="truncate flex-1 min-w-0">
              <div className="text-xs font-semibold text-slate-900 truncate">{user?.name || 'Guest'}</div>
              <div className="flex items-center gap-1.5 mt-0.5">
                <span className="text-[10px] text-slate-500 truncate">{user?.role || 'ANONYMOUS'}</span>
                <span className="w-1 h-1 rounded-full bg-slate-400"></span>
                <span className="text-[10px] text-primary font-medium">IT Dept</span>
              </div>
            </div>
          )}
        </div>
      </div>
    </aside>
  );
}
