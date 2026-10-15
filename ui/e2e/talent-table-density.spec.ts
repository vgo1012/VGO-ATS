import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('我的简历保留列宽和紧凑行高，技能浮层适配屏幕', async ({ page }, testInfo) => {
  await reviewWorkspace(page);
  const facts = { snapshot: { id: 'facts', resumeVersionId: 'resume-a', gender: 'FEMALE', age: 28 }, workExperiences: [], projectExperiences: [], educations: [], skills: ['Linux', 'Nginx', 'Tomcat', 'MySQL', 'Docker', 'Kubernetes', 'TCP/IP五层模型', 'OSI七层模型', 'ACL', 'NAT', 'VLSM子网划分', 'OSPF', 'BGP', 'RIP', 'VLAN', 'Trunk', 'STP/RSTP', 'QoS流量调度', 'VRRP/HSRP', '企业WLAN组网', '防火墙策略', 'IPSec VPN', 'DHCP', 'IPS/IDS', 'WAF', '堡垒机', '安全日志分析', '华为/H3C设备维护', 'Shell脚本', 'Wireshark'].map((skillName, id) => ({ id: String(id), skillName })), simpleFacts: [] };
  await page.route('**/api/v1/candidate-resume-summaries?**', route => route.fulfill({ json: [{ candidateId: 'person-a', factsStatus: 'READY', facts, source: { sourceChannelName: '内推' }, resumeVersion: { id: 'resume-a', createdAt: '2026-09-07T01:00:00Z' } }] }));
  await page.route('**/api/v1/resume-versions/resume-a/facts', route => route.fulfill({ json: facts }));
  await page.route('**/api/v1/applications?**', route => route.fulfill({ json: { items: [{ id: 'app-a', candidateId: 'person-a', resumeVersionId: 'resume-a', jdId: 'jd-a', ownerUserId: 'hr-a', currentStage: 'OFFER', recruitmentType: 'INTERNAL' }], hasMore: false } }));
  await page.route('**/api/v1/candidate-pool-statuses?**',route=>route.fulfill({json:[{candidateId:'person-a',resumeVersionId:'resume-a',jobContexts:[{jdId:'jd-a',jdVersionId:'jd-v2',title:'默认岗位',versionNo:2,resumeVersionId:'resume-a',status:'ADVANCED',applicationId:'app-a'}]}]}));
  await page.setViewportSize({ width: 1920, height: 1000 });
  await page.goto('/recruitment');
  const row = () => page.locator('.ant-table-tbody > tr.ant-table-row').first();
  await expect(row()).toContainText('林雨桐');
  await page.evaluate(() => document.fonts.ready);
  const baseline = await row().evaluate(element => ({ font: getComputedStyle(element.querySelector('td')!).fontSize }));
  const nameFont = await row().locator('.table-link').evaluate(element => getComputedStyle(element).fontSize);
  for (const path of ['/talents', '/talents?view=todo&todo_kind=recruitment']) {
    await page.goto(path);
    await expect(row()).toContainText('林雨桐');
    await expect(page.getByRole('columnheader',{name:'年龄',exact:true})).toBeVisible();
    await expect(row().getByRole('cell',{name:'28 岁',exact:true})).toBeVisible();
    await expect(row().getByText('已解析', { exact: true })).toHaveCount(0);
    await expect(page.getByRole('columnheader', { name: '风险标签', exact: true })).toBeVisible();
    expect((await row().locator('.talent-identity-cell').boundingBox())!.width).toBeLessThanOrEqual(181);
    expect((await row().locator('.talent-age-cell').boundingBox())!.width).toBeLessThanOrEqual(61);
    await expect(row().locator('td').first()).toHaveCSS('font-size', baseline.font);
    await expect(row().locator('.table-link')).toHaveCSS('font-size', nameFont);
    const coarsePointer = await page.evaluate(() => matchMedia('(pointer: coarse)').matches);
    const actionButtons = row().locator('.table-action-cell .ant-btn');
    await expect(actionButtons).toHaveCount(1);
    await expect(actionButtons.first()).toHaveText('详情与版本');
    await expect(page.getByRole('button',{name:'查看招聘活动'})).toHaveCount(0);
    for (const button of await actionButtons.all()) {
      expect((await button.boundingBox())!.height).toBeGreaterThanOrEqual(coarsePointer ? 44 : 32);
    }
    // 表格以 72px 为基础行高；说明小字和纵排操作按实际内容增高，不压缩操作目标。
    const height = (await row().boundingBox())!.height;
    const range = coarsePointer ? [85, 87] : [72, 74];
    expect(height).toBeGreaterThanOrEqual(range[0]!);
    expect(height).toBeLessThanOrEqual(range[1]!);
    {
      const more = row().getByRole('button', { name: '查看全部 30 项技能' });
      await expect(row().locator('.candidate-skills-preview > span')).toHaveCount(8);
      await expect(more).toHaveText('+22 项');
      const before = await row().locator('td').evaluateAll(cells => cells.map(cell => cell.getBoundingClientRect().width));
      const beforeHeight = (await row().boundingBox())!.height;
      await more.focus();
      await page.keyboard.press('Enter');
      await expect(page.getByRole('region', { name: '全部核心技能' }).getByText('Kubernetes', { exact: true })).toBeVisible();
      expect((await row().boundingBox())!.height).toBe(beforeHeight);
      expect(await row().locator('td').evaluateAll(cells => cells.map(cell => cell.getBoundingClientRect().width))).toEqual(before);
      await page.getByRole('region', { name: '全部核心技能' }).screenshot({ path: testInfo.outputPath('skills-open.png'), animations: 'disabled' });
      await page.keyboard.press('Escape');
      await expect(more).toHaveAttribute('aria-expanded', 'false');
      await expect(more).toBeFocused();
    }
    for (const width of [390, 768, 1280, 1440, 1920]) {
      await page.setViewportSize({ width, height: 1000 });
      await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
      await expect(row().getByRole('button').first()).toBeVisible();
      expect((await row().locator('.table-action-cell .ant-btn').first().boundingBox())!.height).toBeGreaterThanOrEqual(width < 768 || coarsePointer ? 44 : 32);
      {
        const more = row().getByRole('button', { name: '查看全部 30 项技能' });
        await more.click();
        const panel = page.getByRole('region', { name: '全部核心技能' });
        await expect(panel).toBeVisible();
        await expect.poll(async () => {
          const bounds = (await panel.boundingBox())!;
          return bounds.x >= 0 && bounds.x + bounds.width <= width;
        }).toBe(true);
        await panel.getByRole('button', { name: '收起' }).click();
        await expect(more).toBeFocused();
      }
    }
    expect((await new AxeBuilder({ page }).include('.talent-ledger-card').analyze()).violations).toEqual([]);
    await page.screenshot({ path: testInfo.outputPath(path.includes('todo') ? 'todo.png' : 'pool.png'), fullPage: true });
  }
});
