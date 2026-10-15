import { Spin } from 'antd';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import { AppShell } from '../components/AppShell';
import { NotFoundPage, RouteErrorPage } from '../components/RouteErrorPage';
import { LoginPage } from '../pages/LoginPage';
import { HomePage } from '../pages/HomePage';
import { SettingsLayout } from '../components/SettingsLayout';
import { RequireSession, RequireSuperAdmin, RequireWorkspaceAccess } from '../auth/guards';

export const router = createBrowserRouter([
  { path: '/', element: <HomePage />, errorElement: <RouteErrorPage /> },
  { path: '/login', element: <LoginPage />, errorElement: <RouteErrorPage /> },
  {
    path: '/change-password',
    lazy: async () => {
      const { ChangePasswordPage } = await import('../pages/ChangePasswordPage');
      return { Component: () => <RequireSession><ChangePasswordPage /></RequireSession> };
    },
    hydrateFallbackElement: <Spin fullscreen description="正在加载工作区" />,
    errorElement: <RouteErrorPage />,
  },
  {
    element: <RequireSession><AppShell /></RequireSession>,
    hydrateFallbackElement: <Spin fullscreen description="正在加载工作区" />,
    errorElement: <RouteErrorPage />,
    children: [
      { path: 'recruitment-analysis', lazy: async () => ({ Component: (await import('../pages/RecruitmentAnalyticsPage')).RecruitmentAnalyticsPage }) },
      { path: 'matching', lazy: async () => ({ Component: (await import('../pages/MatchingWorkspacePage')).MatchingWorkspacePage }) },
      { path: 'overview', element: <Navigate to="/recruitment-analysis" replace /> },
      { path: 'recruitment', lazy: async () => ({ Component: (await import('../pages/RecruitmentWorkspacePage')).RecruitmentWorkspacePage }) },
      { path: 'schedule', lazy: async () => ({ Component: (await import('../pages/InterviewWorkspacePage')).InterviewWorkspacePage }) },
      { path: 'recruitment/setup', lazy: async () => {
        const { JdLibraryPage } = await import('../pages/JdLibraryPage');
        return { Component: () => <SettingsLayout><JdLibraryPage /></SettingsLayout> };
      } },
      { path: 'talents', lazy: async () => {
        const { TalentRoutePage } = await import('../pages/TalentRoutePage');
        return { Component: TalentRoutePage };
      } },
      { path: 'shared-library', lazy: async () => ({ Component: (await import('../pages/SharedLibraryPage')).SharedLibraryPage }) },
      { path: 'onboarded', lazy: async () => ({ Component: (await import('../pages/OnboardedPage')).OnboardedPage }) },
      { path: 'monthly-reports', lazy: async () => {
        const { MonthlyReportsPage } = await import('../monthly-reports/MonthlyReportsPage');
        return { Component: () => <SettingsLayout><MonthlyReportsPage /></SettingsLayout> };
      } },
      { path: 'settings/intakes', lazy: async () => {
        return { Component: (await import('../pages/TalentRoutePage')).LegacyIntakeRoute };
      } },
      { path: 'admin/clients', lazy: async () => {
        const { CustomerManagementPage } = await import('../pages/CustomerManagementPage');
        return { Component: () => <RequireSuperAdmin><SettingsLayout><CustomerManagementPage /></SettingsLayout></RequireSuperAdmin> };
      } },
      { path: 'admin/mail-resume-reviews', lazy: async () => {
        const { MailResumeValidationPage } = await import('../mail-resume/MailResumeValidationPage');
        return { Component: () => <RequireWorkspaceAccess><SettingsLayout><MailResumeValidationPage /></SettingsLayout></RequireWorkspaceAccess> };
      } },
      { path: 'admin/source-channels', lazy: async () => {
        const { SourceChannelsPage } = await import('../pages/SourceChannelsPage');
        return { Component: () => <RequireSuperAdmin><SettingsLayout><SourceChannelsPage /></SettingsLayout></RequireSuperAdmin> };
      } },
      { path: 'settings/preferences', lazy: async () => {
        const { PersonalPreferencesPage } = await import('../pages/PersonalPreferencesPage');
        return { Component: () => <SettingsLayout><PersonalPreferencesPage /></SettingsLayout> };
      } },
      { path: 'admin/work-calendars', lazy: async () => {
        const { WorkCalendarPage } = await import('../work-calendar/WorkCalendarPage');
        return { Component: () => <RequireSuperAdmin><SettingsLayout><WorkCalendarPage /></SettingsLayout></RequireSuperAdmin> };
      } },
      { path: 'admin/task-center', lazy: async () => {
        const { TaskCenterPage } = await import('../pages/TaskCenterPage');
        return { Component: () => <RequireSuperAdmin><SettingsLayout><TaskCenterPage /></SettingsLayout></RequireSuperAdmin> };
      } },
      { path: 'admin/reports', lazy: async () => {
        const { ReportingPage } = await import('../pages/ReportingPage');
        return { Component: () => <SettingsLayout><ReportingPage /></SettingsLayout> };
      } },
      { path: 'admin/access', element: <Navigate to="/admin/identity" replace /> },
      {
        path: 'admin/identity',
        lazy: async () => {
          const { IdentityAdminPage } = await import('../pages/IdentityAdminPage');
          return { Component: () => <RequireSuperAdmin><SettingsLayout><IdentityAdminPage /></SettingsLayout></RequireSuperAdmin> };
        },
      },
      {
        path: 'admin/scoring',
        lazy: async () => {
          const { ScoringGovernancePage } = await import('../pages/ScoringGovernancePage');
          return { Component: () => <RequireSuperAdmin><SettingsLayout><ScoringGovernancePage /></SettingsLayout></RequireSuperAdmin> };
        },
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
