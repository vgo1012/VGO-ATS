import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('招聘流程紧凑布局使用真实日期、岗位筛选、轮次及分页', async ({ page }, testInfo) => {
  await reviewWorkspace(page);
  const queries: URLSearchParams[] = [];
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/api/v1/applications?**', route => {
    const params = new URL(route.request().url()).searchParams;
    queries.push(params);
    return route.fulfill({ json: { items: Array.from({ length: 7 }, (_, index) => ({ id: `app-${index}`, candidateId: `candidate-${index}`, jdId: 'jd-a', jdVersionId: 'jd-v2', resumeVersionId: `resume-${index}`, recruitmentType: 'EXTERNAL', clientName: '华为云', currentStage: index === 5 ? 'PATH_SELECTION' : index === 6 ? 'OFFER' : 'IN_PROCESS', currentStepCode: 'INTERVIEW_ROUND_1', currentStepRoundNo: index + 1, currentStepStatus: 'IN_PROGRESS', activeInterviewRoundNos: index < 5 ? [index + 1] : [], ownerUserId: 'hr-a', updateTime: '2026-09-07T01:00:00Z' })), hasMore: !params.has('cursor'), nextCursor: params.has('cursor') ? null : 'next' } });
  });
  await page.route('**/api/v1/candidates/candidate-*', route => {
    const index = Number(route.request().url().split('-').at(-1));
    return route.fulfill({ json: { id: `candidate-${index}`, fullName: ['葛清旭', '贺懂', '何子恒', '康荣涛', '井朝', '待跟进候选人', '待入职候选人'][index], primaryPhone: `1380000000${index}` } });
  });
  await page.route('**/api/v1/resume-versions/resume-*/facts', route => route.fulfill({ json: { snapshot: { id: 'snapshot', resumeVersionId: 'resume-0', gender: route.request().url().includes('resume-0/') ? 'FEMALE' : 'MALE' }, workExperiences: [], projectExperiences: [], educations: [], skills: [], simpleFacts: [{ id: 'review', factType: 'UNKNOWN', nameValue: '证书有效期需要人工核对' }] } }));
  await page.route('**/api/v1/candidate-batch?*',route=>route.fulfill({json:new URL(route.request().url()).searchParams.getAll('candidateIds').map(id=>{
    const index=Number(id.split('-').at(-1));
    return {id,fullName:['葛清旭','贺懂','何子恒','康荣涛','井朝','待跟进候选人','待入职候选人'][index],primaryPhone:`1380000000${index}`};
  })}));
  await page.route('**/api/v1/resume-version-fact-summaries?*',route=>route.fulfill({json:new URL(route.request().url()).searchParams.getAll('resumeVersionIds').map(resumeVersionId=>({resumeVersionId,facts:{snapshot:{id:'snapshot',resumeVersionId,gender:resumeVersionId==='resume-0'?'FEMALE':'MALE'},workExperiences:[],projectExperiences:[],educations:[],skills:[],simpleFacts:[{id:'review',factType:'UNKNOWN',nameValue:'证书有效期需要人工核对'}]}}))}));
  await page.goto('/recruitment');
  await expect(page.getByRole('button', { name: '葛清旭 女', exact: true })).toBeVisible();
  await expect(page.getByRole('img', { name: '女', exact: true })).toHaveCount(1);
  await expect(page.getByRole('img', { name: '男', exact: true })).toHaveCount(6);
  expect(queries.at(-1)?.get('from')).toBe('2026-09-06T16:00:00.000Z');
  expect(queries.at(-1)?.get('to')).toBe('2026-09-13T16:00:00.000Z');
  await expect(page.locator('.pipeline-metrics')).toHaveCount(0);
  await expect(page.getByRole('button', { name: '创建招聘活动', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '职位与规则', exact: true })).toHaveCount(0);
  await expect(page.getByText('更多条件', { exact: true })).toHaveCount(0);
  await expect(page.locator('.ledger-heading')).toHaveCSS('border-bottom-width', '0px');
  await expect(page.getByRole('heading', { name: '招聘流程', exact: true })).toHaveCSS('font-weight', '600');
  await expect(page.getByRole('heading', { name: '招聘流程', exact: true })).toHaveCSS('font-size', page.viewportSize()!.width <= 640 ? '26px' : '30px');
  await expect(page.locator('.recruitment-rounds').first().getByRole('button')).toHaveText(['1', '2', '3', '4', '5', '6']);
  await expect(page.locator('.recruitment-rounds').last().getByRole('button')).toHaveText(['1', '2', '3', '4', '5', '6']);
  await page.getByRole('button', { name: '复核 1 项', exact: true }).first().click();
  const review = page.getByRole('dialog', { name: '简历待复核', exact: true });
  await expect(review).toBeVisible();
  await expect(review.getByText('1 项待核实', { exact: true })).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(review).toHaveCount(0);
  await page.getByRole('button', { name: '下一页', exact: true }).click();
  await expect.poll(() => queries.at(-1)?.get('cursor')).toBe('next');
  await page.getByRole('button', { name: '上一周', exact: true }).click();
  await expect.poll(() => queries.at(-1)?.get('from')).toBe('2026-08-30T16:00:00.000Z');
  expect(queries.at(-1)?.has('cursor')).toBe(false);
  await page.getByRole('button', { name: /^本\s*周$/ }).click();
  const queryCountBeforeJob = queries.length;
  await page.getByRole('combobox', { name: '搜索应聘岗位', exact: true }).click();
  const jobRequest = page.waitForRequest(request => {
    const url = new URL(request.url());
    return url.pathname === '/api/v1/applications' && url.searchParams.get('jdId') === 'jd-a';
  });
  await page.locator('.jd-selector-popup .ant-select-item-option-content').filter({ hasText: '高级前端工程师（招聘平台与复杂业务系统方向）' }).click();
  await jobRequest;
  await expect.poll(() => queries.slice(queryCountBeforeJob).some(query => query.get('jdId') === 'jd-a')).toBe(true);
  await page.reload();
  await expect(page.getByRole('combobox', { name: '搜索应聘岗位', exact: true }).locator('..')).toContainText('高级前端工程师（招聘平台与复杂业务系统方向）');
  await page.getByRole('button', { name: '重置', exact: true }).click();
  await expect.poll(() => queries.at(-1)?.has('jdId')).toBe(false);
  await expect(page.getByRole('combobox', { name: '负责人', exact: true })).toHaveCount(0);
  await expect(page.locator('.recruitment-filter-grid').getByRole('combobox')).toHaveCount(3);
  for (const [label, lifecycle, stage] of [['已完成','ALL','ONBOARDED'],['已取消','ALL','TERMINATED'],['全部状态','ALL',null],['进行中','ACTIVE',null]] as const) {
    await page.getByRole('combobox', { name: '任务状态', exact: true }).click();
    await expect(page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option-content')).toHaveText(['全部状态','进行中','已完成','已取消']);
    await page.locator('.ant-select-item-option-content').getByText(label, { exact: true }).click();
    await expect.poll(() => queries.at(-1)?.get('lifecycle')).toBe(lifecycle);
    await expect.poll(() => queries.at(-1)?.get('stage')).toBe(stage);
    await page.reload();
    await expect(page.getByRole('combobox', { name: '任务状态', exact: true }).locator('..')).toContainText(label);
  }
  for (const width of [390, 768, 1280, 1440, 1920]) {
    await page.setViewportSize({ width, height: 900 });
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    for (const stage of await page.locator('.recruitment-stage').all()) {
      const stageBox = await stage.boundingBox();
      const options = await stage.locator('.recruitment-stage-options').boundingBox();
      const detail = await stage.locator('.recruitment-rounds, .recruitment-stage-detail').boundingBox();
      expect(Math.abs(options!.x + options!.width / 2 - stageBox!.x - stageBox!.width / 2)).toBeLessThanOrEqual(1);
      expect(Math.abs(detail!.x + detail!.width / 2 - stageBox!.x - stageBox!.width / 2)).toBeLessThanOrEqual(1);
      if (width >= 768) {
        const tag = await stage.locator('.stage-pending').boundingBox();
        const button = await stage.getByRole('button', { name: '黑名单', exact: true }).boundingBox();
        expect(button!.height).toBe(tag!.height);
        const buttons=await stage.locator('.recruitment-stage-options > button').evaluateAll(elements=>elements.reduce((sum,element)=>sum+element.getBoundingClientRect().width,0));
        if(options!.width>=buttons+40)expect(button!.y).toBe(tag!.y);
        else {
          expect(button!.y).toBeGreaterThanOrEqual(tag!.y);
          expect(button!.y+button!.height).toBeLessThanOrEqual(options!.y+options!.height+1);
        }
      }
    }
    await page.screenshot({ path: testInfo.outputPath(`recruitment-${width}.png`), fullPage: true, animations: 'disabled' });
  }
  expect((await new AxeBuilder({ page }).include('.pipeline-workspace').analyze()).violations).toEqual([]);
  expect(errors).toEqual([]);
});


