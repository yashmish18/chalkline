export type UserRole = 'COORDINATOR' | 'FACULTY' | 'DEAN';

export interface UserDto {
  id: number;
  email: string;
  name: string;
  role: UserRole;
}
