import type { Page, Route } from '@playwright/test';
import type { RecruitmentAnalytics } from '../../src/reporting/analyticsApi';

type AnalyticsResponse = RecruitmentAnalytics;

export const analyticsActor = {
  userId: 'analytics-reader',
  displayName: '招聘分析负责人',
  workEmail: 'analytics@example.com',
  homeDepartmentId: 'dept-product',
  roles: ['RECRUITMENT_MANAGER'],
  permissions: [
    'reporting.read-own',
    'demand.read',
    'recruitment.manage',
    'talent.read',
    'matching.run',
    'interview.feedback.read-authorized',
  ],
  superAdmin: false,
  mustChangePassword: false,
  version: '1',
};

export const analyticsJds = [
  {
    id: 'jd-platform',
    code: 'JD-PLATFORM',
    title: '平台工程师',
    distinction: '上海 · 基础架构',
    status: 'ACTIVE',
    currentVersionNo: 3,
    clientId: null,
    clientName: null,
    ownerUserId: 'analytics-reader',
    ownerDepartmentId: 'dept-product',
    recruitmentType: 'INTERNAL',
    headcount: 4,
    occupiedHeadcount: 1,
    remainingHeadcount: 3,
    recruitmentCycleDays: 45,
    version: '3',
    currentParseStatus: 'SUCCEEDED',
    createTime: '2026-08-01T02:00:00Z',
  },
  {
    id: 'jd-delivery',
    code: 'JD-DELIVERY',
    title: '客户交付经理',
    distinction: '北京 · 金融客户',
    status: 'ACTIVE',
    currentVersionNo: 2,
    clientId: 'client-finance',
    clientName: '远山金融',
    ownerUserId: 'analytics-reader',
    ownerDepartmentId: 'dept-delivery',
    recruitmentType: 'EXTERNAL',
    headcount: 2,
    occupiedHeadcount: 1,
    remainingHeadcount: 1,
    recruitmentCycleDays: 60,
    version: '2',
    currentParseStatus: 'SUCCEEDED',
    createTime: '2026-08-10T02:00:00Z',
  },
] as const;

export const recruitmentAnalysis: AnalyticsResponse = {
  from: '2026-08-31T16:00:00.000Z',
  to: '2026-09-30T16:00:00.000Z',
  asOf: '2026-09-15T02:30:00Z',
  risk: { activeBlacklists: 3 },
  demand: {
    activeJobs: 6,
    headcount: 18,
    unknownHeadcountJobs: 1,
    occupied: 7,
    openHeadcount: 11,
  },
  funnel: [
    { key: 'RESUME', label: '全部简历', count: 120 },
    { key: 'APPLICATION', label: '进入流程', count: 84 },
    { key: 'INTERVIEW_1', label: '进入一面', count: 56 },
    { key: 'INTERVIEW_2', label: '进入二面', count: 31 },
    { key: 'INTERVIEW_3_PLUS', label: '进入三面及以上', count: 18 },
    { key: 'OFFER', label: '已发 Offer', count: 12 },
    { key: 'ONBOARDED', label: '已入职', count: 7 },
  ],
  trend: [
    { date: '2026-09-01', applications: 20, offers: 3, onboarded: 1 },
    { date: '2026-09-08', applications: 34, offers: 5, onboarded: 3 },
    { date: '2026-09-15', applications: 30, offers: 4, onboarded: 3 },
  ],
  channels: [
    { id: 'channel-boss', name: 'BOSS直聘', resumes: 80, applications: 56, offers: 5, onboarded: 3 },
    { id: 'channel-referral', name: '内推', resumes: 40, applications: 28, offers: 7, onboarded: 4 },
  ],
  portraits: [
    { dimension: 'AGE', label: '25–29 岁', resumes: 50, passed: 24, rejected: 12, pending: 8, withdrawn: 6 },
    { dimension: 'AGE', label: '30–34 岁', resumes: 34, passed: 17, rejected: 8, pending: 6, withdrawn: 3 },
    { dimension: 'EDUCATION', label: '本科', resumes: 60, passed: 30, rejected: 14, pending: 10, withdrawn: 6 },
    { dimension: 'EDUCATION', label: '硕士', resumes: 24, passed: 11, rejected: 6, pending: 4, withdrawn: 3 },
    { dimension: 'INTENDED_LOCATION', label: '上海', resumes: 46, passed: 23, rejected: 11, pending: 8, withdrawn: 4 },
    { dimension: 'INTENDED_LOCATION', label: '北京', resumes: 38, passed: 18, rejected: 9, pending: 6, withdrawn: 5 },
  ],
  efficiency: {
    completedCount: 24,
    averageTotalHours: 196.5,
    activeCount: 60,
    averageActiveHours: 82.25,
  },
  departments: [
    { id: 'dept-product', name: '产品研发部' },
    { id: 'dept-delivery', name: '客户交付部' },
  ],
  notes: ['漏斗按招聘流程创建时间归组，后续结果统计截至当前。'],
};

