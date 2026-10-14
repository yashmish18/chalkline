import { useState, useRef, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { NotificationDto, NotificationType } from '../types/notifications';
import { useAuth } from '../context/AuthContext';
import {
  Bell,
  CheckCircle,
  AlertCircle,
  AlertTriangle,
  Clock,
  RotateCcw,
  Check,
  CheckCheck,
} from 'lucide-react';

interface NotificationCenterProps {
  onNavigate?: (view: string) => void;
}

export function NotificationCenter({ onNavigate }: NotificationCenterProps) {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  // Poll unread count every 45 seconds
  const { data: unreadData } = useQuery<{ unreadCount: number }>({
    queryKey: ['notifications', 'unread-count'],
    queryFn: async () => {
      const res = await fetch('/api/notifications/unread-count');
      if (!res.ok) throw new Error('Failed to fetch unread count');
      return res.json();
    },
    enabled: !!user,
    refetchInterval: 45000,
  });

  const unreadCount = unreadData?.unreadCount ?? 0;

  // Fetch full notifications list when popover is open
  const { data: notifications = [], isLoading } = useQuery<NotificationDto[]>({
    queryKey: ['notifications', 'list'],
    queryFn: async () => {
      const res = await fetch('/api/notifications');
      if (!res.ok) throw new Error('Failed to fetch notifications');
      return res.json();
    },
    enabled: !!user && isOpen,
  });

  // Mark single notification as read
  const markAsReadMutation = useMutation({
    mutationFn: async (id: number) => {
      const res = await fetch(`/api/notifications/${id}/read`, { method: 'PATCH' });
      if (!res.ok) throw new Error('Failed to mark notification as read');
      return res.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  // Mark all as read
  const markAllReadMutation = useMutation({
    mutationFn: async () => {
      const res = await fetch('/api/notifications/read-all', { method: 'POST' });
      if (!res.ok) throw new Error('Failed to mark all as read');
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  // Close dropdown on click outside
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }
    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [isOpen]);

  const handleNotificationClick = (n: NotificationDto) => {
    if (!n.isRead) {
      markAsReadMutation.mutate(n.id);
    }
    if (n.targetUrl && onNavigate) {
      const view = n.targetUrl.replace('/', '');
      onNavigate(view);
      setIsOpen(false);
    }
  };

  const getNotificationIcon = (type: NotificationType) => {
    switch (type) {
      case 'DEADLINE_ASSIGNED':
        return <Clock className="w-4 h-4 text-blue-500" />;
      case 'UPCOMING_REMINDER':
        return <AlertCircle className="w-4 h-4 text-amber-500" />;
      case 'OVERDUE_ALERT':
        return <AlertTriangle className="w-4 h-4 text-rose-500" />;
      case 'SUBMISSION_ACCEPTED':
        return <CheckCircle className="w-4 h-4 text-emerald-500" />;
      case 'SUBMISSION_RETURNED':
        return <RotateCcw className="w-4 h-4 text-rose-500" />;
      default:
        return <Bell className="w-4 h-4 text-slate-500" />;
    }
  };

  const formatRelativeTime = (isoString: string) => {
    const diff = Math.floor((Date.now() - new Date(isoString).getTime()) / 1000);
    if (diff < 60) return 'Just now';
    if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
    if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
    return `${Math.floor(diff / 86400)}d ago`;
  };

  return (
    <div className="relative" ref={dropdownRef}>
      {/* Bell Button */}
      <button
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        className="relative p-2 text-[#6B6F76] hover:text-[#0B4747] hover:bg-[#F6F5F1] rounded-lg border border-transparent hover:border-[#E6E4DD] transition-colors focus:outline-none focus:ring-2 focus:ring-[#0F5C5C]/40"
        aria-label={`Notifications (${unreadCount} unread)`}
      >
        <Bell className="w-4 h-4" />
        {unreadCount > 0 && (
          <span className="absolute top-1 right-1 flex items-center justify-center min-w-[16px] h-[16px] px-1 text-[9px] font-bold text-white bg-[#0F5C5C] rounded-full ring-2 ring-[#FFFFFF]">
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>

      {/* Popover */}
      {isOpen && (
        <div className="absolute right-0 mt-2 w-80 sm:w-96 bg-[#F6F5F1] border border-[#E6E4DD] rounded-xl shadow-2xl z-50 overflow-hidden animate-in fade-in zoom-in-95 duration-100">
          {/* Header */}
          <div className="px-4 py-3 border-b border-[#E6E4DD] flex items-center justify-between bg-[#F6F5F1]">
            <div className="flex items-center gap-2">
              <span className="font-sans text-sm font-bold text-[#16181D]">Notifications</span>
              {unreadCount > 0 && (
                <span className="text-[11px] font-semibold px-2 py-0.5 rounded-full bg-[#0F5C5C]/20 text-[#0B4747] border border-[#0F5C5C]/40">
                  {unreadCount} new
                </span>
              )}
            </div>
            {unreadCount > 0 && (
              <button
                type="button"
                onClick={() => markAllReadMutation.mutate()}
                disabled={markAllReadMutation.isPending}
                className="inline-flex items-center gap-1 text-[11px] font-medium text-[#6B6F76] hover:text-[#0B4747] transition-colors disabled:opacity-50"
              >
                <CheckCheck className="w-3.5 h-3.5" />
                Mark all as read
              </button>
            )}
          </div>

          {/* List */}
          <div className="max-h-96 overflow-y-auto divide-y divide-[#E6E4DD]">
            {isLoading ? (
              <div className="p-8 text-center text-xs text-[#6B6F76]">Loading notifications...</div>
            ) : notifications.length === 0 ? (
              <div className="p-8 text-center">
                <Check className="w-8 h-8 text-[#8A8E95] mx-auto mb-1.5" />
                <p className="text-xs font-medium text-[#3A3D44]">No notifications yet</p>
                <p className="text-[11px] text-[#8A8E95] mt-0.5">We'll alert you here when deadlines or reviews occur.</p>
              </div>
            ) : (
              notifications.map((n) => (
                <div
                  key={n.id}
                  onClick={() => handleNotificationClick(n)}
                  className={`p-3.5 flex items-start gap-3 transition-colors cursor-pointer hover:bg-[#0F5C5C]/10 ${
                    !n.isRead ? 'bg-[#0F5C5C]/5' : 'bg-transparent'
                  }`}
                >
                  <div className="mt-0.5 shrink-0 w-7 h-7 rounded-full bg-[#F6F5F1] border border-[#E7E5DF] flex items-center justify-center">
                    {getNotificationIcon(n.type)}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between gap-1">
                      <h4
                        className={`text-xs truncate ${
                          !n.isRead ? 'font-bold text-[#16181D]' : 'font-medium text-[#3A3D44]'
                        }`}
                      >
                        {n.title}
                      </h4>
                      <span className="text-[10px] text-[#8A8E95] shrink-0">
                        {formatRelativeTime(n.createdAt)}
                      </span>
                    </div>
                    <p className="text-xs text-[#6B6F76] mt-0.5 line-clamp-2">{n.message}</p>
                  </div>
                  {!n.isRead && (
                    <span className="w-2 h-2 rounded-full bg-[#0B4747] mt-1.5 shrink-0 shadow-xs shadow-[#0F5C5C]" aria-label="Unread" />
                  )}
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
}
