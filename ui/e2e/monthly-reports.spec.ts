import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

const actor = {
  userId: "director-1",
  displayName: "人力负责人",
  workEmail: "director@example.com",
  homeDepartmentId: "hr",
  roles: ["HR_DIRECTOR"],
  permissions: ["reporting.monthly-report.manage"],
  superAdmin: false,
  mustChangePassword: false,
  version: "1",
};
const summary = {
  id: "report-1",
  month: "2026-08",
  asOfDate: "2026-09-14",
  status: "READY",
  version: "1",
  createTime: "2026-09-14T01:00:00Z",
  createName: "人力负责人",
  offerCount: 171,
  totalScore: 122.75,
  errorCode: null,
  errorDetail: null,
};
const snapshot = {
  ruleVersion: "2026.09",
  parserVersion: "1",
  templateVersion: "1",
  reportMonth: "2026-08",
  dataAsOf: "2026-09-14",
  scope: "DOMESTIC",
  ready: true,
  offerSummary: {
    totalCount: 171,
    scoredCount: 150,
    totalScore: 122.75,
    statusCounts: {
      已入职offer: 96,
      已接受offer: 52,
      已拒绝offer: 21,
      已发offer: 2,
    },
    rejectedRate: 12.3,
    nonRejectedRate: 87.7,
    acceptedOrOnboardedRate: 86.5,
    onboardedRate: 56.1,
    dateFrom: "2026-08-01",
    dateTo: "2026-09-01",
    excludedCounts: {},
  },
  demandSummary: {
    approvedDemandCount: 3,
    approvedHeadcount: 8,
    monthAppliedDemandCount: 0,
    monthAppliedHeadcount: 0,
    monthExpectedDemandCount: 3,
    monthExpectedHeadcount: 8,
  },
  dimensions: [
    {
      dimension: "group",
      key: "示例组甲",
      group: null,
      count: 100,
      score: 70,
      averageScore: 0.7,
      statusCounts: {},
      countShare: 58.5,
      scoreShare: 57,
    },
  ],
  analysis: [
    {
      ruleId: "TOP_GROUP",
      metricKey: "count",
      dimension: "group",
      value: 100,
      text: "本月示例组甲发放 100 条，为各小组最高。",
      recommendation: null,
    },
  ],
  diagnostics: [],
};

test.beforeEach(async ({ page }) => {
  await page.route("**/api/v1/me", (route) => route.fulfill({ json: actor }));
  await page.route("**/api/v1/auth/csrf", (route) =>
    route.fulfill({
      json: {
        headerName: "X-CSRF-TOKEN",
        parameterName: "_csrf",
        token: "fixture",
      },
    }),
  );
  await page.route("**/api/v1/monthly-reports?*", (route) =>
    route.fulfill({ json: { items: [summary], nextCursor: null } }),
  );
  await page.route("**/api/v1/monthly-reports/report-1", (route) =>
    route.fulfill({
      json: {
        ...summary,
        taskId: null,
        progressPercent: 100,
        remarks: null,
        files: [],
        diagnostics: [
          {
            severity: "WARNING",
            code: "MISSING_RECOMMENDER",
            message: "推荐人字段有 2 条缺失",
            source: "offer.xlsx",
            sheet: "Sheet1",
            count: 2,
          },
        ],
        snapshot,
      },
    }),
  );
});

