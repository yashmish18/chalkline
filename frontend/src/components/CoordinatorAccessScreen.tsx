import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, KeyRound, Mail } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useAppConfig } from '../lib/appConfig';
import { clearSetupToken } from '../lib/coordinatorSetup';
import { UserDto } from '../types/auth';

type Mode = 'setup' | 'unlock' | 'awaiting';

interface Props {
  mode: Mode;
  token?: string;
}

/**
 * Coordinator access sits behind a second step after Microsoft sign-in:
 *  - setup: opened from the emailed link — choose the coordinator password;
 *  - unlock: enter it, once per session;
 *  - awaiting: a coordinator who has not set it up yet — the link is in their inbox.
 */
export function CoordinatorAccessScreen({ mode, token }: Props) {
  const { user, logout } = useAuth();
  const config = useAppConfig();
  const queryClient = useQueryClient();
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [devLink, setDevLink] = useState<string | null>(null);

  const post = async (url: string, body?: unknown) => {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.detail || `Request failed (${res.status})`);
    return data;
  };

  const enter = (updated: UserDto) => {
    clearSetupToken();
    queryClient.setQueryData(['auth', 'me'], updated);
    queryClient.invalidateQueries();
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (mode === 'setup' && password !== confirm) {
      setError('The two passwords don’t match.');
      return;
    }
    setBusy(true);
    try {
      if (mode === 'setup') {
        enter(await post('/api/auth/coordinator/setup', { token, password }));
      } else {
        enter(await post('/api/auth/coordinator/unlock', { password }));
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong.');
      setPassword('');
      setConfirm('');
    } finally {
      setBusy(false);
    }
  };

  const resend = async () => {
    setBusy(true);
    setError(null);
    try {
      const data = await post('/api/auth/coordinator/resend');
      setNotice(`A new link was sent to ${user?.email}. It expires in 72 hours.`);
      setDevLink(typeof data.devLink === 'string' ? data.devLink : null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not send the link.');
    } finally {
      setBusy(false);
    }
  };

  const skipSetup = () => {
    clearSetupToken();
    queryClient.invalidateQueries({ queryKey: ['auth', 'me'] });
    window.location.replace('/');
  };

  const title = mode === 'setup' ? 'Activate coordinator access' : mode === 'unlock' ? 'Coordinator password' : 'Check your email';

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
            {mode === 'awaiting' ? <Mail className="w-4 h-4" /> : <KeyRound className="w-4 h-4" />}
          </span>
          <h1 className="font-display mt-5 text-[28px] font-medium leading-tight">{title}</h1>
          <p className="mt-2 text-sm leading-relaxed text-[var(--ink-subtle)]">
            {mode === 'setup' && (
              <>Signed in as <strong className="text-[var(--ink)]">{user?.email}</strong>. Choose a password only you know. You’ll enter it each time you open coordinator tools.</>
            )}
            {mode === 'unlock' && <>Coordinator tools need your coordinator password, in addition to Microsoft sign-in.</>}
            {mode === 'awaiting' && (
              <>You’ve been made a coordinator. Open the link we emailed to <strong className="text-[var(--ink)]">{user?.email}</strong> to choose your coordinator password.</>
            )}
          </p>

          {mode !== 'awaiting' && (
            <form onSubmit={submit} className="mt-8 space-y-4">
              <label className="block">
                <span className="text-[13px] font-medium">{mode === 'setup' ? 'New coordinator password' : 'Password'}</span>
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
                {busy ? 'Checking…' : mode === 'setup' ? 'Activate coordinator access' : 'Unlock'}
              </button>
            </form>
          )}

          {mode === 'awaiting' && (
            <button type="button" onClick={resend} disabled={busy} className="btn w-full mt-8 h-11">
              {busy ? 'Sending…' : 'Email me a new link'}
            </button>
          )}

          {error && (
            <div role="alert" className="mt-4 flex items-start gap-2.5 rounded-lg border border-rose-200 bg-rose-50 p-3">
              <AlertTriangle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
              <span className="text-[13px] text-rose-800 leading-relaxed">{error}</span>
            </div>
          )}
          {notice && <p role="status" className="mt-4 text-[13px] text-[var(--ink-muted)]">{notice}</p>}
          {devLink && (
            <p className="mt-2 text-[12px] break-all text-[var(--ink-subtle)]">
              Dev mode (no mail server): <a className="underline" href={devLink}>{devLink}</a>
            </p>
          )}

          {mode === 'setup' && (
            <button type="button" onClick={skipSetup} className="mt-6 text-[13px] text-[var(--ink-subtle)] underline underline-offset-2">
              Not now — continue without coordinator access
            </button>
          )}
          {mode === 'unlock' && (
            <button type="button" onClick={resend} disabled={busy} className="mt-6 text-[13px] text-[var(--ink-subtle)] underline underline-offset-2">
              Forgot it? Email me a reset link
            </button>
          )}
        </div>
      </main>
    </div>
  );
}
