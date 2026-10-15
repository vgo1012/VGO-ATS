import '@testing-library/jest-dom/vitest';

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => undefined,
    removeListener: () => undefined,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    dispatchEvent: () => false,
  }),
});

Object.defineProperty(window, 'scrollTo', {
  writable: true,
  value: () => undefined,
});

class ResizeObserverStub {
  observe() {
    // Layout observation is outside jsdom's scope.
  }
  unobserve() {
    // Layout observation is outside jsdom's scope.
  }
  disconnect() {
    // Layout observation is outside jsdom's scope.
  }
}

globalThis.ResizeObserver = ResizeObserverStub;
