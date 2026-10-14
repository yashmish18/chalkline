import { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { AlertTriangle, ArrowRight } from 'lucide-react';
import { useAppConfig } from '../lib/appConfig';

/**
 * Sign-in. Paper background, a serif statement of what the portal is for, and one
 * institutional sign-in action. Development shortcuts sit below a hairline, visually quiet.
 */
export function LoginScreen() {
  const { login } = useAuth();
  const config = useAppConfig();
  const [isPending, setIsPending] = useState(false);
  const [devEmail, setDevEmail] = useState('');
  const [error, setError] = useState<string | null>(() =>
    new URLSearchParams(window.location.search).get('signin') === 'not-registered'
      ? 'Microsoft signed you in, but this email has no account here. Ask the coordinator to add you through the allocation sheet.'
      : null,
  );

  // The real Microsoft Entra flow: a full-page redirect to Microsoft and back.
  const handleMicrosoftSignIn = () => {
    if (!config.microsoftLoginUrl) return;
    setIsPending(true);
    window.location.href = config.microsoftLoginUrl;
  };

  /** Dev only: sign in as an existing account by email. The admin still needs the admin password next. */
  const handleDevLogin = async (email: string) => {
    if (!email.trim()) return;
    setIsPending(true);
    setError(null);
    try {
      await login(email.trim());
    } catch (err: unknown) {
      setError(err instanceof Error && err.message ? err.message : `No active account for ${email}.`);
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

            {!config.microsoftLogin && !config.emailSignIn && (
              <p className="mt-8 rounded-lg border border-[var(--hairline)] bg-[var(--surface-3)] p-3 text-[13px] text-[var(--ink-muted)]">
                Sign-in is not configured on this server yet. The administrator needs to set the Microsoft Entra
                settings (see DEPLOY.md).
              </p>
            )}

            {config.microsoftLogin && (
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

            {config.emailSignIn && !config.microsoftLogin && (
              <p className="mt-8 rounded-lg border border-[var(--hairline)] bg-[var(--surface-3)] p-3 text-[13px] text-[var(--ink-muted)]">
                Microsoft sign-in isn’t set up yet. Sign in with your email below.
              </p>
            )}

            {config.emailSignIn && (
            <div className="mt-10 pt-6 border-t border-[var(--hairline)]">
              <p className="eyebrow">{config.devLogin ? 'Development sign-in' : 'Preview sign-in'}</p>
              <p className="mt-1.5 text-[12px] text-[var(--ink-subtle)]">
                {config.devLogin
                  ? 'Stands in for Microsoft on this machine only.'
                  : 'Temporary, until Microsoft sign-in is set up.'}{' '}
                Enter your account email; the admin is then asked for the admin password.
              </p>
              <form
                className="mt-3 flex gap-2"
                onSubmit={(e) => {
                  e.preventDefault();
                  handleDevLogin(devEmail);
                }}
              >
                <input
                  type="email"
                  className="field flex-1"
                  placeholder="any account email"
                  value={devEmail}
                  onChange={(e) => setDevEmail(e.target.value)}
                  aria-label="Account email"
                />
                <button type="submit" disabled={isPending || !devEmail.trim()} className="btn">
                  Sign in
                </button>
              </form>
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