for (const width of [390, 1440])
  test(`预览独立需求指标并生成月报 ${width}`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    let generateBody: unknown;
    let uploadFields: string[] = [];
    await page.route(
      "**/api/v1/monthly-reports/report-1/generate",
      async (route) => {
        generateBody = route.request().postDataJSON();
        await route.fulfill({
          json: {
            ...summary,
            status: "QUEUED",
            version: "2",
            taskId: "task-1",
            progressPercent: 0,
            remarks: "关注拒绝原因",
            files: [],
            diagnostics: [],
            snapshot,
          },
        });
      },
    );
    await page.route("**/api/v1/monthly-reports", async (route) => {
      const multipartNames =
        (await route.request().postDataBuffer())
          ?.toString()
          .match(/name="[^"]+"/g) ?? [];
      uploadFields = multipartNames.filter((value) =>
        [
          'name="asOfDate"',
          'name="demandFile"',
          'name="month"',
          'name="offerFile"',
        ].includes(value),
      );
      await route.fulfill({
        status: 202,
        json: {
          ...summary,
          status: "VALIDATING",
          taskId: "task-2",
          progressPercent: 0,
          remarks: null,
          files: [],
          diagnostics: [],
          snapshot: null,
        },
      });
    });
    await page.goto("/monthly-reports");
    await expect(
      page.getByRole("heading", { name: "招聘月度汇报" }),
    ).toBeVisible();
    await expect(page.getByText("需求数据独立展示")).toBeVisible();
    await expect(page.getByText(/不计算招聘完成率或缺口/)).toBeVisible();
    await expect(page.getByText("接受及入职占比")).toBeVisible();
    await expect(page.getByText("86.5%")).toBeVisible();
    await expect(page.getByText("已入职占比")).toBeVisible();
    await expect(page.getByText("56.1%")).toBeVisible();
    if (width === 1440) {
      await page.screenshot({
        path: "test-results/monthly-reports-1440.png",
        fullPage: true,
        animations: "disabled",
      });
    }
    await page.getByLabel("管理备注").fill("关注拒绝原因");
    await page.getByLabel("确认校验警告").check();
    await page.getByRole("button", { name: /生成 Word 汇报/ }).click();
    await expect
      .poll(() => generateBody)
      .toEqual({
        version: "1",
        remarks: "关注拒绝原因",
        acknowledgeWarnings: true,
      });
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth - innerWidth,
      ),
    ).toBeLessThanOrEqual(1);
    await page
      .locator("input[type=file]")
      .nth(0)
      .setInputFiles({
        name: "offer.xlsx",
        mimeType:
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        buffer: Buffer.from("offer"),
      });
    await page
      .locator("input[type=file]")
      .nth(1)
      .setInputFiles({
        name: "demand.xlsx",
        mimeType:
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        buffer: Buffer.from("demand"),
      });
    await page.getByRole("button", { name: "上传并校验" }).click();
    await expect
      .poll(() => uploadFields.sort())
      .toEqual([
        'name="asOfDate"',
        'name="demandFile"',
        'name="month"',
        'name="offerFile"',
      ]);
  });

test("普通招聘专员即使误获权限也不能进入月报", async ({ page }) => {
  await page.unroute("**/api/v1/me");
  await page.route("**/api/v1/me", (route) =>
    route.fulfill({ json: { ...actor, roles: ["RECRUITER"] } }),
  );
  await page.goto("/monthly-reports");
  await expect(page.getByText("无权访问")).toBeVisible();
});

test("生成响应丢失后复用幂等键，修改备注后换新键", async ({ page }) => {
  const keys: string[] = [];
  let attempts = 0;
  await page.route(
    "**/api/v1/monthly-reports/report-1/generate",
    async (route) => {
      keys.push(route.request().headers()["idempotency-key"] ?? "");
      attempts++;
      if (attempts < 3) {
        await route.fulfill({
          status: 503,
          contentType: "application/problem+json",
          body: JSON.stringify({
            title: "响应暂不可用",
            status: 503,
            detail: "请求结果未知，请使用原操作重试",
            code: "TEMPORARY_FAILURE",
            recoverable: true,
          }),
        });
        return;
      }
      await route.fulfill({
        status: 202,
        json: {
          ...summary,
          status: "QUEUED",
          version: "2",
          taskId: "task-1",
          progressPercent: 0,
          retryable: false,
          remarks: "修改后的备注",
          files: [],
          diagnostics: [],
          snapshot,
        },
      });
    },
  );
  await page.goto("/monthly-reports");
  await page.getByLabel("管理备注").fill("原备注");
  await page.getByLabel("确认校验警告").check();
  const generate = page.getByRole("button", { name: /生成 Word 汇报/ });
  await generate.click();
  await expect(page.getByText("请求结果未知，请使用原操作重试")).toBeVisible();
  await generate.click();
  await expect.poll(() => keys.length).toBe(2);
  expect(keys[0]).toBeTruthy();
  expect(keys[1]).toBe(keys[0]);
  await page.getByLabel("管理备注").fill("修改后的备注");
  await generate.click();
  await expect.poll(() => keys.length).toBe(3);
  expect(keys[2]).not.toBe(keys[0]);
});

