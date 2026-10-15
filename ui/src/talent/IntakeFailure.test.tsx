import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import type { ResumeIntake } from './api';
import { IntakeFailure } from './IntakeFailure';

afterEach(cleanup);

const failedIntake=(failureCode:string):ResumeIntake=>({
  id:'intake',batchId:'batch',sourceChannelId:'source',sourceRecruiterUserId:'hr',defaultOwnerUserId:'hr',
  ownerUserId:'hr',ownerDepartmentId:'department',handledByUserId:null,originalFileName:'synthetic.pdf',
  mediaType:'application/pdf',sizeBytes:1,contentSha256:'hash',scanStatus:'CLEAN',status:'FAILED_RETRYABLE',
  importStatus:'PENDING',processingStatus:'FAILED_FINAL',failureCode,candidateId:null,resumeVersionId:null,
  version:1,createdAt:'2026-10-09T00:00:00Z',
});

it.each([
  ['DOCUMENT_PAGE_RENDER_LIMIT','文档页面或图片尺寸、像素数量不符合当前处理要求'],
  ['DOCUMENT_CONTAINER_LIMIT','文档资源或处理量超过当前安全上限'],
  ['DOCUMENT_CONTAINER_INVALID','文档内部结构无效，无法安全识别'],
  ['DOCUMENT_PAGE_LIMIT','文档页数超出安全上限'],
  ['DOCUMENT_TEXT_LIMIT','文档文字内容超出安全上限'],
])('explains deterministic document failure %s and requires file or configuration correction before retry',(code,reason)=>{
  render(<IntakeFailure value={failedIntake(code)}/>);
  expect(screen.getByText(`失败原因：${reason}`)).toBeVisible();
  expect(screen.getByText('请压缩图片、精简内容或重新导出带文字层的 PDF 后上传，或联系管理员调整文档处理配置；文件或配置更新后再重试。')).toBeVisible();
  expect(screen.queryByText(/可尝试重试导入/)).not.toBeInTheDocument();
  expect(screen.getByText(code)).toBeInTheDocument();
});

it('preserves the scoring retry advice',()=>{
  render(<IntakeFailure value={{...failedIntake('DOCUMENT_CONTAINER_LIMIT'),scoreStatus:'FAILED_RETRYABLE'}}/>);
  expect(screen.getByText('可点击“重试评分”；若仍失败，请联系管理员排查。')).toBeVisible();
});

it('requires permission correction before retrying scoring',()=>{
  render(<IntakeFailure value={{...failedIntake('DATA_SCOPE_FORBIDDEN'),scoreStatus:'FAILED_RETRYABLE'}}/>);
  expect(screen.getByText('失败原因：当前处理账号无权访问相关业务数据')).toBeVisible();
  expect(screen.getByText('请管理员核对关联 JD、候选人归属及处理权限；权限调整后再重试。')).toBeVisible();
  expect(screen.queryByText(/可点击“重试评分”/)).not.toBeInTheDocument();
});

it('keeps recovery advice for transient intake completion failure',()=>{
  render(<IntakeFailure value={failedIntake('INTAKE_COMPLETION_FAILED')}/>);
  expect(screen.getByText('失败原因：简历解析完成后的入库处理异常')).toBeVisible();
  expect(screen.getByText(/可尝试重试导入/)).toBeVisible();
});
