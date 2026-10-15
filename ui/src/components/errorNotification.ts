import type { ReactNode } from 'react';
import type { NotificationInstance, ArgsProps, NotificationConfig } from 'antd/es/notification/interface';

export const errorNotificationConfig: NotificationConfig = {
  placement: 'top', duration: 5, pauseOnHover: false, stack: false,
  classNames: { list: 'error-notification-list' },
};

/** 所有业务错误统一使用顶部居中、可关闭的五秒红色浮层。 */
export function notifyError(notification: NotificationInstance, title: ReactNode, options: ArgsProps = {}) {
  notification.error({
    ...options,
    title,
    duration: 5,
    pauseOnHover: false,
    placement: 'top',
    className: 'error-notification',
    role: 'alert',
    closable: { 'aria-label': '关闭错误提示' },
  });
}
