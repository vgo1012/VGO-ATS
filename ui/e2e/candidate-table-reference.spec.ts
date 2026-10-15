import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('全部候选人列表姓名与联系方式居中，性别不影响对齐', async ({ page }, testInfo) => {
  test.setTimeout(60_000);
  await reviewWorkspace(page);
  const facts = { snapshot: { id: 'facts', resumeVersionId: 'resume-a', gender: 'FEMALE' as string | undefined, age: 28 }, workExperiences: [], projectExperiences: [], educations: [], skills: ['Linux', 'Nginx', 'Tomcat', 'MySQL', 'Docker', 'Kubernetes', 'TCP/IP五层模型', 'OSI七层模型', 'ACL', 'NAT', 'VLSM子网划分', 'OSPF', 'BGP', 'RIP', 'VLAN', 'Trunk', 'STP/RSTP', 'QoS流量调度', 'VRRP/HSRP', '企业WLAN组网', '防火墙策略', 'IPSec VPN', 'DHCP', 'IPS/IDS', 'WAF', '堡垒机', '安全日志分析', '华为/H3C设备维护', 'Shell脚本', 'Wireshark'].map((skillName, id) => ({ id: String(id), skillName })), simpleFacts: [] };
  await page.route('**/api/v1/candidate-resume-summaries?**', route => route.fulfill({ json: [{ candidateId: 'person-a', factsStatus: 'READY', facts, source: { sourceChannelName: '内推' }, resumeVersion: { id: 'resume-a', createdAt: '2026-09-07T01:00:00Z' } }] }));
  await page.route('**/api/v1/resume-version-fact-summaries?**', route => route.fulfill({ json: [{ resumeVersionId: 'resume-a', facts }] }));
  await page.route('**/api/v1/applications?**', route => route.fulfill({ json: { items: [{ id: 'app-a', candidateId: 'person-a', resumeVersionId: 'resume-a', jdId: 'jd-a', ownerUserId: 'hr-a', currentStage: 'OFFER', recruitmentType: 'INTERNAL' }], hasMore: false } }));
  await page.route('**/api/v1/talent-search?**',route=>route.fulfill({json:{items:[{candidateId:'person-a',fullName:'林雨桐',gender:facts.snapshot.gender,skills:[],conditionMatches:[]}],hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/candidate-pool-statuses?**',route=>route.fulfill({json:[{candidateId:'person-a',resumeVersionId:'resume-a',jobContexts:[{jdId:'jd-a',jdVersionId:'jd-v2',title:'默认岗位',versionNo:2,resumeVersionId:'resume-a',status:'ADVANCED',applicationId:'app-a'}]}]}));

  const row = () => page.locator('.ant-table-tbody > tr.ant-table-row').first();
  for (const width of [390, 1280, 1440, 1920]) {
    facts.snapshot.gender = width === 390 ? 'MALE' : width === 1280 ? 'FEMALE' : undefined;
    await page.setViewportSize({ width, height: 1000 });
    await page.goto('/recruitment');
    await expect(row()).toContainText('林雨桐');
    const measure = () => row().evaluate(element => {
      const name = element.querySelector('.table-link')!;
      return { nameFont: getComputedStyle(name).fontSize, bodyFont: getComputedStyle(element.querySelector('td')!).fontSize };
    });
    const reference = await measure();
    for (const path of ['/recruitment', '/talents', '/talents?view=todo', '/shared-library', '/onboarded']) {
      await page.goto(path);
      await expect(row()).toContainText('林雨桐');
      await page.evaluate(() => document.fonts.ready);
      const actual = await measure();
      expect(actual.nameFont).toBe(reference.nameFont);
      expect(actual.bodyFont).toBe(reference.bodyFont);
      const alignment = await row().locator('.talent-identity-cell').evaluate(cell => {
        const center = (element: Element) => { const box = element.getBoundingClientRect(); return box.x + box.width / 2; };
        return {
          name: center(cell.querySelector('.table-link > span:not(.anticon)')!) - center(cell),
          contact: center(cell.querySelector('.talent-contact')!) - center(cell),
        };
      });
      expect(Math.abs(alignment.name), `${path} @ ${width}px: 姓名`).toBeLessThanOrEqual(1);
      expect(Math.abs(alignment.contact), `${path} @ ${width}px: 联系方式`).toBeLessThanOrEqual(1);
      await expect(row().locator('.table-link > .anticon')).toHaveCount(facts.snapshot.gender ? 1 : 0);
      if (facts.snapshot.gender) {
        const name = (await row().locator('.table-link > span:not(.anticon)').boundingBox())!;
        const icon = (await row().locator('.table-link > .anticon').boundingBox())!;
        expect(icon.x - name.x - name.width, `${path} @ ${width}px: 性别图标与姓名间距`).toBeGreaterThanOrEqual(4);
        expect(Math.abs(icon.y+icon.height/2-name.y-name.height/2), `${path} @ ${width}px: 性别图标垂直对齐`).toBeLessThanOrEqual(1);
      }
      await row().locator('.table-link').focus();
      await expect(row().locator('.table-link')).toBeFocused();
      await expect(row().locator('.talent-contact')).toHaveCSS('font-size', '12px');
      await expect(page.getByRole('columnheader', { name: '候选人', exact: true })).toHaveCSS('text-align', 'center');
      expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
      await page.screenshot({ path: testInfo.outputPath(`${path.replace(/[^a-z]/g, '-')}-${width}.png`), animations: 'disabled' });
      // 用长姓名验证同一单元格的截断边界，不改变业务数据。
      await row().locator('.table-link > span:not(.anticon)').evaluate(element => { element.textContent = '测试超长候选人姓名用于验证居中与省略显示'; });
      const cell = (await row().locator('.talent-identity-cell').boundingBox())!;
      const link = (await row().locator('.table-link').boundingBox())!;
      expect(link.x).toBeGreaterThanOrEqual(cell.x);
      expect(link.x + link.width).toBeLessThanOrEqual(cell.x + cell.width);

    }
  }
});
