import { describe, expect, it, vi } from 'vitest';
import { registerPreloadErrorRecovery } from './preloadRecovery';

function targetWith(initialAttempt?: string) {
  const values = new Map<string, string>();
  if (initialAttempt) values.set('hr-ai:preload-reload-at', initialAttempt);
  let listener: EventListener | undefined;
  const reload = vi.fn();
  const target = {
    addEventListener: vi.fn((_type: string, value: EventListener) => { listener = value; }),
    location: { reload },
    sessionStorage: {
      getItem: vi.fn((key: string) => values.get(key) ?? null),
      setItem: vi.fn((key: string, value: string) => { values.set(key, value); }),
    },
  };
  registerPreloadErrorRecovery(target, () => 100_000);
  return { listener: () => listener!, reload, target };
}

describe('registerPreloadErrorRecovery', () => {
  it('reloads once when a deployed lazy chunk is no longer available', () => {
    const { listener, reload, target } = targetWith();
    const first = new Event('vite:preloadError', { cancelable: true });
    const repeated = new Event('vite:preloadError', { cancelable: true });

    listener()(first);
    listener()(repeated);

    expect(first.defaultPrevented).toBe(true);
    expect(repeated.defaultPrevented).toBe(false);
    expect(target.sessionStorage.setItem).toHaveBeenCalledWith('hr-ai:preload-reload-at', '100000');
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it('does not enter a reload loop after a recent recovery attempt', () => {
    const { listener, reload } = targetWith('70000');
    const event = new Event('vite:preloadError', { cancelable: true });

    listener()(event);

    expect(event.defaultPrevented).toBe(false);
    expect(reload).not.toHaveBeenCalled();
  });

  it('allows a later deployment recovery after the guard expires', () => {
    const { listener, reload } = targetWith('39999');
    const event = new Event('vite:preloadError', { cancelable: true });

    listener()(event);

    expect(event.defaultPrevented).toBe(true);
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it('still reloads once when session storage is unavailable', () => {
    let listener: EventListener | undefined;
    const reload = vi.fn();
    const target = {
      addEventListener: (_type: string, value: EventListener) => { listener = value; },
      location: { reload },
      sessionStorage: {
        getItem: vi.fn(() => { throw new Error('blocked'); }),
        setItem: vi.fn(() => { throw new Error('blocked'); }),
      },
    };
    registerPreloadErrorRecovery(target, () => 100_000);
    const event = new Event('vite:preloadError', { cancelable: true });

    listener!(event);

    expect(event.defaultPrevented).toBe(true);
    expect(reload).toHaveBeenCalledTimes(1);
  });
});
