import { scrollableTableHeader } from '../components/scrollableTableHeader';
import { CompactPagination } from '../components/CompactPagination';
import { countPaginationProps } from '../components/countPaginationProps';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, Descriptions, Input, Space, Table, Tag, Typography } from 'antd';
import { useState } from 'react';
import { ApiProblemError } from '../api/problem';
import { WorkspaceDialog } from '../components/WorkspaceDialog';
import { mailSourceApi, type JdResumeSource } from './mailSourceApi';

const errorDetail = (error: unknown) => error instanceof ApiProblemError ? error.problem.detail : '来源读取失败，请重试。';

export function JdResumeSources({ jdVersionId }: { jdVersionId: string }) {
  const [pageSize, setPageSize] = useState(20);
  const [cursor, setCursor] = useState<string>();
  const [history, setHistory] = useState<Array<string | undefined>>([]);
  const [mailId, setMailId] = useState<string>();
  const sources = useQuery({ queryKey: ['demand', 'resume-sources', jdVersionId, cursor, pageSize], queryFn: () => mailSourceApi.sources(jdVersionId, cursor, pageSize) });
  const total = useQuery({ queryKey: ['demand', 'resume-sources', jdVersionId, 'count'], queryFn: ({ signal }) => mailSourceApi.countSources(jdVersionId, signal), retry: false });
  const mail = useQuery({ queryKey: ['demand', 'mail-source', mailId], queryFn: () => mailSourceApi.message(mailId!), enabled: Boolean(mailId), retry: false });
  return <Card title="此 JD 版本下的来件简历" extra={<Button loading={sources.isFetching} onClick={() => void sources.refetch()}>刷新来件</Button>}>
    <Typography.Paragraph type="secondary">这里记录附件与原文版本的关联。模拟邮件尚未经过企业邮箱收发，候选人的招聘进展需由 HR 另行确认。</Typography.Paragraph>
    {sources.error ? <Alert occurrence={sources.error} type="error" showIcon title={errorDetail(sources.error)} action={<Button onClick={() => void sources.refetch()}>重试来件</Button>} /> : null}
    <Table<JdResumeSource> rowKey="id" dataSource={sources.data?.items ?? []} loading={sources.isPending} pagination={false} scroll={{ x: 620 }}
      locale={{ emptyText: '此版本尚无已登记的来件简历' }} onHeaderRow={() => scrollableTableHeader('来件简历表头，可横向滚动')}
      columns={[
        { title: '候选人', render: (_, item) => <Typography.Link href={`/talents?candidate=${encodeURIComponent(item.candidateId)}`}>{item.candidateName}</Typography.Link> },
        { title: '原始附件', dataIndex: 'originalFileName', ellipsis: true },
        { title: '来源', render: (_, item) => <Button type="link" onClick={() => setMailId(item.mailSourceId)}>查看模拟邮件</Button> },
        { title: '版本追溯', render: (_, item) => <Typography.Text copyable={{ text: item.resumeVersionId }}>简历版本 ID</Typography.Text> },
      ]} />
    <CompactPagination previousLabel="上一页来件" nextLabel="下一页来件" label="来件简历分页" page={history.length + 1} pageSize={pageSize} onPageSizeChange={size => { setPageSize(size); setCursor(undefined); setHistory([]); }} loading={sources.isFetching}
      hasPrevious={history.length > 0} hasNext={Boolean(sources.data?.hasMore)} maxPage={history.length + 1}
      onPrevious={() => { setCursor(history.at(-1)); setHistory(history.slice(0, -1)); }}
      onNext={() => { setHistory([...history, cursor]); setCursor(sources.data?.nextCursor ?? undefined); }}
      onPageChange={page => { if (page <= history.length) { setCursor(history[page - 1]); setHistory(history.slice(0, page - 1)); } }} {...countPaginationProps(total)} />
    <WorkspaceDialog title="邮件来源" open={Boolean(mailId)} onCancel={() => setMailId(undefined)} footer={null} size="wide">
      {mail.error ? <Alert occurrence={mail.error} type="error" showIcon title={errorDetail(mail.error)} action={<Button onClick={() => void mail.refetch()}>重试邮件</Button>} /> : null}
      {mail.isFetching && !mail.data ? <Typography.Paragraph role="status">正在读取邮件来源…</Typography.Paragraph> : null}
      {mail.data ? <Space orientation="vertical" style={{ width: '100%' }}>
        <Tag>模拟邮件</Tag>
        <Descriptions column={1} items={[
          { key: 'subject', label: '主题', children: mail.data.subject },
          { key: 'from', label: '发件人', children: mail.data.senderEmail },
          { key: 'to', label: '收件人', children: mail.data.recipientEmail },
          { key: 'id', label: 'Message-ID', children: <Typography.Text copyable>{mail.data.sourceMessageId}</Typography.Text> },
        ]} />
        <Input.TextArea aria-label="原始邮件正文" value={mail.data.plainBody} rows={14} readOnly />
      </Space> : null}
    </WorkspaceDialog>
  </Card>;
}
