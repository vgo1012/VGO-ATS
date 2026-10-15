import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('收件校验使用紧凑列宽和单一列表滚动，操作按钮跟随对应行',async({page},testInfo)=>{
  await page.setViewportSize({width:1440,height:900});
  await reviewWorkspace(page);
  await page.route('**/api/v1/intakes?*',route=>route.fulfill({json:{items:Array.from({length:30},(_,index)=>({
    id:`receipt-${index}`,originalFileName:`候选人简历_${index+1}.pdf`,ownerUserId:'hr-a',sourceChannelId:'source',
    status:'FAILED_RETRYABLE',importStatus:'PENDING',scanStatus:'CLEAN',version:1,
    jdVersionId:'jd-v2',jdTitle:'IT运维工程师',createdAt:'2026-09-09T08:35:00Z',
  })),hasMore:true,nextCursor:'next'}}));
  await page.goto('/settings/intakes');
  const drawer=page.getByRole('dialog',{name:'收件校验',exact:true});
  await expect(drawer.getByText('候选人简历_1.pdf',{exact:true})).toBeVisible();
  await page.locator('.intake-drawer').evaluate(async element=>{await Promise.allSettled(element.getAnimations({subtree:true}).filter(animation=>animation.effect?.getComputedTiming().iterations!==Infinity).map(animation=>animation.finished));});

  const table=drawer.locator('.ant-table-content');
  const pager=drawer.locator('.data-page-controls');
  for(const [width,height] of [[390,900],[768,900],[1280,720],[1440,900],[1920,1080]]){
    await page.setViewportSize({width,height});
    await expect.poll(async()=>Math.abs((await drawer.boundingBox())!.width-Math.min(width*.94,1180))).toBeLessThan(1);
    await expect(drawer).toBeInViewport({ratio:1});
    expect((await drawer.boundingBox())!.width).toBeLessThanOrEqual(1180);
    if(width>=1280){
      const filterCard=drawer.locator('.intake-filter-card');
      const datePicker=filterCard.locator('.ant-picker');
      const searchButton=filterCard.getByRole('button',{name:/^查\s*询$/});
      expect((await filterCard.boundingBox())!.height).toBeLessThan(120);
      expect(Math.abs((await datePicker.boundingBox())!.y-(await searchButton.boundingBox())!.y)).toBeLessThan(2);
    }
    expect(await drawer.evaluate(element=>Array.from(element.querySelectorAll('*')).filter(item=>item.scrollHeight>item.clientHeight+1&&/auto|scroll/.test(getComputedStyle(item).overflowY)).map(item=>item.className)),`viewport ${width}x${height}`).toEqual(['ant-table-content']);
    expect(await table.evaluate(element=>element.scrollWidth>element.clientWidth+1)).toBe(width<1100);
    await expect(pager).toBeInViewport({ratio:1});
    const pagerBefore=await pager.boundingBox();
    await table.evaluate(element=>{element.scrollTop=140;element.scrollLeft=element.scrollWidth;});
    // A fully visible row must retain its buttons at the same vertical position.
    const visibleRowKey=await table.evaluate(element=>{
      const viewport=element.getBoundingClientRect();
      const headerBottom=element.querySelector('thead')?.getBoundingClientRect().bottom??viewport.top;
      return Array.from(element.querySelectorAll<HTMLTableRowElement>('tbody tr[data-row-key]')).find(candidate=>{
        const button=candidate.querySelector('button');
        if(!button)return false;
        const box=button.getBoundingClientRect();
        return box.top>=headerBottom&&box.bottom<=viewport.bottom;
      })?.dataset.rowKey;
    });
    expect(visibleRowKey).toBeTruthy();
    const row=drawer.locator(`tr[data-row-key="${visibleRowKey}"]`);
    const retry=row.getByRole('button',{name:'重试导入',exact:true});
    await expect(retry).toBeInViewport({ratio:1});
    expect(await retry.evaluate(element=>{
      const box=element.getBoundingClientRect();
      return element.contains(document.elementFromPoint(box.x+box.width/2,box.y+box.height/2));
    })).toBe(true);
    const header=drawer.getByRole('columnheader',{name:'操作',exact:true});
    expect(await header.evaluate(element=>{
      const box=element.getBoundingClientRect();
      return element.contains(document.elementFromPoint(box.x+box.width/2,box.y+box.height/2));
    })).toBe(true);
    await table.evaluate(element=>{element.scrollTop=element.scrollHeight;});
    const lastDelete=drawer.locator('tr[data-row-key="receipt-29"]').getByRole('button',{name:/^删\s*除$/});
    await expect(lastDelete).toBeInViewport({ratio:1});
    await lastDelete.click();
    await expect(page.getByText('确认删除此收件？',{exact:true})).toBeVisible();
    await page.getByRole('button',{name:/^取\s*消$/}).click();
    await expect(page.getByText('确认删除此收件？',{exact:true})).not.toBeVisible();
    expect(await pager.boundingBox()).toEqual(pagerBefore);
    await table.evaluate(element=>{element.scrollTop=0;element.scrollLeft=0;});
    await page.screenshot({path:testInfo.outputPath(`intake-layout-${width}.png`),fullPage:true,animations:'disabled'});
  }
  expect((await new AxeBuilder({page}).include('.intake-drawer').analyze()).violations).toEqual([]);
});
