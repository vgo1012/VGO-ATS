import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';
import type { ResumeFactBundle, ResumeFactsCommand } from '../src/talent/api';

const empty = { items: [], hasMore: false, nextCursor: null };
const person = { id: 'person-a', fullName: '受控事实候选人', primaryEmail: 'controlled@example.com' as string | null, primaryPhone: '13800001234' as string | null, ownerUserId: 'hr-a', ownerDepartmentId: 'dept-a', status: 'ACTIVE', version: 3, createdAt: '2026-09-07T01:00:00Z', updatedAt: '2026-09-07T01:00:00Z' };

function problem(status: number, detail: string, code: string) {
  return { status, title: status === 409 ? '事实快照已变化' : '保存失败', detail, code, recoverable: status >= 500 };
}

function initialFacts(): ResumeFactBundle {
  return {
    snapshot: { id: 'facts-1', resumeVersionId: 'resume-controlled', snapshotVersion: 1, sourceType: 'AI_PARSE', currentMarker: 'CURRENT', parseSnapshotId: null, previousSnapshotId: null, correctedByUserId: null, correctionReason: null, createdAt: '2026-09-07T01:00:00Z', fullName: person.fullName, email: person.primaryEmail, phone: person.primaryPhone, currentRole: '数据分析师', city: '杭州', age: 27, birthDate: '1999-03', gender: 'FEMALE', jobIntention: '数据分析与数据产品' },
    workExperiences: [{ id: 'work-row', factSnapshotId: 'facts-1', itemKey: 'work_original', employer: '保留的工作单位', roleName: '保留的工作岗位', startDate: '2020-09-01', startDateText: '2020-09', endDate: null, endDateText: 'PRESENT', descriptionText: '未经编辑的工作经历', responsibilitiesJson: '["数据治理"]', achievementsJson: '["质量提升"]' }],
    projectExperiences: [{ id: 'project-row', factSnapshotId: 'facts-1', itemKey: 'project_original', projectName: '保留的项目', roleName: '负责人', startDate: '2021-01-01', startDateText: '2021', endDate: '2021-06-01', endDateText: '2021-06', descriptionText: '未经编辑的项目经历', technologiesJson: '["Python"]', achievementsJson: '["按时上线"]' }],
    educations: [{ id: 'education-row', factSnapshotId: 'facts-1', itemKey: 'education_original', school: '原始大学', degreeName: '本科', major: '统计学', startDate: '2015-09-01', startDateText: '2015-09', endDate: '2019-06-01', endDateText: '2019-06', learningMode: 'FULL_TIME' }],
    skills: [{ id: 'skill-row', factSnapshotId: 'facts-1', itemKey: 'skill_original', skillName: 'SQL', levelName: '熟练', yearsValue: 5 }],
    simpleFacts: [
      { id: 'credential-row', factSnapshotId: 'facts-1', itemKey: 'credential_original', factType: 'CREDENTIAL', nameValue: 'PMP', levelValue: null, issuerValue: 'PMI', startDate: '2022-04-01', startDateText: '2022-04', endDate: null, endDateText: null },
      { id: 'language-row', factSnapshotId: 'facts-1', itemKey: 'language_original', factType: 'LANGUAGE', nameValue: '英语', levelValue: 'CET-6', issuerValue: null, startDate: null, endDate: null },
      { id: 'tag-row', factSnapshotId: 'facts-1', itemKey: 'industry_original', factType: 'INDUSTRY_TAG', nameValue: '互联网', levelValue: null, issuerValue: null, startDate: null, endDate: null },
    ],
  };
}

