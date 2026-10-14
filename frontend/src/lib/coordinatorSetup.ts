/**
 * The coordinator setup link (`/?coordinator-setup=<token>`) has to survive the Microsoft sign-in
 * round trip, so the token is moved out of the address bar into this tab's session storage.
 */
const PARAM = 'coordinator-setup';
const KEY = 'coordinatorSetupToken';

/** Call once at start-up: keeps the token from the URL (if any) and removes it from the address bar. */
export function captureSetupToken(location: Location = window.location, history: History = window.history) {
  const params = new URLSearchParams(location.search);
  const token = params.get(PARAM)?.trim();
  if (!token) return;
  try {
    sessionStorage.setItem(KEY, token);
  } catch {
    // Storage blocked: the token stays in the URL instead, which still works for this page load.
    return;
  }
  params.delete(PARAM);
  const query = params.toString();
  history.replaceState(null, '', location.pathname + (query ? `?${query}` : '') + location.hash);
}

export function pendingSetupToken(): string | null {
  try {
    const stored = sessionStorage.getItem(KEY);
    if (stored) return stored;
  } catch {
    // fall through to the URL
  }
  return new URLSearchParams(window.location.search).get(PARAM);
}

export function clearSetupToken() {
  try {
    sessionStorage.removeItem(KEY);
  } catch {
    // nothing stored
  }
}
