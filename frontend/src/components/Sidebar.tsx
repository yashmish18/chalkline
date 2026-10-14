import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import {
  LayoutDashboard,
  CalendarCheck,
  CalendarDays,
  BookOpen,
  Calendar,
  Users,
  Inbox,
  CheckSquare,
  FileText,
  ChevronLeft,
  ChevronRight
} from 'lucide-react';
import { UserProfileModal } from './UserProfileModal';
import { useAppConfig } from '../lib/appConfig';

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
}

interface NavGroup {
  title: string;
  items: NavItem[];
}

export function Sidebar({
  currentTab,
  onSelectTab,
  isCollapsed,
  onToggleCollapse,
}: SidebarProps) {
  const { user } = useAuth();
  const [isProfileModalOpen, setIsProfileModalOpen] = useState(false);
  const isCoordinator = user?.role === 'COORDINATOR';
  const appConfig = useAppConfig();

  const teachingItems: NavItem[] = [
    { id: 'today', label: "Today's Classes", icon: CalendarCheck },
    { id: 'timetable', label: 'Timetable', icon: CalendarDays },
    { id: 'my-courses', label: 'My Courses', icon: BookOpen },
    ...(isCoordinator ? [] : [{ id: 'my-report', label: 'My report', icon: FileText }]),
    { id: 'deadlines', label: 'Deadlines', icon: Calendar },
  ];

  const adminItems: NavItem[] = [
    { id: 'overview', label: 'Overview', icon: LayoutDashboard },
    { id: 'academics', label: 'Course Allocations', icon: BookOpen },
    { id: 'faculty', label: 'Faculty Directory', icon: Users },
    { id: 'submissions', label: 'Submissions', icon: Inbox },
    { id: 'compliance', label: 'Compliance Tracking', icon: CheckSquare },
    { id: 'report', label: 'Semester report', icon: FileText },
  ];

  const navGroups: NavGroup[] = isCoordinator
    ? [
        { title: 'My Teaching', items: teachingItems },
        { title: 'Administration', items: adminItems },
      ]
    : [
        {
          title: 'Faculty Portal',
          items: teachingItems,
        },
      ];

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

  return (
    <aside
      className={`print:hidden fixed inset-y-0 left-0 z-40 bg-[var(--surface-1)] border-r border-[#E6E4DD] transition-[width] duration-200 flex flex-col justify-between ${
        isCollapsed ? 'w-[72px]' : 'w-64'
      }`}
    >
      <div className="min-h-0 flex flex-col">
        {/* Brand header */}
        <div className="h-16 px-4 flex items-center justify-between border-b border-[#E6E4DD]">
          {!isCollapsed ? (
            <div className="flex items-center gap-2.5 overflow-hidden">
              <span className="font-display text-[22px] font-semibold text-[#16181D] leading-none shrink-0">IET</span>
              <div className="truncate border-l border-[#E6E4DD] pl-2.5">
                <div className="text-[13px] font-medium text-[#16181D] leading-tight truncate">
                  Faculty Dashboard
                </div>
                <div className="text-[11px] text-[#8A8E95] mt-0.5 truncate">
                  {appConfig.institutionName}
                </div>
              </div>
            </div>
          ) : (
            <span className="mx-auto font-display text-lg font-semibold text-[#16181D]">IET</span>
          )}

          {!isCollapsed && (
            <button
              type="button"
              onClick={onToggleCollapse}
              aria-label="Collapse sidebar"
              className="p-1.5 rounded-md text-[#8A8E95] hover:text-[#16181D] hover:bg-[#16181D]/[0.05] transition-colors shrink-0"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
          )}
        </div>

        {isCollapsed && (
          <button
            type="button"
            onClick={onToggleCollapse}
            aria-label="Expand sidebar"
            className="mx-auto mt-2 p-1.5 rounded-md text-[#8A8E95] hover:text-[#16181D] hover:bg-[#16181D]/[0.05] transition-colors"
          >
            <ChevronRight className="w-4 h-4" />
          </button>
        )}

        {/* Navigation */}
        <nav className="p-3 space-y-5 overflow-y-auto">
          {navGroups.map((group, groupIdx) => (
            <div key={group.title}>
              {!isCollapsed ? (
                <div className="px-3 pb-2 eyebrow text-[10px]">
                  {group.title}
                </div>
              ) : (
                groupIdx > 0 && <div className="mx-auto mb-3 h-px w-6 bg-[#E6E4DD]" />
              )}

              <div className="space-y-0.5">
                {group.items.map((item) => {
                  const Icon = item.icon;
                  const isActive = currentTab === item.id;

                  return (
                    <button
                      key={item.id}
                      type="button"
                      onClick={() => onSelectTab(item.id)}
                      title={isCollapsed ? item.label : undefined}
                      aria-current={isActive ? 'page' : undefined}
                      className={`group relative w-full flex items-center gap-3 rounded-md px-3 py-2 text-[13px] transition-colors ${
                        isActive
                          ? 'bg-white ring-1 ring-[#E6E4DD] text-[#16181D] font-medium'
                          : 'text-[#4E525A] font-normal hover:text-[#16181D] hover:bg-[#16181D]/[0.05]'
                      } ${isCollapsed ? 'justify-center px-0' : ''}`}
                    >
                      {isActive && (
                        <span className="absolute left-0 top-1.5 bottom-1.5 w-[2.5px] rounded-r-full bg-[#0F5C5C]" />
                      )}
                      <Icon
                        className={`w-4 h-4 shrink-0 transition-colors ${
                          isActive ? 'text-[#0B4747]' : 'text-[#6B6F76] group-hover:text-[#3A3D44]'
                        }`}
                      />
                      {!isCollapsed && <span className="truncate">{item.label}</span>}

                      {!isCollapsed && item.badgeCount !== undefined && (
                        <span
                          className={`ml-auto rounded-full px-1.5 py-0.5 text-[10px] font-semibold tabular-nums ${
                            isActive ? 'bg-[#0F5C5C]/25 text-[#0F5C5C]' : 'bg-[#16181D]/[0.08] text-[#3A3D44]'
                          }`}
                        >
                          {item.badgeCount}
                        </span>
                      )}

                      {isCollapsed && item.badgeCount !== undefined && (
                        <span className="absolute top-1 right-2 w-1.5 h-1.5 rounded-full bg-[#0F5C5C]" />
                      )}
                    </button>
                  );
                })}
              </div>
            </div>
          ))}
        </nav>
      </div>

      {/* User footer */}
      <div className="p-3 border-t border-[#EFEEE9]">
        <button
          type="button"
          onClick={() => setIsProfileModalOpen(true)}
          className={`w-full flex items-center ${
            isCollapsed ? 'justify-center' : 'gap-2.5 px-2 py-1.5'
          } rounded-lg hover:bg-[#EFEEE9]/80 transition-colors text-left group`}
          title="Open Profile & Settings"
        >
          <div className="relative shrink-0">
            <div className="w-8 h-8 rounded-full bg-[#EFEEE9] ring-1 ring-[#D6D3CA] text-[#16181D] flex items-center justify-center font-semibold text-[11px] group-hover:ring-[#0B4747] transition-colors">
              {initials}
            </div>
          </div>

          {!isCollapsed && (
            <div className="min-w-0 flex-1">
              <div className="text-[13px] font-medium text-[#16181D] truncate leading-tight group-hover:text-[#0B4747] transition-colors">
                {user?.name || 'Guest'}
              </div>
              <div className="text-[11px] text-[#6B6F76] truncate mt-0.5">
                {user?.role === 'COORDINATOR' ? 'Coordinator' : user?.role === 'FACULTY' ? 'Faculty Member' : 'Signed out'}
                <span className="mx-1 text-[#6B6F76]">·</span>
                Profile & Settings
              </div>
            </div>
          )}
        </button>
      </div>

      {isProfileModalOpen && (
        <UserProfileModal onClose={() => setIsProfileModalOpen(false)} />
      )}
    </aside>
  );
}
