const reloadAttemptKey = 'hr-ai:preload-reload-at';
const reloadGuardMilliseconds = 60_000;

interface PreloadRecoveryTarget {
  addEventListener(type: string, listener: EventListener): void;
  readonly location: Pick<Location, 'reload'>;
  readonly sessionStorage: Pick<Storage, 'getItem' | 'setItem'>;
}

export function registerPreloadErrorRecovery(
  target: PreloadRecoveryTarget = window,
  now: () => number = Date.now,
) {
  let reloadStarted = false;
  target.addEventListener('vite:preloadError', (event) => {
    if (reloadStarted) return;

    const currentTime = now();
    try {
      const previousAttempt = Number(target.sessionStorage.getItem(reloadAttemptKey));
      if (
        Number.isFinite(previousAttempt)
        && previousAttempt > 0
        && previousAttempt <= currentTime
        && currentTime - previousAttempt < reloadGuardMilliseconds
      ) return;
    } catch {
      // 浏览器禁用存储时仍允许当前页面尝试一次恢复。
    }

    event.preventDefault();
    reloadStarted = true;
    try {
      target.sessionStorage.setItem(reloadAttemptKey, String(currentTime));
    } catch {
      // 刷新恢复不应依赖会话存储可用性。
    }
    target.location.reload();
  });
}