test("超级管理员无需额外权限即可从工作区进入月报", async ({ page }) => {
  await page.unroute("**/api/v1/me");
  await page.route("**/api/v1/me", (route) =>
    route.fulfill({ json: { ...actor, roles: [], permissions: [], superAdmin: true } }),
  );
  await page.route("**/api/v1/weight-profiles?*", (route) => route.fulfill({ json: { items: [], nextCursor: null } }));
  await page.route("**/api/v1/weight-profiles/count?*", (route) => route.fulfill({ json: { count: 0, nextCursor: null, hasMore: false } }));
  await page.goto("/settings/preferences");
  await expect(page.getByRole("button", { name: /月度汇报$/ })).toHaveCount(0);
  await expect(page.getByRole("navigation", { name: "设置导航" })).toBeVisible();
  const mobileNavigation = page.getByRole("combobox", { name: "切换设置页面" });
  if (await mobileNavigation.isVisible()) {
    await mobileNavigation.click();
    await page.getByText("月度汇报", { exact: true }).filter({ visible: true }).click();
  } else {
    await page.getByRole("link", { name: /月度汇报$/ }).click();
  }
  await expect(page).toHaveURL(/\/monthly-reports$/);
  await expect(page.getByRole("heading", { name: "招聘月度汇报" })).toBeVisible();
  await expect(page.getByRole("button", { name: "上传并校验" })).toBeVisible();
});

