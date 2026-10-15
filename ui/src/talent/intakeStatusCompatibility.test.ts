import { afterEach, expect, it, vi } from 'vitest';
import { request } from '../api/http';
import { talentApi } from './api';
import { shouldPollIntakes } from './intakePolling';

vi.mock('../api/http',()=>({request:vi.fn()}));
afterEach(()=>vi.resetAllMocks());

it.each([
  ['PENDING_IMPORT',null,'PENDING'],
  ['READY_FOR_CONFIRMATION',null,'PENDING'],
  ['IDENTITY_REVIEW_REQUIRED','processing','PENDING'],
  ['FAILED_RETRYABLE','processing','PENDING'],
  ['PARSING','processing','PROCESSING'],
  ['CONFIRMED','processing','PROCESSING'],
  ['CONFIRMED',null,'IMPORTED'],
  ['COMPLETED','processing','IMPORTED'],
] as const)('restores missing business state for %s / %s',async(status,processingId,expected)=>{
  vi.mocked(request).mockResolvedValue({items:[{id:'intake',status,processingId,createTime:'2026-09-09T01:00:00Z'}],hasMore:false,nextCursor:null});
  const page=await talentApi.intakes();
  expect(page.items[0]?.importStatus).toBe(expected);
  expect(page.items[0]?.createdAt).toBe('2026-09-09T01:00:00Z');
  expect(shouldPollIntakes(page.items,'visible')).toBe(status==='PARSING'||status==='CONFIRMED'?3000:false);
});

it('keeps an explicit server business state authoritative',async()=>{
  vi.mocked(request).mockResolvedValue({id:'intake',status:'CONFIRMED',importStatus:'PROCESSING',createTime:'2026-09-09T01:00:00Z'});
  expect((await talentApi.intake('intake')).importStatus).toBe('PROCESSING');
});

it('normalizes a null state in a detail response',async()=>{
  vi.mocked(request).mockResolvedValue({id:'intake',status:'COMPLETED',importStatus:null});
  expect((await talentApi.intake('intake')).importStatus).toBe('IMPORTED');
});

it('normalizes import action responses so polling can continue',async()=>{
  vi.mocked(request).mockResolvedValueOnce({id:'intake',status:'PENDING_IMPORT',version:1});
  const pending=await talentApi.intake('intake');
  vi.mocked(request).mockResolvedValueOnce({id:'intake',status:'PARSING',version:2});
  expect((await talentApi.importIntake(pending)).importStatus).toBe('PROCESSING');
});
