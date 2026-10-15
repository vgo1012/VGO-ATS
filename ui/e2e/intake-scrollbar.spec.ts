import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

// 原生鼠标拖拽需要显示滚动条；Playwright 无头模式默认隐藏它们。
test.use({launchOptions:{ignoreDefaultArgs:['--hide-scrollbars']},isMobile:false,hasTouch:false});

test('intake table scrollbars can be dragged with the mouse', async ({ page }, testInfo) => {
  await reviewWorkspace(page);
  await page.route('**/api/v1/intakes?**', route => route.fulfill({ json: {
    items: Array.from({ length: 30 }, (_, index) => ({
      id: `intake-${index}`,
      originalFileName: `候选人-${index}.pdf`,
      ownerUserId: 'hr-a',
      sourceChannelId: 'source',
      sizeBytes: 100,
      status: 'COMPLETED',
      importStatus: 'IMPORTED',
      jdVersionId: 'jd-v2',
      jdTitle: '售后工程师',
      scanStatus: 'CLEAN',
      version: 1,
    })),
    hasMore: false,
    nextCursor: null,
  } }));

  await page.goto('/settings/intakes');
  const scroller = page.locator('.intake-validation-page .ant-table-content');
  await expect(scroller.getByText('候选人-0.pdf')).toBeVisible();
  for (const width of [390, 1280, 1440, 1920]) {
    await page.setViewportSize({ width, height: 900 });
    await scroller.scrollIntoViewIfNeeded();
    await scroller.click({trial:true,position:{x:10,y:10}});
    await scroller.evaluate(element => { element.scrollTop = 0; element.scrollLeft = 0; });
    const metrics = await scroller.evaluate((element: HTMLElement) => ({
      clientHeight: element.clientHeight,
      scrollHeight: element.scrollHeight,
      clientWidth: element.clientWidth,
      scrollWidth: element.scrollWidth,
      scrollbarWidth: element.offsetWidth - element.clientWidth,
    }));
    expect(metrics.scrollHeight).toBeGreaterThan(metrics.clientHeight);
    expect(metrics.scrollbarWidth).toBeGreaterThan(0);
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);

    const box = await scroller.boundingBox();
    expect(box).not.toBeNull();
    const x = box!.x + box!.width - metrics.scrollbarWidth / 2;
    const verticalTrackHeight = metrics.clientHeight - metrics.scrollbarWidth * 2;
    const verticalThumbHeight = Math.max(20, verticalTrackHeight * metrics.clientHeight / metrics.scrollHeight);
    const y = box!.y + metrics.scrollbarWidth + verticalThumbHeight / 2;
    await page.screenshot({path:testInfo.outputPath(`intake-scrollbar-${width}.png`)});
    await page.mouse.move(x, y);
    await page.mouse.down();
    await page.mouse.move(x, y + Math.min(180, box!.height / 2), { steps: 10 });
    await page.mouse.up();
    await expect.poll(() => scroller.evaluate(element => element.scrollTop), { message: `${width}px vertical scrollbar ${JSON.stringify({ box, metrics, x, y })}` }).toBeGreaterThan(0);

    if (metrics.scrollWidth > metrics.clientWidth) {
      const scrollbarHeight = box!.height - metrics.clientHeight;
      const trackWidth = metrics.clientWidth - scrollbarHeight * 2;
      const thumbWidth = trackWidth * metrics.clientWidth / metrics.scrollWidth;
      await scroller.evaluate(element => { element.scrollLeft = 30; });
      const availableTravel = trackWidth - thumbWidth;
      const scrollRatio = 30 / (metrics.scrollWidth - metrics.clientWidth);
      const horizontalX = box!.x + scrollbarHeight + thumbWidth / 2 + availableTravel * scrollRatio;
      const horizontalY = box!.y + box!.height - scrollbarHeight / 2;
      const hit = await page.evaluate(({ x, y }) => {
        const element = document.elementFromPoint(x, y);
        return { tag: element?.tagName, className: element?.className, innerHeight };
      }, { x: horizontalX, y: horizontalY });
      await page.mouse.move(horizontalX, horizontalY);
      await page.mouse.down();
      await page.mouse.move(horizontalX + Math.min(160, availableTravel / 2), horizontalY, { steps: 10 });
      await page.mouse.up();
      await expect.poll(() => scroller.evaluate(element => element.scrollLeft), { message: `${width}px horizontal scrollbar ${JSON.stringify({ box, metrics, hit, horizontalX, horizontalY })}` }).toBeGreaterThan(30);
    }
    if (width === 390) await page.screenshot({ path: testInfo.outputPath('intake-scrollbars-390.png'), fullPage: true });
  }
  expect((await new AxeBuilder({ page }).include('.intake-validation-page').analyze()).violations).toEqual([]);
});