function factsFromCommand(command: ResumeFactsCommand, snapshotVersion: number): ResumeFactBundle {
  const original = initialFacts();
  return {
    ...original,
    snapshot: { ...original.snapshot, id: `facts-${snapshotVersion}`, snapshotVersion, sourceType: 'HUMAN_CORRECTION', previousSnapshotId: 'facts-1', fullName: command.candidate.fullName, email: command.candidate.email, phone: command.candidate.phone, currentRole: command.candidate.currentRole, city: command.candidate.city, age: command.candidate.age, birthDate: command.candidate.birthDate, gender: command.candidate.gender, jobIntention: command.candidate.jobIntention },
    educations: command.educations.map((value, index) => ({ id: `education-${index}`, factSnapshotId: `facts-${snapshotVersion}`, itemKey: value.id, school: value.school, degreeName: value.degree, major: value.major, startDate: value.startDate, endDate: value.endDate, learningMode: value.learningMode })),
    simpleFacts: [
      ...command.credentials.map((value, index) => ({ id: `credential-${index}`, factSnapshotId: `facts-${snapshotVersion}`, itemKey: value.id, factType: 'CREDENTIAL' as const, nameValue: value.name, levelValue: null, issuerValue: value.issuer, startDate: value.issuedDate, endDate: value.expiryDate })),
      ...command.languages.map((value, index) => ({ id: `language-${index}`, factSnapshotId: `facts-${snapshotVersion}`, itemKey: value.id, factType: 'LANGUAGE' as const, nameValue: value.name, levelValue: value.level, issuerValue: null, startDate: null, endDate: null })),
      original.simpleFacts[2],
    ],
  };
}

async function expectNoOverflow(page: Page, width: number) {
  await page.setViewportSize({ width, height: 1000 });
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
}

