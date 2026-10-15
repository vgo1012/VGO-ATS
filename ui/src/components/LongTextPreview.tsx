import { useState } from 'react';
import { Button } from 'antd';
import { WorkspaceDialog } from './WorkspaceDialog';
import './LongTextPreview.css';

/** Keep free-form notes compact in dense views while preserving the original text. */
export function LongTextPreview({ text, label, limit = 24 }: { text: string; label: string; limit?: number }) {
  const [open, setOpen] = useState(false);
  const full = text;
  const singleLine = full.trim().replace(/\s+/g, ' ');
  const characters = Array.from(singleLine);
  const shortened = full !== singleLine || characters.length > limit;
  const preview = characters.slice(0, limit).join('') + (characters.length > limit ? '…' : '');

  return <span className="long-text-preview">
    <span>{shortened ? preview : full}</span>
    {shortened ? <>
      <Button type="link" size="small" aria-label={`查看更多${label}`} onClick={() => setOpen(true)}>查看更多</Button>
      {open ? <WorkspaceDialog open size="form" resizeSurface title={`完整${label}`} footer={null} onCancel={() => setOpen(false)}>
        <div className="long-text-full">{full}</div>
      </WorkspaceDialog> : null}
    </> : null}
  </span>;
}
