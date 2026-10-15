import { useEffect, useRef } from 'react';
import { useAnalyticsMotion } from './analyticsMotion';

export function AnalyticsNumber({ value }: { value: number }) {
  const node = useRef<HTMLSpanElement>(null);
  const current = useRef(0);
  const motion = useAnalyticsMotion();
  useEffect(() => {
    const from = current.current;
    const start = performance.now();
    let frame = 0;
    const tick = () => {
      const progress = motion ? Math.max(0, Math.min(1, (performance.now() - start) / 850)) : 1;
      current.current = Math.round(from + (value - from) * (1 - (1 - progress) ** 3));
      if (node.current) node.current.textContent = String(current.current);
      if (progress < 1) frame = requestAnimationFrame(tick);
    };
    tick();
    return () => cancelAnimationFrame(frame);
  }, [value, motion]);
  // 辅助技术始终读取最终统计值，逐帧变化只用于视觉展示。
  return <span><span className="sr-only">{value}</span><span aria-hidden="true" ref={node}>{value}</span></span>;
}
