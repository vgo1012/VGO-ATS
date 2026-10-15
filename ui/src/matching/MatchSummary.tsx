import { Button, Popover } from 'antd';
import { useRef, useState } from 'react';
import './match-summary.css';

export function MatchSummary({ text }: { text: string }) {
  const [open, setOpen] = useState(false);
  const [reading, setReading] = useState(false);
  const trigger = useRef<HTMLButtonElement>(null);
  const panel = useRef<HTMLElement>(null);
  const close = () => { setReading(false); setOpen(false); trigger.current?.focus(); };
  return <Popover open={open} onOpenChange={value => {
    // 鼠标离开产生的延迟关闭不能中断已经获得焦点的正文。
    if (!value && panel.current?.contains(document.activeElement)) return;
    setOpen(value); if (!value) setReading(false);
  }} trigger={reading ? 'click' : ['hover', 'click']} placement="bottom"
    content={<section ref={panel} className="match-summary-panel" aria-label="完整匹配说明" onFocusCapture={() => setReading(true)} onBlurCapture={event => {
      if (!event.currentTarget.contains(event.relatedTarget) && event.relatedTarget !== trigger.current) { setReading(false); setOpen(false); }
    }} onKeyDown={event => {
      if (event.key === 'Escape') { event.stopPropagation(); close(); }
    }}>
      <div className="match-summary-heading"><strong>匹配说明</strong><Button size="small" type="text" onClick={close}>收起</Button></div>
      <p tabIndex={0}>{text}</p>
    </section>}>
    <Button ref={trigger} type="text" className="matching-summary match-summary-trigger" aria-label="查看完整匹配说明" aria-expanded={open}
      onClick={() => { setReading(!reading); setOpen(!reading); }}
      onKeyDown={event => { if (event.key === 'Escape') close(); }}>
      <span className="match-summary-preview">{text}</span>
    </Button>
  </Popover>;
}