test('招聘列表仅展示甲方公司事实，轮次不混入面试类型', async ({ page }) => {
  await reviewWorkspace(page);
  await page.route('**/api/v1/applications?**', route => route.fulfill({ json: {
    items: Array.from({ length: 3 }, (_, index) => ({
      id: `app-${index}`, candidateId: 'person-a', jdId: 'jd-a', resumeVersionId: 'resume-a',
      recruitmentType: index === 0 ? 'INTERNAL' : 'EXTERNAL', clientName: index === 2 ? null : '华为云',
      currentStage: 'IN_PROCESS', currentStepCode: 'INTERVIEW_ROUND_1', currentStepRoundNo: index + 1, activeInterviewRoundNos: [index + 1],
      currentInterviewType: index === 0 ? 'EXTERNAL' : 'INTERNAL', currentStepStatus: 'IN_PROGRESS', ownerUserId: 'hr-a',
    })), hasMore: false, nextCursor: null,
  } }));
  await page.goto('/recruitment');
  const rows = page.locator('.ant-table-tbody > .ant-table-row');
  await expect(page.getByRole('columnheader', { name: '面试类型', exact: true })).toHaveCount(0);
  await expect(rows.nth(0).getByRole('cell').nth(4)).toHaveText('—');
  await expect(rows.nth(1).getByRole('cell').nth(4)).toHaveText('华为云');
  await expect(rows.nth(2).getByRole('cell').nth(4)).toHaveText('甲方信息待核对');
  await expect(rows.nth(0)).not.toContainText('外部面试');
  await expect(rows.nth(1)).not.toContainText('内部面试');
  await expect(rows.nth(2).getByRole('button', { name: '第 3 轮面试', exact: true })).toHaveAttribute('data-active', 'true');
  expect((await new AxeBuilder({ page }).include('.pipeline-workspace').analyze()).violations).toEqual([]);
});


