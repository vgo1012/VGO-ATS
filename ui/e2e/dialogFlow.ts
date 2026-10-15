import { expect, type Page } from '@playwright/test';

/** Assert continuity of the actual surface, not just an identical looking replacement. */
export async function rememberDialog(page: Page) {
  const dialog = page.locator('.workspace-dialog:visible');
  await expect(dialog).not.toHaveClass(/workspace-dialog-motion-(enter|appear)/);
  await dialog.evaluate(node => node.setAttribute('data-flow-surface', 'original'));
  await page.locator('.ant-modal-mask:visible').evaluate(node => node.setAttribute('data-flow-mask', 'original'));
  await dialog.evaluate(node => {
    const mask = document.querySelector('[data-flow-mask="original"]');
    const observer = new MutationObserver(() => {
      if (!node.isConnected || !mask?.isConnected || /workspace-dialog-motion-(enter|appear|leave)/.test(node.className) || getComputedStyle(node).display === 'none' || getComputedStyle(mask).display === 'none') {
        node.setAttribute('data-flow-interrupted', 'true');
        observer.disconnect();
      }
    });
    observer.observe(node.closest('.ant-modal-root')!, { childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'style'] });
  });
  const bounds = await dialog.boundingBox();
  return async (title: string | RegExp) => {
    const original = page.locator('[data-flow-surface="original"]');
    await expect(original).toBeVisible();
    await expect(original).toHaveAccessibleName(title);
    await expect(original).not.toHaveClass(/workspace-dialog-motion-(enter|appear|leave)/);
    await expect(original).not.toHaveAttribute('data-flow-interrupted', 'true');
    await expect(page.locator('[data-flow-mask="original"]')).toBeVisible();
    await expect(page.getByRole('dialog')).toHaveCount(1);
    expect(await original.boundingBox()).toEqual(bounds);
    expect(await original.locator('.ant-modal-body').evaluate(node => node.scrollWidth - node.clientWidth)).toBeLessThanOrEqual(1);
  };
}
