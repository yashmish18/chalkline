import React from 'react';
import { useAuth } from '../context/AuthContext';
import {
  LayoutDashboard,
  CalendarCheck,
  CalendarDays,
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
  activeTermName = 'Semester 1 (2026-27)',
  unreadNotificationsCount = 0,
}: SidebarProps) {
  const { user } = useAuth();
  const isCoordinator = user?.role === 'COORDINATOR';

  const teachingItems: NavItem[] = [
    { id: 'today', label: "Today's Classes", icon: CalendarCheck },
    { id: 'timetable', label: 'Timetable', icon: CalendarDays },
    { id: 'my-courses', label: 'My Courses', icon: BookOpen },
    {
      id: 'deadlines',
      label: 'Deadlines',
      icon: Calendar,
      badgeCount: unreadNotificationsCount > 0 ? unreadNotificationsCount : undefined,
    },
  ];

  const adminItems: NavItem[] = [
    { id: 'overview', label: 'Overview', icon: LayoutDashboard },
    { id: 'academics', label: 'Course Allocations', icon: BookOpen },
    { id: 'faculty', label: 'Faculty Directory', icon: Users },
    { id: 'compliance', label: 'Compliance Tracking', icon: CheckSquare },
    { id: 'audit', label: 'Audit Logs', icon: ShieldCheck },
  ];

  interface NavGroup {
    title: string;
    items: NavItem[];
  }

  const navGroups: NavGroup[] = isCoordinator
    ? [
        { title: 'My Teaching', items: teachingItems },
        { title: 'Administration', items: adminItems },
      ]
    : [
        {
          title: 'Faculty Portal',
          items: [
            ...teachingItems,
            { id: 'faculty', label: 'Faculty Directory', icon: Users },
          ],
        },
      ];

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
              <div className="w-8 h-8 rounded-md bg-primary text-white flex items-center justify-center font-sans font-bold text-sm shrink-0 shadow-xs">
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
            <div className="w-8 h-8 mx-auto rounded-md bg-primary text-white flex items-center justify-center font-sans font-bold text-xs">
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
        <div className="p-3 space-y-4">
          {navGroups.map((group, groupIdx) => (
            <div key={group.title} className="space-y-1">
              {!isCollapsed && (
                <div className="px-2 py-1 text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                  {group.title}
                </div>
              )}
              {isCollapsed && groupIdx > 0 && (
                <div className="my-2 border-t border-slate-200" />
              )}

              {group.items.map((item) => {
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
          ))}
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
