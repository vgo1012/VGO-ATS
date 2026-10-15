import { useAppFeedback } from './useAppFeedback';
import { Button, type ButtonProps } from 'antd';
import { useRef, useState } from 'react';
import { ApiProblemError } from '../api/problem';

type Props = Omit<ButtonProps, 'href' | 'target' | 'onClick' | 'loading' | 'download'> & {
  download: () => Promise<void>;
};

export function FileDownloadButton({ download, disabled, ...props }: Props) {
  const { message } = useAppFeedback();
  const inFlight = useRef(false);
  const [busy, setBusy] = useState(false);
  return <Button {...props} disabled={disabled || busy} loading={busy} aria-busy={busy} onClick={async event => {
    event.stopPropagation();
    if (inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    try {
      await download();
      message.success('文件已交给浏览器下载，请在下载列表中查看。');
    } catch (error) {
      message.error(error instanceof ApiProblemError ? error.problem.detail : '下载失败，请检查网络后重试。');
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  }} />;
}