for (const width of [390, 1440]) test(`上传长文件名不重叠并显示校验进度 ${width}`, async ({ page }) => {
  await page.clock.setFixedTime(new Date('2026-09-14T10:00:00+08:00'));
  await page.setViewportSize({ width, height: 900 });
  await page.goto('/monthly-reports');
  await expect(page.getByLabel('报告月份', { exact: true })).toHaveValue('2026-08');
  const offer = page.getByRole('group', { name: 'Offer 明细 Excel', exact: true });
  const demand = page.getByRole('group', { name: '招聘需求 Excel', exact: true });
  const offerName = '10-2026年8月offer明细（国内招聘）2026.9.1-线上更新.xlsx';
  const demandName = '2-北森招聘需求-系统导出内容.xlsx';
  await offer.locator('input[type=file]').setInputFiles({ name: offerName, mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', buffer: Buffer.from('offer') });
  await demand.locator('input[type=file]').setInputFiles({ name: demandName, mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', buffer: Buffer.from('demand') });
  await expect.poll(async () => {
    const groupBox = await offer.boundingBox();
    const fileBox = await offer.getByText(offerName, { exact: true }).boundingBox();
    return Boolean(groupBox && fileBox && fileBox.y + fileBox.height <= groupBox.y + groupBox.height + 1);
  }).toBe(true);
  await expect.poll(() => offer.getByText(offerName, { exact: true }).evaluate(element => element.scrollWidth - element.clientWidth)).toBeLessThanOrEqual(1);
  const offerBox = await offer.boundingBox();
  const offerFileBox = await offer.getByText(offerName, { exact: true }).boundingBox();
  const demandBox = await demand.boundingBox();
  const demandFileBox = await demand.getByText(demandName, { exact: true }).boundingBox();
  const uploadButton = page.getByRole('button', { name: '上传并校验', exact: true });
  const buttonBox = await uploadButton.boundingBox();
  expect(offerFileBox!.y + offerFileBox!.height).toBeLessThanOrEqual(offerBox!.y + offerBox!.height + 1);
  if (width > 900) {
    expect(offerBox!.y).toBeCloseTo(demandBox!.y, 0);
    expect(offerBox!.x + offerBox!.width).toBeLessThanOrEqual(demandBox!.x);
  } else expect(offerBox!.y + offerBox!.height).toBeLessThanOrEqual(demandBox!.y);
  expect(demandFileBox!.y + demandFileBox!.height).toBeLessThanOrEqual(buttonBox!.y);
  await offer.scrollIntoViewIfNeeded();
  await page.screenshot({ path: `test-results/monthly-upload-${width}.png`, fullPage: true });
  await page.route('**/api/v1/monthly-reports', async route => {
    expect(route.request().postData()).toContain('2026-08');
    await route.fulfill({ status: 202, json: { ...summary, id: 'upload-new', status: 'VALIDATING', snapshot: null, diagnostics: [], files: [], progressPercent: 0 } });
  });
  await uploadButton.click();
  await expect(page.getByLabel('处理进度 0%')).toBeVisible();
});

test('历史零数据报告不能继续生成', async ({ page }) => {
  await page.route('**/api/v1/monthly-reports/report-1', route => route.fulfill({ json: { ...summary, files: [], diagnostics: [], snapshot: { ...snapshot, offerSummary: { ...snapshot.offerSummary, totalCount: 0 } } } }));
  await page.goto('/monthly-reports');
  await expect(page.getByText('所选月份没有 Offer 数据，不能生成汇报')).toBeVisible();
  await expect(page.getByRole('button', { name: /生成 Word 汇报/ })).toHaveCount(0);
  await expect(page.locator('.monthly-report-preview').getByText('可生成', { exact: true })).toHaveCount(0);
});

for (const width of [390, 768, 1280, 1440, 1920]) {
  test(`重复校验提示收起并在固定区域滚动 ${width}`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 900 });
    await page.route("**/api/v1/monthly-reports/report-1", (route) => route.fulfill({ json: {
      ...summary, files: [], snapshot,
      diagnostics: Array.from({ length: 75 }, (_, index) => ({
        severity: "WARNING", code: "STATUS_DATE_MISMATCH", count: 1,
        message: "Offer状态与入职日期不一致，统计仍以Offer状态为准",
        source: "offer", sheet: "Offer20260901115348", row: index + 2, column: "入职日期",
      })),
    } }));
    await page.goto("/monthly-reports");
    await expect(page.getByText("校验提示：75 项", { exact: true })).toBeVisible();
    const region = page.getByRole("region", { name: "校验明细" });
    await expect(region).toBeHidden();
    const toggle = page.locator("summary").filter({ hasText: "查看校验明细" });
    await toggle.focus();
    await page.keyboard.press("Enter");
    await expect(region).toBeVisible();
    await expect(region.locator("strong")).toHaveCount(1);
    await expect(region.locator("li")).toHaveCount(75);
    const size = await region.evaluate(el => ({ height: el.clientHeight, content: el.scrollHeight }));
    expect(size.height).toBeLessThanOrEqual(240);
    expect(size.content).toBeGreaterThan(size.height);
    const bounds = await region.boundingBox();
    expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(width);
    expect(await region.evaluate(el => el.scrollWidth - el.clientWidth)).toBeLessThanOrEqual(1);
    await region.focus();
    await page.keyboard.press("End");
    await expect.poll(() => region.evaluate(el => el.scrollTop)).toBeGreaterThan(0);
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
    if (width === 1440 || width === 390) await page.screenshot({ path: testInfo.outputPath("diagnostics.png"), fullPage: true });
    await toggle.click();
    await expect(region).toBeHidden();
    await expect(page.getByRole("button", { name: "生成 Word 汇报" })).toBeDisabled();
    await page.getByLabel("确认校验警告").check();
    await expect(page.getByRole("button", { name: "生成 Word 汇报" })).toBeEnabled();
  });
}

test("阻断错误默认展开且聚合计数保留严重级别", async ({ page }) => {
  await page.route("**/api/v1/monthly-reports/report-1", (route) => route.fulfill({ json: {
    ...summary, status: "INVALID", files: [], snapshot: null,
    diagnostics: [
      { severity: "ERROR", code: "INVALID_DATE", message: "日期需要核对", row: 3, count: 2 },
      { severity: "WARNING", code: "INVALID_DATE", message: "日期需要核对", row: 5, count: 1 },
    ],
  } }));
  await page.goto("/monthly-reports");
  await expect(page.getByText("校验未通过：3 项", { exact: true })).toBeVisible();
  const region = page.getByRole("region", { name: "校验明细" });
  await expect(region).toBeVisible();
  await expect(region.locator("strong")).toHaveCount(2);
  await expect(page.getByRole("button", { name: "生成 Word 汇报" })).toHaveCount(0);
});

test("取消删除不发送请求并返回操作按钮焦点", async ({ page }) => {
  let deletes = 0;
  page.on("request", (request) => { if (request.method() === "DELETE") deletes++; });
  await page.goto("/monthly-reports");
  const remove = page.getByRole("button", { name: "删除", exact: true });
  await remove.click();
  const dialog = page.getByRole("dialog", { name: "删除月度汇报" });
  await expect(dialog.getByRole("button", { name: "确认删除" })).toBeDisabled();
  await dialog.getByRole("button", { name: /^取\s*消$/ }).click();
  await expect(dialog).toBeHidden();
  await expect(remove).toBeFocused();
  expect(deletes).toBe(0);
});

for (const width of [390, 1440]) {
  test(`删除最后一份汇报清空预览且不再读取已删记录 ${width}`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    let deleted = false;
    let deleteBody: unknown;
    let staleReads = 0;
    await page.route("**/api/v1/monthly-reports?*", (route) =>
      route.fulfill({ json: { items: deleted ? [] : [summary], nextCursor: null } }));
    await page.route("**/api/v1/monthly-reports/report-1", async (route) => {
      if (route.request().method() === "DELETE") {
        deleteBody = route.request().postDataJSON();
        deleted = true;
        await route.fulfill({ status: 204 });
      } else {
        if (deleted) staleReads++;
        await route.fulfill({ json: { ...summary, files: [], diagnostics: [], snapshot } });
      }
    });
    await page.goto("/monthly-reports");
    await expect(page.getByRole("button", { name: "生成 Word 汇报" })).toBeVisible();
    await page.getByRole("button", { name: "删除", exact: true }).click();
    const dialog = page.getByRole("dialog", { name: "删除月度汇报" });
    await expect(dialog.getByText(/实际文件稍后由系统回收/)).toBeVisible();
    await dialog.getByLabel("删除原因").fill("  误上传，重新生成  ");
    await dialog.getByRole("button", { name: "确认删除" }).click();
    await expect(dialog).toBeHidden();
    await expect(page.getByText("还没有月度汇报")).toBeVisible();
    await expect(page.getByText("上传新文件或从历史记录选择一份汇报。")).toBeVisible();
    await page.getByRole("button", { name: "刷新历史" }).click();
    await expect(page.getByText("还没有月度汇报")).toBeVisible();
    expect(deleteBody).toEqual({ version: "1", reason: "误上传，重新生成" });
    expect(staleReads).toBe(0);
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
  });
}

