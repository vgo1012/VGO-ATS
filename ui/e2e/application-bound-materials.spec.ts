import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('直接推荐未排期也能查看绑定简历和 JD', async ({ page }, testInfo) => {
  await reviewWorkspace(page);
  const application = { id: 'app-bound', candidateId: 'person-a', jdId: 'jd-a', jdVersionId: 'bound-jd', resumeVersionId: 'bound-resume', recruitmentType: 'INTERNAL', currentStage: 'PATH_SELECTION', ownerUserId: 'hr-a', terminalAt: null };
  const requested: string[] = [];
  await page.route('**/api/v1/**', route => {
    const url = new URL(route.request().url()), path = url.pathname.replace('/api/v1', '');
    if (path === '/applications/app-bound') return route.fulfill({ json: { application, collaboratorUserIds: [], workflowSteps: [], decisions: [], offerStatusRecords: [], elapsedSeconds: 60 } });
    if (path === '/interviews') return route.fulfill({ json: { items: [], hasMore: false, nextCursor: null } });
    if (path === '/resume-version-references') { requested.push(url.search); return route.fulfill({ json: [{ id: 'bound-resume', versionNo: 2, originalFileName: '推荐简历.pdf' }] }); }
    if (path === '/jd-versions/bound-jd') return route.fulfill({ json: { id: 'bound-jd', versionNo: 3, title: '绑定岗位', rawText: '推荐时 JD 原文，保持历史版本。' } });
    if (path === '/resume-versions/bound-resume/download-authorizations') { requested.push(path); return route.fulfill({ json: { token: 'bound-grant' } }); }
    if (path === '/download-authorizations/bound-grant') return route.fulfill({ contentType: 'application/pdf', headers: { 'content-disposition': 'attachment; filename=resume.pdf' }, body: Buffer.from('JVBERi0xLjQKMSAwIG9iago8PCAvVHlwZSAvQ2F0YWxvZyAvUGFnZXMgMiAwIFIgPj4KZW5kb2JqCjIgMCBvYmoKPDwgL1R5cGUgL1BhZ2VzIC9LaWRzIFszIDAgUl0gL0NvdW50IDEgPj4KZW5kb2JqCjMgMCBvYmoKPDwgL1R5cGUgL1BhZ2UgL1BhcmVudCAyIDAgUiAvTWVkaWFCb3ggWzAgMCA0MDAgNTAwXSAvUmVzb3VyY2VzIDw8IC9Gb250IDw8IC9GMSA0IDAgUiA+PiA+PiAvQ29udGVudHMgNSAwIFIgPj4KZW5kb2JqCjQgMCBvYmoKPDwgL1R5cGUgL0ZvbnQgL1N1YnR5cGUgL1R5cGUxIC9CYXNlRm9udCAvSGVsdmV0aWNhID4+CmVuZG9iago1IDAgb2JqCjw8IC9MZW5ndGggNTMgPj4Kc3RyZWFtCkJUIC9GMSAxNiBUZiAzMCA0NDAgVGQgKEJvdW5kIHJlc3VtZSB2ZXJzaW9uIDIpIFRqIEVUCmVuZHN0cmVhbQplbmRvYmoKeHJlZgowIDYKMDAwMDAwMDAwMCA2NTUzNSBmIAowMDAwMDAwMDA5IDAwMDAwIG4gCjAwMDAwMDAwNTggMDAwMDAgbiAKMDAwMDAwMDExNSAwMDAwMCBuIAowMDAwMDAwMjQxIDAwMDAwIG4gCjAwMDAwMDAzMTEgMDAwMDAgbiAKdHJhaWxlcgo8PCAvU2l6ZSA2IC9Sb290IDEgMCBSID4+CnN0YXJ0eHJlZgo0MTQKJSVFT0YK', 'base64') });
    return route.fallback();
  });
  await page.goto('/recruitment?application=app-bound');
  const detail = page.getByRole('dialog', { name: '招聘活动详情', exact: true });
  await expect(detail.getByText('暂无面试材料')).toHaveCount(0);
  await detail.getByRole('button', { name: '查看简历与 JD' }).click();
  const materials = page.getByRole('dialog', { name: '招聘材料', exact: true });
  await expect(materials.getByText('推荐时 JD 原文，保持历史版本。')).toBeVisible();
  await expect(materials.locator('iframe')).toHaveAttribute('src', /^blob:/);
  expect(requested).toContain('?resumeVersionIds=bound-resume');
  expect(requested).toContain('/resume-versions/bound-resume/download-authorizations');
  expect(await materials.evaluate(node => node.scrollWidth - node.clientWidth)).toBeLessThanOrEqual(1);
  await page.screenshot({ path: testInfo.outputPath('bound-materials.png'), animations: 'disabled' });
});
