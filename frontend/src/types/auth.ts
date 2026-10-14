export type UserRole = 'COORDINATOR' | 'FACULTY' | 'DEAN';

export interface UserDto {
  id: number;
  email: string;
  name: string;
  role: UserRole;
  /** Only for the admin's own session: unlocked, waiting for the admin password, or password not chosen yet. */
  adminAccess?: AdminAccess | null;
}

export type AdminAccess = 'UNLOCKED' | 'LOCKED' | 'SETUP_REQUIRED';
