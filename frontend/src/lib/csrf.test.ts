import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { readCsrfToken, installCsrfFetch } from './csrf';

describe('readCsrfToken', () => {
  it('reads and decodes the XSRF-TOKEN cookie', () => {
    expect(readCsrfToken('JSESSIONID=abc; XSRF-TOKEN=a%2Fb-1')).toBe('a/b-1');
  });
  it('returns null when the cookie is absent', () => {
    expect(readCsrfToken('JSESSIONID=abc')).toBeNull();
  });
});

describe('readCsrfToken edge inputs', () => {
  it('finds the token wherever it sits in the cookie string', () => {
    expect(readCsrfToken('XSRF-TOKEN=first; JSESSIONID=abc')).toBe('first');
    expect(readCsrfToken('a=1; b=2; XSRF-TOKEN=last')).toBe('last');
  });
  it('keeps "=" characters inside the token value', () => {
    expect(readCsrfToken('XSRF-TOKEN=abc==')).toBe('abc==');
  });
  it('does not mistake a similarly named cookie for the token', () => {
    expect(readCsrfToken('MY-XSRF-TOKEN=nope; OLD_XSRF-TOKEN=nope')).toBeNull();
  });
  it('returns null for an empty cookie string and an empty string for an empty token', () => {
    expect(readCsrfToken('')).toBeNull();
    expect(readCsrfToken('XSRF-TOKEN=')).toBe('');
  });
});

describe('installCsrfFetch', () => {
  const ORIGIN = 'https://dash.iet.edu';
  let calls: Array<{ input: RequestInfo | URL; init?: RequestInit }>;
  let cookie: string;

  beforeEach(() => {
    calls = [];
    cookie = 'XSRF-TOKEN=tok%3D1';
    const fetchStub = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      calls.push({ input, init });
      if (input === '/api/auth/config') cookie = 'XSRF-TOKEN=fresh';
      return new Response('{}');
    });
    vi.stubGlobal('window', { fetch: fetchStub, location: { origin: ORIGIN } });
    vi.stubGlobal('document', {
      get cookie() {
        return cookie;
      },
    });
    installCsrfFetch();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  const headerOf = (init?: RequestInit) => new Headers(init?.headers).get('X-XSRF-TOKEN');

  it('adds the token header to same-origin writes, whatever the method case', async () => {
    await window.fetch('/api/courses', { method: 'post' });
    await window.fetch(`${ORIGIN}/api/courses/1`, { method: 'DELETE' });
    expect(calls.map((c) => headerOf(c.init))).toEqual(['tok=1', 'tok=1']);
    expect(calls.every((c) => c.init?.credentials === 'include')).toBe(true);
  });

  it('leaves reads alone', async () => {
    await window.fetch('/api/courses');
    await window.fetch('/api/courses', { method: 'HEAD' });
    expect(calls.map((c) => headerOf(c.init))).toEqual([null, null]);
  });

  it('never sends the token to another origin', async () => {
    await window.fetch('https://evil.example.com/steal', { method: 'POST' });
    await window.fetch(new URL('https://evil.example.com/x'), { method: 'PUT' });
    expect(calls.map((c) => headerOf(c.init))).toEqual([null, null]);
  });

  it('keeps headers the caller set', async () => {
    await window.fetch('/api/courses', { method: 'POST', headers: { 'Content-Type': 'application/json' } });
    const headers = new Headers(calls[0].init?.headers);
    expect(headers.get('Content-Type')).toBe('application/json');
    expect(headers.get('X-XSRF-TOKEN')).toBe('tok=1');
  });

  it('fetches the token first when the session has none yet', async () => {
    cookie = '';
    await window.fetch('/api/timetable/import', { method: 'POST' });
    // The priming GET is what makes the server set the cookie.
    expect(calls.map((c) => c.input)).toEqual(['/api/auth/config', '/api/timetable/import']);
    expect(headerOf(calls[1].init)).toBe('fresh');
  });
});
