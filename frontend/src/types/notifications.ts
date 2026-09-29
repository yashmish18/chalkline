export type NotificationType =
  | 'DEADLINE_ASSIGNED'
  | 'UPCOMING_REMINDER'
  | 'OVERDUE_ALERT'
  | 'SUBMISSION_ACCEPTED'
  | 'SUBMISSION_RETURNED'
  | 'GENERAL';

export interface NotificationDto {
  id: number;
  userId: number;
  title: string;
  message: string;
  type: NotificationType;
  targetUrl: string | null;
  readAt: string | null;
  createdAt: string;
  isRead: boolean;
}
