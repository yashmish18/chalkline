import { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { AlertTriangle, ArrowRight } from 'lucide-react';
import { useAppConfig } from '../lib/appConfig';
import { pendingSetupToken } from '../lib/coordinatorSetup';

/**
 * Sign-in. Paper background, a serif statement of what the portal is for, and one
 * institutional sign-in action. Development shortcuts sit below a hairline, visually quiet.
 */
export function LoginScreen() {
  const { loginWithMicrosoft, bypassLogin, login } = useAuth();
  const config = useAppConfig();
  const [isPending, setIsPending] = useState(false);
  const [error, setError] = useState<string | null>(() =>
    new URLSearchParams(window.location.search).get('signin') === 'not-registered'
      ? 'Microsoft signed you in, but this email has no account here. Ask the coordinator to add you through the allocation sheet.'
      : null,
  );

  const handleMicrosoftSignIn = async () => {
    // Production: the real Microsoft Entra flow (a full-page redirect).
    if (config.microsoftLoginUrl) {
      window.location.href = config.microsoftLoginUrl;
      return;
    }
    setIsPending(true);
    setError(null);
    try {
      await loginWithMicrosoft();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Sign-in failed. Please try again.');
      setIsPending(false);
    }
  };

  const handleFastLogin = async (email: string) => {
    setIsPending(true);
    setError(null);
    try {
      await login(email);
    } catch {
      try {
        await bypassLogin();
      } catch (fallbackErr: unknown) {
        setError(fallbackErr instanceof Error ? fallbackErr.message : 'Sign-in failed.');
      }
    } finally {
      setIsPending(false);
    }
  };

  return (
    <div className="min-h-[100dvh] bg-[var(--paper)] text-[var(--ink)] flex flex-col">
      <header className="px-6 sm:px-10 py-6 flex items-center justify-between border-b border-[var(--hairline)]">
        <div className="flex items-baseline gap-3">
          <span className="font-display text-xl font-semibold tracking-tight">IET</span>
          <span className="text-[13px] text-[var(--ink-subtle)]">{config.institutionName}</span>
        </div>
        <span className="eyebrow hidden sm:inline">Faculty Dashboard</span>
      </header>

      <main className="flex-1 grid lg:grid-cols-[1.15fr_1fr]">
        <section className="px-6 sm:px-10 lg:px-16 py-12 lg:py-20 flex flex-col justify-center lg:border-r border-[var(--hairline)]">
          <h1 className="font-display mt-5 text-[40px] sm:text-[52px] leading-[1.05] font-medium max-w-[14ch]">
            Teaching load, syllabus pace and class logs, in one place.
          </h1>
          <dl className="mt-12 grid sm:grid-cols-3 gap-8 max-w-2xl">
            {[
              ['Workload', 'Lecture, tutorial, practical and PBL hours per faculty, from the load sheet.'],
              ['Pacing', 'Each section’s syllabus coverage against where the calendar says it should be.'],
              ['Logging', 'Who is recording their classes, and who has gone quiet.'],
            ].map(([term, detail]) => (
              <div key={term} className="border-t border-[var(--ink)] pt-3">
                <dt className="text-[13px] font-semibold">{term}</dt>
                <dd className="mt-1.5 text-[13px] leading-relaxed text-[var(--ink-subtle)]">{detail}</dd>
              </div>
            ))}
          </dl>
        </section>

        <section className="px-6 sm:px-10 lg:px-16 py-12 flex items-center justify-center bg-[var(--surface-2)] lg:bg-transparent">
          <div className="w-full max-w-sm">
            <h2 className="font-display text-[28px] font-medium">Sign in</h2>
            <p className="mt-1.5 text-sm text-[var(--ink-subtle)]">Use your institutional Microsoft account.</p>
            {pendingSetupToken() && (
              <p className="mt-4 rounded-lg border border-[var(--hairline)] bg-[var(--surface-3)] p-3 text-[13px] text-[var(--ink-muted)]">
                You opened a coordinator activation link. Sign in with the Microsoft account it was emailed to, then
                choose your coordinator password.
              </p>
            )}

            {!config.microsoftLogin && !config.devLogin && (
              <p className="mt-8 rounded-lg border border-[var(--hairline)] bg-[var(--surface-3)] p-3 text-[13px] text-[var(--ink-muted)]">
                Sign-in is not configured on this server yet. The administrator needs to set the Microsoft Entra
                settings (see DEPLOY.md).
              </p>
            )}

            {(config.microsoftLogin || config.devLogin) && (
            <button
              type="button"
              onClick={handleMicrosoftSignIn}
              disabled={isPending}
              className="btn btn-primary w-full mt-8 h-11 justify-between px-4 text-sm group"
            >
              <span className="inline-flex items-center gap-3">
                <MicrosoftLogo />
                {isPending ? 'Connecting…' : 'Continue with Microsoft'}
              </span>
              <ArrowRight className="w-4 h-4 opacity-70 group-hover:translate-x-0.5 transition-transform" />
            </button>
            )}

            {error && (
              <div role="alert" className="mt-4 flex items-start gap-2.5 rounded-lg border border-rose-200 bg-rose-50 p-3">
                <AlertTriangle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
                <span className="text-[13px] text-rose-800 leading-relaxed">{error}</span>
              </div>
            )}

            {config.devLogin && (
            <div className="mt-10 pt-6 border-t border-[var(--hairline)]">
              <p className="eyebrow">Development sign-in</p>
              <div className="mt-3 grid grid-cols-2 gap-2">
                <button type="button" onClick={() => handleFastLogin('coordinator@iet.edu')} disabled={isPending} className="btn">
                  Coordinator
                </button>
                <button type="button" onClick={() => handleFastLogin('mayank.kundalwal@iet.edu')} disabled={isPending} className="btn">
                  Faculty
                </button>
              </div>
            </div>
            )}
          </div>
        </section>
      </main>

      <footer className="px-6 sm:px-10 py-5 border-t border-[var(--hairline)] text-[12px] text-[var(--ink-tertiary)]">
        Single sign-on through Microsoft Entra ID.
      </footer>
    </div>
  );
}

function MicrosoftLogo() {
  return (
    <svg viewBox="0 0 23 23" className="w-4 h-4 shrink-0" aria-hidden="true" focusable="false">
      <rect x="1" y="1" width="10" height="10" fill="#F25022" />
      <rect x="12" y="1" width="10" height="10" fill="#7FBA00" />
      <rect x="1" y="12" width="10" height="10" fill="#00A4EF" />
      <rect x="12" y="12" width="10" height="10" fill="#FFB900" />
    </svg>
  );
}
