/**
 * CSRF protection for every write request. The server issues an XSRF-TOKEN cookie; Spring Security
 * expects it echoed back in the X-XSRF-TOKEN header. Wrapping fetch once means no screen can forget.
 */
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE']);

export function readCsrfToken(cookie: string = document.cookie): string | null {
  const match = cookie.split('; ').find((part) => part.startsWith('XSRF-TOKEN='));
  return match ? decodeURIComponent(match.slice('XSRF-TOKEN='.length)) : null;
}

export function installCsrfFetch() {
  const original = window.fetch.bind(window);
  window.fetch = async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const method = (init.method ?? (input instanceof Request ? input.method : 'GET')).toUpperCase();
    const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
    const sameOrigin = url.startsWith('/') || url.startsWith(window.location.origin);
    if (SAFE_METHODS.has(method) || !sameOrigin) {
      return original(input, { credentials: 'include', ...init });
    }
    let token = readCsrfToken();
    if (!token) {
      // First write of the session: any GET makes the server set the cookie.
      await original('/api/auth/config', { credentials: 'include' });
      token = readCsrfToken();
    }
    const headers = new Headers(init.headers ?? (input instanceof Request ? input.headers : undefined));
    if (token) headers.set('X-XSRF-TOKEN', token);
    return original(input, { credentials: 'include', ...init, headers });
  };
}
