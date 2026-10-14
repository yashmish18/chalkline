import { useQuery } from '@tanstack/react-query';

/** What this server offers (GET /api/auth/config). Hides anything that would not work here. */
export interface AppConfig {
  institutionName: string;
  /** Password-less dev sign-in, bypass and user switcher (dev profile only). */
  devLogin: boolean;
  /** Real Microsoft Entra sign-in is configured. */
  microsoftLogin: boolean;
  microsoftLoginUrl: string | null;
  /** "Delete all data", "Reset activity", "Delete all faculty". */
  destructiveActions: boolean;
  /** Sign-in by typing an email: dev mode, or the temporary preview mode on a server. */
  emailSignIn?: boolean;
}

const SAFE_DEFAULT: AppConfig = {
  institutionName: '',
  devLogin: false,
  microsoftLogin: false,
  microsoftLoginUrl: null,
  destructiveActions: false,
  emailSignIn: false,
};

export function useAppConfig(): AppConfig {
  const { data } = useQuery({
    queryKey: ['app-config'],
    queryFn: async () => {
      const res = await fetch('/api/auth/config');
      if (!res.ok) throw new Error('config unavailable');
      return (await res.json()) as AppConfig;
    },
    staleTime: Infinity,
    retry: 1,
  });
  return data ?? SAFE_DEFAULT;
}