test("删除失败保留记录和原因并可重试", async ({ page }) => {
  let attempts = 0;
  let deleted = false;
  await page.route("**/api/v1/monthly-reports?*", (route) =>
    route.fulfill({ json: { items: deleted ? [] : [summary], nextCursor: null } }));
  await page.route("**/api/v1/monthly-reports/report-1", async (route) => {
    if (route.request().method() !== "DELETE") return route.fallback();
    attempts++;
    if (attempts === 1) {
      await route.fulfill({ status: 503, json: {
        title: "服务暂不可用", status: 503, detail: "请稍后重试", code: "SERVICE_UNAVAILABLE", recoverable: true,
      } });
    } else {
      deleted = true;
      await route.fulfill({ status: 204 });
    }
  });
  await page.goto("/monthly-reports");
  await page.getByRole("button", { name: "删除", exact: true }).click();
  const dialog = page.getByRole("dialog", { name: "删除月度汇报" });
  await dialog.getByLabel("删除原因").fill("重复汇报");
  await dialog.getByRole("button", { name: "确认删除" }).click();
  await expect(dialog.getByText("删除失败，汇报仍保留")).toBeVisible();
  await expect(dialog.getByLabel("删除原因")).toHaveValue("重复汇报");
  await expect(page.getByRole("button", { name: "查看", exact: true })).toBeVisible();
  await dialog.getByRole("button", { name: "确认删除" }).click();
  await expect(page.getByText("还没有月度汇报")).toBeVisible();
  expect(attempts).toBe(2);
});

