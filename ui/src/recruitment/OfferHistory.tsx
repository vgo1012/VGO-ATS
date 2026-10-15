import { Button, Card, Collapse, Dropdown, Space, Timeline } from 'antd';
import { UserName } from '../display/BusinessReferences';
import { displayEnum, formatDateTime } from '../display/labels';
import type { ApplicationDetails } from './api';

export function OfferHistory({ value, actionsAllowed = true, onOnboard, onCorrect, onAdvance }: { value: ApplicationDetails; actionsAllowed?: boolean; onOnboard: () => void; onCorrect: () => void; onAdvance: () => void }) {
  const active = actionsAllowed && value.application.currentStage === 'OFFER' && !value.application.terminalAt;
  const current = value.offerStatusRecords.at(-1);
  const canOnboard = current?.status === 'ISSUED' || current?.status === 'ACCEPTED';
  const history = value.offerStatusRecords.slice(0, -1);
  if (!current && !active) return null;
  const timeline = (records: ApplicationDetails['offerStatusRecords']) => <Timeline items={records.map(record => ({ key: record.id, color: record.status === 'TERMINATED' ? 'red' : record.status === 'ONBOARDED' ? 'green' : 'blue',
      content: <span><b>{displayEnum(record.status)}</b> · {displayEnum(record.recordType)}<br /><small>{record.reason} · {formatDateTime(record.occurredAt)} · <UserName id={record.operatedByUserId} /></small></span>,
    }))} />;
  return <Card title="录用进展">
    {active ? <Space wrap style={{ marginBottom: 16 }}>
      {canOnboard ? <Button type="primary" onClick={onOnboard}>确认入职</Button> : <Button type="primary" onClick={onAdvance}>记录已发出</Button>}
      <Dropdown trigger={['click']} menu={{ items: [
        ...(canOnboard ? [{ key: 'terminate', label: '终止录用', danger: true, onClick: onAdvance }] : []),
        ...(current && current.status !== 'ONBOARDED' ? [{ key: 'correct', label: '更正当前记录', onClick: onCorrect }] : []),
      ] }}><Button>更多</Button></Dropdown>
    </Space> : null}
    {current ? timeline([current]) : null}
    {history.length ? <Collapse ghost items={[{ key: 'history', label: `查看历史（${history.length}条）`, children: timeline(history) }]} /> : null}
  </Card>;
}
