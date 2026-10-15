import { App } from 'antd';
import { useMemo } from 'react';
import type { ReactNode } from 'react';
import { notifyError } from './errorNotification';

export function useAppFeedback() {
  const app = App.useApp();
  return useMemo(() => ({
    ...app,
    message: {
      ...app.message,
      error: (content: ReactNode) => notifyError(app.notification, content),
    },
  }), [app]);
}
