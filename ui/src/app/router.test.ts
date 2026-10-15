import { describe, expect, it } from 'vitest';

import { isKnownWorkspacePath } from '../auth/workspaceAccess';
import { router } from './router';

describe('application router', () => {
  it('keeps the public home synchronous and provides a fallback above protected lazy routes', () => {
    const home = router.routes.find((route) => route.path === '/');
    const workspace = router.routes.find((route) => !route.path && route.children?.some((child) => child.path === 'overview'));
    const password = router.routes.find((route) => route.path === '/change-password');

    expect(home?.element).toBeDefined();
    expect(workspace?.hydrateFallbackElement).toBeDefined();
    expect(password?.hydrateFallbackElement).toBeDefined();
    expect(workspace?.children?.find((route) => route.path === 'admin/storage')).toBeUndefined();
  });

  it('registers an access policy for every protected page', () => {
    const workspace = router.routes.find((route) => !route.path && route.children?.some((child) => child.path === 'overview'));
    const paths = workspace?.children?.map((route) => route.path).filter((path) => path && path !== '*') ?? [];
    expect(paths.length).toBeGreaterThan(0);
    for (const path of paths) expect(isKnownWorkspacePath(`/${path}`)).toBe(true);
  });
});
