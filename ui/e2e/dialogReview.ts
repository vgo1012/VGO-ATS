import { expect, type Page, test } from '@playwright/test';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

export async function reviewDialog(page: Page, name: string) {
  const dialog = page.locator('.workspace-dialog:visible');
  await expect(dialog).toHaveCount(1);
  await expect(dialog).not.toHaveClass(/workspace-dialog-motion-(enter|appear)/);
  const rect = await dialog.boundingBox();
  const viewport = page.viewportSize()!;
  expect(rect!.x).toBeGreaterThanOrEqual(8);
  expect(rect!.y).toBeGreaterThanOrEqual(8);
  expect(rect!.x + rect!.width).toBeLessThanOrEqual(viewport.width - 8);
  expect(rect!.y + rect!.height).toBeLessThanOrEqual(viewport.height - 8);
  expect(await dialog.locator('.ant-modal-body').evaluate(node => node.scrollWidth - node.clientWidth)).toBeLessThanOrEqual(1);
  if (process.env.CAPTURE_VISUAL !== '1') return;
  const directory = 'test-results/review/2026-09-05-interactions';
  await mkdir(directory, { recursive: true });
  const output = `${directory}/${name}-${test.info().project.name}`;
  await page.screenshot({ path: `${output}.png`, animations: 'disabled' });
  // Optional local audit tooling; no production or mandatory test dependency.
  if (process.env.HR_QA_AXE_PATH) {
    const { default: AxeBuilder } = await import(pathToFileURL(resolve(process.env.HR_QA_AXE_PATH)).href);
    const result = await new AxeBuilder({ page }).include('.workspace-dialog').withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
    await writeFile(`${output}-axe.json`, JSON.stringify(result.violations, null, 2));
  }
}

