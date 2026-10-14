export type UserRole = 'COORDINATOR' | 'FACULTY' | 'DEAN';

export interface UserDto {
  id: number;
  email: string;
  name: string;
  role: UserRole;
  /** A coordinator's own session: unlocked, waiting for the coordinator password, or not set up yet. */
  coordinatorAccess?: CoordinatorAccess | null;
}

export type CoordinatorAccess = 'UNLOCKED' | 'LOCKED' | 'SETUP_REQUIRED';
