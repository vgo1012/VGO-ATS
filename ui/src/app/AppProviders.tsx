import { errorNotificationConfig } from '../components/errorNotification';
import '../styles/error-notification.css';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { useState } from 'react';
import { RouterProvider } from 'react-router-dom';
import { router } from './router';
import { SessionProvider } from '../auth/session';

export function AppProviders() {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            retry: 1,
            refetchOnWindowFocus: false,
          },
          mutations: { retry: false },
        },
      }),
  );

  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#2563eb',
          colorPrimaryHover: '#1d4ed8',
          colorPrimaryActive: '#1e40af',
          colorPrimaryBg: '#eff6ff',
          colorInfo: '#168bd2',
          colorSuccess: '#16856b',
          colorWarning: '#b66a12',
          colorError: '#c33c54',
          colorText: '#17233f',
          colorTextSecondary: '#596a83',
          colorTextDescription: '#596a83',
          colorTextPlaceholder: '#596a83',
          colorLink: '#2563eb',
          colorLinkHover: '#1d4ed8',
          colorLinkActive: '#1e40af',
          colorBorder: '#dbe5f4',
          controlHeight: 40,
          controlHeightSM: 32,
          borderRadius: 8,
          fontFamily:
            '"Noto Sans SC", "PingFang SC", "Microsoft YaHei", system-ui, sans-serif',
        },
        components: {
          Button: { controlHeight: 40, controlHeightSM: 32 },
          Card: { headerHeight: 48 },
          Table: { cellPaddingBlock: 10, cellPaddingInline: 12 },
        },
      }}
    >
      <App message={{ maxCount: 3, duration: 3, top: 88 }} notification={errorNotificationConfig}>
        <QueryClientProvider client={queryClient}>
          <SessionProvider>
            <RouterProvider router={router} />
          </SessionProvider>
        </QueryClientProvider>
      </App>
    </ConfigProvider>
  );
}
