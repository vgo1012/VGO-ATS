import { readFileSync } from 'node:fs';
import { expect, test } from '@playwright/test';

// Windows 有头浏览器使用占据布局空间的滚动条，避免无头覆盖式滚动条漏报。
test.use({ headless: process.platform !== 'win32' });

test('workspace scrollbars preserve content width as overflow appears and disappears', async ({ page }) => {
  await page.setContent('<main class="app-main"><div class="pagination-workspace"></div><div class="talent-ledger-tabs"><div class="ant-tabs-content-active"></div></div><div class="settings-layout"><aside class="settings-sidebar"></aside><section class="settings-content"></section></div></main>');
  for (const file of ['global.css', 'settings-layout.css']) {
    await page.addStyleTag({ content: readFileSync(new URL(`../src/styles/${file}`, import.meta.url), 'utf8') });
  }
  const settingsWidth = await page.locator('body').evaluate(element => element.getBoundingClientRect().width);
  await page.locator('.settings-layout').evaluate(element => element.classList.remove('settings-layout'));
  expect(await page.locator('body').evaluate(element => element.getBoundingClientRect().width)).toBe(settingsWidth);
  await page.locator('.settings-content').evaluate(element => element.parentElement!.classList.add('settings-layout'));
  for (const selector of ['.pagination-workspace', '.talent-ledger-tabs .ant-tabs-content-active', '.settings-sidebar', '.settings-content']) {
    const widths = await page.locator(selector).evaluate(element => {
      const container = element as HTMLElement;
      container.style.cssText = 'display:block; width:300px; height:100px; min-height:0; max-height:none; flex:none;';
      const content = document.createElement('div');
      content.style.height = '20px';
      container.replaceChildren(content);
      const before = content.getBoundingClientRect().width;
      content.style.height = '300px';
      const during = content.getBoundingClientRect().width;
      const overflowing = container.scrollHeight > container.clientHeight;
      container.scrollTop = 50;
      const scrollable = container.scrollTop > 0;
      content.style.height = '20px';
      return { before, during, after: content.getBoundingClientRect().width, overflowing, scrollable };
    });
    expect(widths.overflowing, selector).toBe(true);
    expect(widths.scrollable, selector).toBe(true);
    expect(widths.during, selector).toBe(widths.before);
    expect(widths.after, selector).toBe(widths.before);
  }
});