export const emptyRecruitmentAnalysis: AnalyticsResponse = {
  ...recruitmentAnalysis,
  risk: { activeBlacklists: 0 },
  demand: { activeJobs: 0, headcount: 0, unknownHeadcountJobs: 0, occupied: 0, openHeadcount: 0 },
  funnel: [],
  trend: [],
  channels: [],
  portraits: [],
  efficiency: { completedCount: 0, averageTotalHours: null, activeCount: 0, averageActiveHours: null },
  notes: [],
};

export interface RecruitmentAnalysisFixtureState {
  actorStatus: 200 | 401;
  actor: typeof analyticsActor;
  reportStatus: 200 | 401 | 403 | 503;
  report: AnalyticsResponse;
  reportRequests: URL[];
}

function problem(status: 401 | 403 | 503) {
  if (status === 401) return { status, title: '会话失效', detail: '请重新登录', code: 'SESSION_REQUIRED', recoverable: false };
  if (status === 403) return { status, title: '无权访问招聘分析', detail: '当前数据范围已被撤销', code: 'FORBIDDEN', recoverable: false };
  return { status, title: '招聘分析暂不可用', detail: '聚合服务暂时无法读取，请重试', code: 'SERVICE_UNAVAILABLE', recoverable: true };
}

async function fulfillApi(route: Route, state: RecruitmentAnalysisFixtureState) {
  const request = route.request();
  const url = new URL(request.url());
  const path = url.pathname.replace('/api/v1', '');

  if (request.method() !== 'GET') {
    return route.fulfill({ status: 405, json: { title: '测试夹具拒绝写入', status: 405, code: 'READ_ONLY_FIXTURE' } });
  }
  if (path === '/auth/csrf') {
    return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'analytics-fixture' } });
  }
  if (path === '/me') {
    if (state.actorStatus === 401) return route.fulfill({ status: 401, contentType: 'application/problem+json', json: problem(401) });
    return route.fulfill({ json: state.actor });
  }
  if (path === '/reports/recruitment-analysis') {
    state.reportRequests.push(url);
    if (state.reportStatus !== 200) {
      return route.fulfill({ status: state.reportStatus, contentType: 'application/problem+json', json: problem(state.reportStatus) });
    }
    return route.fulfill({ json: {
      ...state.report,
      from: url.searchParams.get('from') ?? state.report.from,
      to: url.searchParams.get('to') ?? state.report.to,
    } });
  }
  if (path === '/jds') {
    return route.fulfill({ json: { items: analyticsJds, nextCursor: null, hasMore: false } });
  }
  if (path.startsWith('/jds/')) {
    const jd = analyticsJds.find(item => `/jds/${item.id}` === path);
    return jd ? route.fulfill({ json: jd }) : route.fulfill({ status: 404, json: { status: 404, code: 'JD_NOT_FOUND' } });
  }
  if (path === '/directory/departments') {
    return route.fulfill({ json: recruitmentAnalysis.departments.map((item, index) => ({
      ...item,
      code: index === 0 ? 'PRODUCT' : 'DELIVERY',
      parentDepartmentId: null,
      status: 'ACTIVE',
    })) });
  }
  if (path === '/directory/users/resolve') {
    return route.fulfill({ json: [{ id: analyticsActor.userId, displayName: analyticsActor.displayName, status: 'ACTIVE' }] });
  }
  if (path === '/directory/users') {
    return route.fulfill({ json: { items: [{ id: analyticsActor.userId, displayName: analyticsActor.displayName, status: 'ACTIVE' }], nextCursor: null, hasMore: false } });
  }
  if (['/workbench', '/management/workbench', '/my/interview-tasks', '/tasks', '/admin/super-admins'].includes(path)) {
    return route.fulfill({ json: [] });
  }
  return route.fulfill({ json: { items: [], nextCursor: null, hasMore: false } });
}

export async function mockRecruitmentAnalysis(page: Page, overrides: Partial<Omit<RecruitmentAnalysisFixtureState, 'reportRequests'>> = {}) {
  const state: RecruitmentAnalysisFixtureState = {
    actorStatus: 200,
    actor: { ...analyticsActor },
    reportStatus: 200,
    report: recruitmentAnalysis,
    reportRequests: [],
    ...overrides,
  };
  await page.clock.setFixedTime(new Date('2026-09-15T10:30:00+08:00'));
  await page.route('**/api/v1/**', route => fulfillApi(route, state));
  return state;
}