test('招聘流程筛选区与时间范围等高且搜索与筛选框对齐', async ({ page }, testInfo) => {
  await reviewWorkspace(page);
  const queries: URLSearchParams[] = [];
  await page.route('**/api/v1/applications?**', route => {
    queries.push(new URL(route.request().url()).searchParams);
    return route.fulfill({ json: { items: [], hasMore: false, nextCursor: null } });
  });
  await page.goto('/recruitment');
  await page.getByRole('combobox', { name: '任务状态', exact: true }).click();
  await page.locator('.ant-select-item-option-content').getByText('已完成', { exact: true }).click();
  await expect.poll(() => queries.at(-1)?.get('stage')).toBe('ONBOARDED');
  const from = queries.at(-1)?.get('from');
  const to = queries.at(-1)?.get('to');
  await page.getByRole('button', { name: '重置', exact: true }).click();
  await expect(page).not.toHaveURL(/stage=ONBOARDED/);
  await page.reload();
  await expect.poll(() => queries.at(-1)?.has('stage')).toBe(false);
  expect(queries.at(-1)?.get('from')).toBe(from);
  expect(queries.at(-1)?.get('to')).toBe(to);
  for (const width of [390, 768, 1280, 1440, 1920]) {
    await page.setViewportSize({ width, height: 900 });
    const layout = await page.locator('.recruitment-toolbars').evaluate(element => {
      const rect = (selector: string) => element.querySelector(selector)!.getBoundingClientRect().toJSON();
      const typography = (selector: string) => {
        const style = getComputedStyle(element.querySelector(selector)!);
        return [style.fontFamily, style.fontSize, style.fontWeight, style.lineHeight, style.color];
      };
      return {
        date: rect('.recruitment-date-toolbar'), filter: rect('.recruitment-filter-card'),
        previous: rect('.recruitment-date-controls > button'),
        fields: Array.from(element.querySelectorAll('.recruitment-filter-grid > label')).map(field => field.getBoundingClientRect().toJSON()),
        title: rect('.recruitment-filter-card > b'), reset: rect('.recruitment-filter-card > button'),
        previousFont: typography('.recruitment-date-controls > button'),
        dateFont: typography('.recruitment-date-controls > b'), filterFont: typography('.recruitment-filter-card > b'),
      };
    });
    expect(layout.filterFont).toEqual(layout.dateFont);
    expect(layout.dateFont[1]).toBe(layout.previousFont[1]);
    await expect(page.getByRole('button', { name: '重置', exact: true })).toHaveClass(/ant-btn-primary/);
    if (width >= 1000) {
      expect(Math.abs(layout.date.height - layout.filter.height)).toBeLessThanOrEqual(1);
      expect(Math.abs(layout.fields[0]!.left - layout.previous.left)).toBeLessThanOrEqual(1);
      expect(layout.fields[0]!.left).toBeGreaterThan(layout.title.right);
      expect(layout.fields.at(-1)!.right).toBeLessThan(layout.reset.left);
      for (const field of layout.fields.slice(1)) {
        expect(Math.abs(field.width - layout.fields[1]!.width)).toBeLessThanOrEqual(1);
        expect(Math.abs(field.top - layout.fields[0]!.top)).toBeLessThanOrEqual(1);
      }
    }
    const search=page.getByRole('searchbox',{name:'搜索候选人姓名'});
    const fits=await search.evaluate(input=>{
      const style=getComputedStyle(input),canvas=document.createElement('canvas');
      const context=canvas.getContext('2d')!;context.font=style.font;
      return input.clientWidth-parseFloat(style.paddingLeft)-parseFloat(style.paddingRight)>=context.measureText((input as HTMLInputElement).placeholder).width;
    });
    expect(fits,`姓名搜索提示完整显示 @ ${width}px`).toBe(true);
    await search.fill('林雨桐');
    await expect.poll(()=>search.evaluate(input=>input.getBoundingClientRect().height-parseFloat(getComputedStyle(input).lineHeight)),{message:`姓名文字上下不裁切 @ ${width}px`}).toBeGreaterThanOrEqual(0);
    await search.screenshot({path:testInfo.outputPath(`search-text-${width}.png`)});
    await search.press('Enter');
    await expect.poll(()=>queries.at(-1)?.get('q')).toBe('林雨桐');
    await page.getByRole('button',{name:'重置',exact:true}).click();
    await expect(search).toHaveValue('');
    expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    await page.screenshot({ path: testInfo.outputPath(`recruitment-filters-${width}.png`), fullPage: true, animations: 'disabled' });
  }
  expect((await new AxeBuilder({ page }).include('.recruitment-toolbars').analyze()).violations).toEqual([]);
});
