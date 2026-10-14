import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

interface QueryOptions {
  queryFn: () => Promise<unknown>;
}

const query = vi.hoisted(() => ({ data: undefined as unknown, options: undefined as QueryOptions | undefined }));

vi.mock('@tanstack/react-query', () => ({
  useQuery: (options: QueryOptions) => {
    query.options = options;
    return { data: query.data };
  },
}));

import { useAppConfig } from './appConfig';

describe('useAppConfig', () => {
  beforeEach(() => {
    query.data = undefined;
    query.options = undefined;
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('offers nothing risky until the server has answered', () => {
    expect(useAppConfig()).toEqual({
      institutionName: '',
      devLogin: false,
      microsoftLogin: false,
      microsoftLoginUrl: null,
      destructiveActions: false,
      emailSignIn: false,
    });
  });

  it('returns what the server reports once loaded', () => {
    query.data = {
      institutionName: 'IET',
      devLogin: false,
      microsoftLogin: true,
      microsoftLoginUrl: '/oauth2/authorization/entra',
      destructiveActions: false,
    };
    expect(useAppConfig().microsoftLoginUrl).toBe('/oauth2/authorization/entra');
  });

  it('fails the query on an error response instead of trusting its body', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('{"devLogin":true}', { status: 500 })));
    useAppConfig();
    await expect(query.options!.queryFn()).rejects.toThrow('config unavailable');
  });

  it('reads the config from /api/auth/config', async () => {
    const fetchStub = vi.fn(async () => new Response('{"devLogin":false}', { status: 200 }));
    vi.stubGlobal('fetch', fetchStub);
    useAppConfig();
    await expect(query.options!.queryFn()).resolves.toEqual({ devLogin: false });
    expect(fetchStub).toHaveBeenCalledWith('/api/auth/config');
  });
});
