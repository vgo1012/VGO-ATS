import { Spin } from 'antd';

export function LoadingStatus({ label, size }: { label: string; size?: 'small' }) {
  return <div role="status"><Spin size={size} aria-hidden="true" /><span className="sr-only">{label}</span></div>;
}