for (const status of ["UPLOADING", "VALIDATING", "QUEUED", "RUNNING", "CANCELLING"]) {
  test(`处理中禁止删除 ${status}`, async ({ page }) => {
    await page.route("**/api/v1/monthly-reports?*", (route) =>
      route.fulfill({ json: { items: [{ ...summary, status }], nextCursor: null } }));
    await page.goto("/monthly-reports");
    await expect(page.getByRole("button", { name: "删除", exact: true })).toBeDisabled();
  });
}

test("删除末页最后一条返回首页并保留其他汇报", async ({ page }) => {
  const second = { ...summary, id: "report-2", month: "2026-07" };
  let deleted = false;
  await page.route("**/api/v1/monthly-reports?*", (route) => {
    const lastPage = new URL(route.request().url()).searchParams.has("cursor");
    return route.fulfill({ json: {
      items: lastPage ? deleted ? [] : [second] : [summary],
      nextCursor: lastPage || deleted ? null : "page-2",
    } });
  });
  await page.route("**/api/v1/monthly-reports/report-2", (route) => {
    if (route.request().method() === "DELETE") {
      deleted = true;
      return route.fulfill({ status: 204 });
    }
    return route.fulfill({ json: { ...second, files: [], diagnostics: [], snapshot } });
  });
  await page.goto("/monthly-reports");
  await page.getByRole("button", { name: "下一页" }).click();
  await expect(page.getByRole("cell", { name: "2026-07", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "删除", exact: true }).click();
  const dialog = page.getByRole("dialog", { name: "删除月度汇报" });
  await dialog.getByLabel("删除原因").fill("重复记录");
  await dialog.getByRole("button", { name: "确认删除" }).click();
  await expect(page.getByRole("cell", { name: "2026-08", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "上一页" })).toBeDisabled();
  await expect(page.getByRole("button", { name: "下一页" })).toBeDisabled();
});

for (const width of [390, 1440, 1920]) test(`空月报布局紧凑且历史入口可达 ${width}`, async ({ page }, info) => {
  await page.setViewportSize({ width, height: 1000 });
  await page.route('**/api/v1/monthly-reports?**', route => route.fulfill({ json: { items: [], nextCursor: null, hasMore: false } }));
  await page.goto('/monthly-reports');
  const create = page.locator('.monthly-report-panel').filter({ has: page.getByRole('heading', { name: '新建汇报', exact: true }) });
  const preview = page.locator('.monthly-report-panel').filter({ has: page.getByRole('heading', { name: '内容预览', exact: true }) });
  await expect(page.getByText('上传新文件或从历史记录选择一份汇报。')).toBeVisible();
  const createBox = await create.boundingBox();
  const previewBox = await preview.boundingBox();
  expect(previewBox!.x).toBeCloseTo(createBox!.x, 0);
  expect(previewBox!.width).toBeCloseTo(createBox!.width, 0);
  expect(previewBox!.y).toBeGreaterThanOrEqual(createBox!.y + createBox!.height);
  expect(previewBox!.height).toBeLessThan(160);
  if (width >= 1440) {
    expect(createBox!.height).toBeLessThan(420);
    await expect(page.getByRole('heading', { name: '历史汇报', exact: true })).toBeInViewport();
  }
  expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  await page.screenshot({ path: info.outputPath(`monthly-layout-${width}.png`), animations: 'disabled' });
});

test("空月报历史提示文字保持清晰可读", async ({ page }) => {
  await page.route("**/api/v1/monthly-reports?**", (route) =>
    route.fulfill({ json: { items: [], nextCursor: null, hasMore: false } }),
  );
  await page.goto("/monthly-reports");

  const emptyDescription = page.locator(".monthly-report-history .ant-empty-description");
  await expect(emptyDescription).toHaveText("还没有月度汇报");
  await expect(emptyDescription).toHaveCSS("color", "rgb(89, 106, 131)");

  const result = await new AxeBuilder({ page })
    .include(".monthly-report-history")
    .withRules(["color-contrast"])
    .analyze();
  expect(result.violations).toEqual([]);
});