for (const width of [390, 768, 1280, 1440, 1920]) {
  test(`受控事实更正保留完整载荷并同步展示 ${width}`, async ({ page }, testInfo) => {
    test.setTimeout(90_000);
    await reviewWorkspace(page);
    await expectNoOverflow(page, width);
    let facts = initialFacts();
    let candidate = { ...person };
    let writes = 0;
    const submitted: ResumeFactsCommand[] = [];

    await page.route('**/api/v1/candidates/person-a', route => route.fulfill({ json: candidate }));
    await page.route('**/api/v1/candidates?**', route => route.fulfill({ json: { ...empty, items: [candidate] } }));
    await page.route('**/api/v1/candidates/person-a/resume-versions?**', route => route.fulfill({ json: { ...empty, items: [{ id: 'resume-controlled', versionNo: 2, originalFileName: '受控事实简历.pdf', createdAt: '2026-09-07T01:00:00Z' }] } }));
    await page.route('**/api/v1/resume-versions/resume-controlled/facts', route => route.fulfill({ json: facts }));
    await page.route('**/api/v1/resume-versions/resume-controlled/extracted-text', route => route.fulfill({ json: { pages: [], pageCount: 0, coverageRatio: 0 } }));
    await page.route('**/api/v1/candidate-resume-summaries?**', route => route.fulfill({ json: [{ candidateId: candidate.id, factsStatus: 'READY', facts, resumeVersion: { id: 'resume-controlled' }, source: null }] }));
    await page.route('**/api/v1/fact-corrections', route => {
      writes++;
      const body = route.request().postDataJSON() as { resumeVersionId: string; baseFactSnapshotId: string; facts: ResumeFactsCommand };
      expect(body.resumeVersionId).toBe('resume-controlled');
      expect(body.baseFactSnapshotId).toBe('facts-1');
      submitted.push(body.facts);
      if (writes === 1) return route.fulfill({ status: 503, contentType: 'application/problem+json', json: problem(503, '保存暂时失败，请重试。', 'UNAVAILABLE') });
      if (writes === 2) return route.fulfill({ status: 409, contentType: 'application/problem+json', json: problem(409, '事实快照已被其他更正更新，请重新打开后核对。', 'FACT_SNAPSHOT_VERSION_CONFLICT') });
      facts = factsFromCommand(body.facts, 2);
      candidate = { ...candidate, fullName: body.facts.candidate.fullName ?? candidate.fullName, primaryEmail: body.facts.candidate.email, primaryPhone: body.facts.candidate.phone };
      return route.fulfill({ status: 201, json: { correctionId: 'correction-2', factSnapshotId: 'facts-2', snapshotVersion: 2, affectedCapabilities: [] } });
    });

    await page.goto('/talents');
    await page.getByRole('button', { name: '详情与版本', exact: true }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByRole('button', { name: 'HR 更正', exact: true }).click();
    const form = dialog.getByRole('region', { name: 'HR 更正' });
    await expect(form.getByRole('button', { name: '原始大学 · 本科 · 统计学', exact: true })).toBeVisible();
    await expect(form.getByRole('button', { name: 'PMP', exact: true })).toBeVisible();
    await expect(form.getByRole('button', { name: '英语', exact: true })).toBeVisible();
    await expect(form.getByLabel('更正原因', { exact: true })).toHaveValue('人工校正');
    expect((await new AxeBuilder({ page }).include('[aria-label="HR 更正"]').analyze()).violations).toEqual([]);
    await form.getByRole('button', { name: '原始大学 · 本科 · 统计学', exact: true }).click();
    await form.getByLabel('学校', { exact: true }).scrollIntoViewIfNeeded();
    await page.screenshot({ path: testInfo.outputPath(`controlled-correction-input-${width}.png`), fullPage: true, animations: 'disabled' });
    await form.getByLabel('学校', { exact: true }).fill('更新大学');
    await form.getByLabel('专业', { exact: true }).fill('人工智能');
    await form.getByLabel('城市', { exact: true }).fill('上海');
    await form.getByLabel('求职意向', { exact: true }).fill('AI 产品经理');
    await form.getByRole('button', { name: '确认更新教育经历', exact: true }).click();
    await form.getByLabel('学校', { exact: true }).fill('新增研究院');
    await form.getByLabel('学历', { exact: true }).fill('硕士');
    await form.getByLabel('专业', { exact: true }).fill('机器学习');
    await form.getByRole('button', { name: '添加教育经历', exact: true }).click();
    await form.getByRole('button', { name: 'PMP', exact: true }).click();
    await form.getByLabel('证书名称', { exact: true }).fill('PRINCE2');
    await form.getByRole('button', { name: '确认更新证书', exact: true }).click();
    await form.getByLabel('证书名称', { exact: true }).fill('云原生认证;数据治理认证');
    await form.getByRole('button', { name: '添加证书', exact: true }).click();
    await form.getByRole('button', { name: '英语', exact: true }).locator('xpath=..').getByRole('button', { name: '关闭', exact: true }).click();
    await form.getByRole('button', { name: '保存更正', exact: true }).click();
    await expect(page.getByText('保存暂时失败，请重试。', { exact: true })).toBeVisible();
    await expect(form.getByRole('button', { name: '更新大学 · 本科 · 人工智能', exact: true })).toBeVisible();
    await expect(form.getByRole('button', { name: '新增研究院 · 硕士 · 机器学习', exact: true })).toBeVisible();

    await form.getByRole('button', { name: '保存更正', exact: true }).click();
    await expect(page.getByText('事实快照已被其他更正更新，请重新打开后核对。', { exact: true })).toBeVisible();
    await expect(form.getByLabel('城市', { exact: true })).toHaveValue('上海');
    await expect(form.getByRole('button', { name: '取消更正', exact: true })).toBeEnabled();
    await form.getByRole('button', { name: '取消更正', exact: true }).click();
    await expect(dialog.getByRole('region', { name: '关键资料' })).toBeVisible();
    expect(writes).toBe(2);

    await dialog.getByRole('button', { name: 'HR 更正', exact: true }).click();
    const reopened = dialog.getByRole('region', { name: 'HR 更正' });
    await reopened.getByRole('button', { name: '原始大学 · 本科 · 统计学', exact: true }).click();
    await reopened.getByLabel('学校', { exact: true }).fill('更新大学');
    await reopened.getByLabel('专业', { exact: true }).fill('人工智能');
    await reopened.getByLabel('城市', { exact: true }).fill('上海');
    await reopened.getByLabel('求职意向', { exact: true }).fill('AI 产品经理');
    await reopened.getByRole('button', { name: '确认更新教育经历', exact: true }).click();
    await reopened.getByLabel('学校', { exact: true }).fill('新增研究院');
    await reopened.getByLabel('学历', { exact: true }).fill('硕士');
    await reopened.getByLabel('专业', { exact: true }).fill('机器学习');
    await reopened.getByRole('button', { name: '添加教育经历', exact: true }).click();
    await reopened.getByRole('button', { name: 'PMP', exact: true }).click();
    await reopened.getByLabel('证书名称', { exact: true }).fill('PRINCE2');
    await reopened.getByRole('button', { name: '确认更新证书', exact: true }).click();
    await reopened.getByLabel('证书名称', { exact: true }).fill('云原生认证;数据治理认证');
    await reopened.getByRole('button', { name: '添加证书', exact: true }).click();
    await reopened.getByRole('button', { name: '英语', exact: true }).locator('xpath=..').getByRole('button', { name: '关闭', exact: true }).click();
    await reopened.getByRole('button', { name: '保存更正', exact: true }).focus();
    await page.keyboard.press('Enter');

    await expect(dialog.getByRole('tab', { name: '受控条件', exact: true })).toBeVisible();
    await dialog.getByRole('tab', { name: '受控条件', exact: true }).focus();
    await page.keyboard.press('Enter');
    const controlled = dialog.getByRole('tabpanel', { name: '受控条件' });
    await expect(controlled).toContainText('更新大学 本科 人工智能');
    await expect(controlled).toContainText('新增研究院 硕士 机器学习');
    await expect(controlled).toContainText('PRINCE2；云原生认证');
    await expect(controlled).toContainText('未识别');
    await page.screenshot({ path: testInfo.outputPath(`controlled-correction-${width}.png`), fullPage: true, animations: 'disabled' });
    await dialog.getByRole('tab', { name: '履约与意向', exact: true }).click();
    const fulfillment = dialog.getByRole('tabpanel', { name: '履约与意向' });
    await expect(fulfillment).toContainText('上海');
    await expect(fulfillment).toContainText('AI 产品经理');
    await expectNoOverflow(page, width);
    expect((await new AxeBuilder({ page }).include('.candidate-detail-content').analyze()).violations).toEqual([]);

    const command = submitted.at(-1)!;
    expect(command.workExperiences).toEqual([{ id: 'work_original', employer: '保留的工作单位', role: '保留的工作岗位', startDate: '2020-09', endDate: 'PRESENT', description: '未经编辑的工作经历', responsibilities: ['数据治理'], achievements: ['质量提升'] }]);
    expect(command.projectExperiences).toEqual([{ id: 'project_original', projectName: '保留的项目', role: '负责人', startDate: '2021', endDate: '2021-06', description: '未经编辑的项目经历', technologies: ['Python'], achievements: ['按时上线'] }]);
    expect(command.educations[0]).toMatchObject({ id: 'education_original', school: '更新大学', degree: '本科', major: '人工智能', startDate: '2015-09', endDate: '2019-06' });
    expect(command.educations[1]).toMatchObject({ school: '新增研究院', degree: '硕士', major: '机器学习' });
    expect(command.credentials).toEqual(expect.arrayContaining([{ id: 'credential_original', name: 'PRINCE2', issuer: 'PMI', issuedDate: '2022-04', expiryDate: null }, expect.objectContaining({ name: '云原生认证' }), expect.objectContaining({ name: '数据治理认证' })]));
    expect(command.languages).toEqual([]);
    await dialog.getByRole('button', { name: 'HR 更正', exact: true }).click();
    const finalForm = dialog.getByRole('region', { name: 'HR 更正' });
    await expect(finalForm.getByRole('button', { name: '更新大学 · 本科 · 人工智能', exact: true })).toBeVisible();
    await expect(finalForm.getByLabel('城市', { exact: true })).toHaveValue('上海');
    await expect(finalForm.getByLabel('求职意向', { exact: true })).toHaveValue('AI 产品经理');
    await finalForm.getByRole('button', { name: '取消更正', exact: true }).click();
    expect(writes).toBe(3);
  });
}
