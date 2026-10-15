import { request } from '../api/http';
import { collectCount, type CountPage } from '../api/count';
import type { CursorPage } from './api';

export interface JdResumeSource {
  id: string; mailSourceId: string; candidateId: string; candidateName: string;
  resumeVersionId: string; originalFileName: string; contentSha256: string; createTime: string;
}
export interface MailSourceMessage {
  id: string; jdVersionId: string; sourceMessageId: string; senderEmail: string; recipientEmail: string;
  subject: string; plainBody: string; deliveryType: 'MAIL_SIMULATION'; createId: string; createTime: string;
}
export const mailSourceApi = {
  sources: (jdVersionId: string, cursor?: string, limit = 20) => {
    const params = new URLSearchParams({ limit: String(limit) });
    if (cursor) params.set('cursor', cursor);
    return request<CursorPage<JdResumeSource>>(`/jd-versions/${jdVersionId}/resume-sources?${params}`);
  },
  countSources: (jdVersionId: string, signal?: AbortSignal) => collectCount(
    () => request<CountPage>(`/jd-versions/${jdVersionId}/resume-sources/count`, { signal: signal ?? null }), signal),
  message: (id: string) => request<MailSourceMessage>(`/mail-sources/${id}`),
};
