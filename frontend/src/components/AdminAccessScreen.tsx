import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, KeyRound } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useAppConfig } from '../lib/appConfig';
import { UserDto } from '../types/auth';

interface Props {
  /** setup: first sign-in, choose the admin password. unlock: enter it, once per session. */
  mode: 'setup' | 'unlock';
}

/** The admin's second step after Microsoft sign-in. Nobody else ever sees this screen. */
export function AdminAccessScreen({ mode }: Props) {
  const { user, logout } = useAuth();
  const config = useAppConfig();
  const queryClient = useQueryClient();
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (mode === 'setup' && password !== confirm) {
      setError('The two passwords don’t match.');
      return;
    }
    setBusy(true);
    try {
      const res = await fetch(`/api/auth/admin/${mode}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ password }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(data.detail || `Request failed (${res.status})`);
      queryClient.setQueryData(['auth', 'me'], data as UserDto);
      queryClient.invalidateQueries();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong.');
      setPassword('');
      setConfirm('');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="min-h-[100dvh] bg-[var(--paper)] text-[var(--ink)] flex flex-col">
      <header className="px-6 sm:px-10 py-6 flex items-center justify-between border-b border-[var(--hairline)]">
        <div className="flex items-baseline gap-3">
          <span className="font-display text-xl font-semibold tracking-tight">IET</span>
          <span className="text-[13px] text-[var(--ink-subtle)]">{config.institutionName}</span>
        </div>
        <button type="button" onClick={() => logout()} className="btn btn-quiet text-[13px]">
          Sign out
        </button>
      </header>

      <main className="flex-1 flex items-center justify-center px-4 sm:px-6 py-12">
        <div className="w-full max-w-sm">
          <span className="inline-flex w-10 h-10 items-center justify-center rounded-full border border-[var(--hairline)] bg-[var(--surface-2)]">
            <KeyRound className="w-4 h-4" />
          </span>
          <h1 className="font-display mt-5 text-[28px] font-medium leading-tight">
            {mode === 'setup' ? 'Set the admin password' : 'Admin password'}
          </h1>
          <p className="mt-2 text-sm leading-relaxed text-[var(--ink-subtle)]">
            {mode === 'setup' ? (
              <>Signed in as <strong className="text-[var(--ink)]">{user?.email}</strong>, the admin. Choose a password only you know. You’ll enter it after every sign-in.</>
            ) : (
              <>Signed in as <strong className="text-[var(--ink)]">{user?.email}</strong>. The admin account needs the admin password too.</>
            )}
          </p>

          <form onSubmit={submit} className="mt-8 space-y-4">
            <label className="block">
              <span className="text-[13px] font-medium">{mode === 'setup' ? 'New admin password' : 'Password'}</span>
              <input
                type="password"
                className="field mt-1.5"
                autoComplete={mode === 'setup' ? 'new-password' : 'current-password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                minLength={mode === 'setup' ? 12 : undefined}
                maxLength={72}
                required
                autoFocus
              />
              {mode === 'setup' && (
                <span className="mt-1 block text-[12px] text-[var(--ink-subtle)]">At least 12 characters. A short sentence works well.</span>
              )}
            </label>
            {mode === 'setup' && (
              <label className="block">
                <span className="text-[13px] font-medium">Type it again</span>
                <input
                  type="password"
                  className="field mt-1.5"
                  autoComplete="new-password"
                  value={confirm}
                  onChange={(e) => setConfirm(e.target.value)}
                  maxLength={72}
                  required
                />
              </label>
            )}
            <button type="submit" disabled={busy} className="btn btn-primary w-full h-11">
              {busy ? 'Checking…' : mode === 'setup' ? 'Save and continue' : 'Unlock'}
            </button>
          </form>

          {error && (
            <div role="alert" className="mt-4 flex items-start gap-2.5 rounded-lg border border-rose-200 bg-rose-50 p-3">
              <AlertTriangle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
              <span className="text-[13px] text-rose-800 leading-relaxed">{error}</span>
            </div>
          )}

          {mode === 'unlock' && (
            <p className="mt-6 text-[12px] text-[var(--ink-tertiary)]">
              Forgot it? Whoever runs the server sets <code>APP_ADMIN_RESET_PASSWORD=true</code> and restarts it; you then choose a new one.
            </p>
          )}
        </div>
      </main>
    </div>
  );
}
