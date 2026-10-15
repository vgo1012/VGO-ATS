import { createContext, useContext, useSyncExternalStore } from 'react';

export const AnalyticsMotionContext = createContext(true);

function subscribe(listener: () => void) {
  const media = window.matchMedia('(prefers-reduced-motion: reduce)');
  media.addEventListener('change', listener);
  document.addEventListener('visibilitychange', listener);
  return () => {
    media.removeEventListener('change', listener);
    document.removeEventListener('visibilitychange', listener);
  };
}

export function useAnalyticsMotionPreference() {
  return useSyncExternalStore(subscribe,
    () => !window.matchMedia('(prefers-reduced-motion: reduce)').matches && !document.hidden,
    () => false);
}

export function useAnalyticsMotion() {
  const enabled = useContext(AnalyticsMotionContext);
  const allowed = useAnalyticsMotionPreference();
  return enabled && allowed;
}
