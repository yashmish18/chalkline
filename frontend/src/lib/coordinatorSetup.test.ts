import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { captureSetupToken, clearSetupToken, pendingSetupToken } from './coordinatorSetup';

function memoryStorage() {
  const data = new Map<string, string>();
  return {
    getItem: (k: string) => data.get(k) ?? null,
    setItem: (k: string, v: string) => void data.set(k, v),
    removeItem: (k: string) => void data.delete(k),
  };
}

describe('coordinator setup link', () => {
  beforeEach(() => {
    vi.stubGlobal('sessionStorage', memoryStorage());
    vi.stubGlobal('window', { location: { search: '' } });
  });
  afterEach(() => vi.unstubAllGlobals());

  it('moves the token out of the address bar and keeps other parameters', () => {
    const replaceState = vi.fn();
    captureSetupToken(
      { search: '?coordinator-setup=abc123&tab=x', pathname: '/', hash: '' } as Location,
      { replaceState } as unknown as History,
    );
    expect(pendingSetupToken()).toBe('abc123');
    expect(replaceState).toHaveBeenCalledWith(null, '', '/?tab=x');
  });

  it('does nothing without a token, and forgets it once used', () => {
    const replaceState = vi.fn();
    captureSetupToken({ search: '?signin=failed', pathname: '/', hash: '' } as Location, { replaceState } as unknown as History);
    expect(replaceState).not.toHaveBeenCalled();
    expect(pendingSetupToken()).toBeNull();

    sessionStorage.setItem('coordinatorSetupToken', 'tok');
    clearSetupToken();
    expect(pendingSetupToken()).toBeNull();
  });

  it('falls back to the URL when storage is blocked', () => {
    vi.stubGlobal('sessionStorage', {
      getItem: () => { throw new Error('blocked'); },
      setItem: () => { throw new Error('blocked'); },
      removeItem: () => { throw new Error('blocked'); },
    });
    vi.stubGlobal('window', { location: { search: '?coordinator-setup=urltok' } });
    const replaceState = vi.fn();
    captureSetupToken({ search: '?coordinator-setup=urltok', pathname: '/', hash: '' } as Location, { replaceState } as unknown as History);
    expect(replaceState).not.toHaveBeenCalled();
    expect(pendingSetupToken()).toBe('urltok');
    expect(() => clearSetupToken()).not.toThrow();
  });
});
