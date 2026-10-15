import { Alert, App, notification } from 'antd';
import type { AlertProps } from 'antd';
import { useEffect, useEffectEvent, useId } from 'react';
import { errorNotificationConfig, notifyError } from './errorNotification';

type FeedbackAlertProps = AlertProps & { occurrence?: unknown };

function ErrorNotice({ title, description, action, afterClose, occurrence }: FeedbackAlertProps) {
  const app = App.useApp();
  const [fallback, holder] = notification.useNotification(errorNotificationConfig);
  const hasProvider = typeof app.notification.error === 'function';
  const api = hasProvider ? app.notification : fallback;
  const key = useId();
  const textDescription = typeof description === 'string' ? description : undefined;
  const open = useEffectEvent(() => {
    notifyError(api, title, {
      key,
      description: textDescription,
      onClose: () => { afterClose?.(); },
    });
  });
  useEffect(() => {
    open();
    return () => api.destroy(key);
  }, [api, key, title, textDescription, occurrence]);

  // 恢复操作与长任务的结构化进度保留在原处，不随短暂提示消失。
  return <>{hasProvider ? null : holder}{description && !textDescription ? <div>{description}</div> : null}{action ? <div className="error-recovery-actions">{action}</div> : null}</>;
}

/** 错误改为浮层，其余信息、警告和成功状态沿用 Ant Design。 */
export function FeedbackAlert({ occurrence, ...props }: FeedbackAlertProps) {
  return props.type === 'error' ? <ErrorNotice {...props} occurrence={occurrence} /> : <Alert {...props} />;
}
