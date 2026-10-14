import { createContext, useContext, ReactNode } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { UserDto } from '../types/auth';

interface AuthContextType {
  user: UserDto | null;
  isLoading: boolean;
  login: (email: string) => Promise<void>;
  loginWithMicrosoft: (email?: string) => Promise<void>;
  /** Dev login bypass: signed in with the server-configured bypass identity. */
  bypassLogin: () => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

async function fetchMe(): Promise<UserDto | null> {
  const res = await fetch('/api/me');
  // No session yet — the login screen handles sign-in from here.
  if (res.status === 401) {
    return null;
  }
  if (!res.ok) {
    throw new Error('Failed to fetch current user');
  }
  return res.json();
}

async function startSession(endpoint: string, body: Record<string, unknown>): Promise<UserDto> {
  const res = await fetch(endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const error = await res.json().catch(() => ({}));
    throw new Error(error.detail || `Sign-in failed (${res.status})`);
  }
  return res.json() as Promise<UserDto>;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();

  const { data: user = null, isLoading } = useQuery({
    queryKey: ['auth', 'me'],
    queryFn: fetchMe,
    retry: false,
    staleTime: 1000 * 60 * 5, // 5 minutes
  });

  const loginMutation = useMutation({
    mutationFn: (email: string) => startSession('/api/auth/dev-login', { email }),
    onSuccess: (newUser) => {
      queryClient.setQueryData(['auth', 'me'], newUser);
      queryClient.invalidateQueries();
    },
  });

  const microsoftLoginMutation = useMutation({
    mutationFn: (email?: string) => startSession('/api/auth/microsoft', email ? { email } : {}),
    onSuccess: (newUser) => {
      queryClient.setQueryData(['auth', 'me'], newUser);
      queryClient.invalidateQueries();
    },
  });

  const logoutMutation = useMutation({
    mutationFn: async () => {
      await fetch('/api/auth/logout', { method: 'POST' });
    },
    onSuccess: () => {
      queryClient.setQueryData(['auth', 'me'], null);
      queryClient.clear();
    },
  });

  const login = async (email: string) => {
    await loginMutation.mutateAsync(email);
  };

  const bypassLoginMutation = useMutation({
    // Must match the server's app.auth.bypass-key dev default.
    mutationFn: () => startSession('/api/auth/microsoft', { bypassKey: 'dev' }),
    onSuccess: (newUser) => {
      queryClient.setQueryData(['auth', 'me'], newUser);
      queryClient.invalidateQueries();
    },
  });

  const loginWithMicrosoft = async (email?: string) => {
    await microsoftLoginMutation.mutateAsync(email);
  };

  const bypassLogin = async () => {
    await bypassLoginMutation.mutateAsync();
  };

  const logout = async () => {
    await logoutMutation.mutateAsync();
  };

  return (
    <AuthContext.Provider value={{ user, isLoading, login, loginWithMicrosoft, bypassLogin, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
