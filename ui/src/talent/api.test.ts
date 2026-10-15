import { afterEach, expect, it, vi } from 'vitest';
import { request } from '../api/http';
import { confirmCurrentIntake, importCurrentIntake, talentApi, type ResumeIntake } from './api';

vi.mock('../api/http', () => ({ request: vi.fn(), downloadFile: vi.fn() }));
afterEach(() => vi.resetAllMocks());

it('batches historical source IDs and skips empty requests',async()=>{
  const sources=[{resumeVersionId:'historical +/',source:null}];
  vi.mocked(request).mockResolvedValue(sources);
  expect(await talentApi.resumeVersionSources([])).toEqual([]);
  expect(request).not.toHaveBeenCalled();
  expect(await talentApi.resumeVersionSources(['historical +/','new'])).toEqual(sources);
  expect(request).toHaveBeenCalledExactlyOnceWith('/resume-version-sources?resumeVersionIds=historical+%2B%2F&resumeVersionIds=new');
});

it('reads exact resume version references with encoded repeated IDs and skips empty requests',async()=>{
  const references=[{id:'historical +/',versionNo:1,originalFileName:'历史简历.pdf'}];
  vi.mocked(request).mockResolvedValue(references);
  expect(await talentApi.resumeVersionReferences([])).toEqual([]);
  expect(request).not.toHaveBeenCalled();
  expect(await talentApi.resumeVersionReferences(['historical +/','new'])).toEqual(references);
  expect(request).toHaveBeenCalledExactlyOnceWith('/resume-version-references?resumeVersionIds=historical+%2B%2F&resumeVersionIds=new');
});

it('keeps the default resume page and encodes a requested history cursor',async()=>{
  vi.mocked(request).mockResolvedValue({items:[],hasMore:false,nextCursor:null});
  await talentApi.resumeVersions('candidate');
  expect(request).toHaveBeenLastCalledWith('/candidates/candidate/resume-versions?limit=20');
  await talentApi.resumeVersions('candidate','next+/=',20);
  expect(request).toHaveBeenLastCalledWith('/candidates/candidate/resume-versions?cursor=next%2B%2F%3D&limit=20');
});

it('reads exact historical resume versions through the isolated summary endpoint and skips empty requests',async()=>{
  vi.mocked(request).mockResolvedValue([{resumeVersionId:'old +/',facts:null}]);
  expect(await talentApi.resumeVersionFactSummaries([])).toEqual([]);
  expect(request).not.toHaveBeenCalled();
  expect(await talentApi.resumeVersionFactSummaries(['old +/','new'])).toEqual([{resumeVersionId:'old +/',facts:null}]);
  expect(request).toHaveBeenCalledExactlyOnceWith('/resume-version-fact-summaries?resumeVersionIds=old+%2B%2F&resumeVersionIds=new');
});

it('reads the admitted screening scope and encodes an exact candidate selection', async () => {
  vi.mocked(request).mockResolvedValue([]);

  await talentApi.screeningCandidates();
  expect(request).toHaveBeenLastCalledWith('/screening-candidates');

  await talentApi.screeningCandidates(['candidate +/', 'candidate-2']);
  expect(request).toHaveBeenLastCalledWith('/screening-candidates?candidateIds=candidate+%2B%2F&candidateIds=candidate-2');
});

it('admits the frozen default-score identities with an idempotency key', async () => {
  vi.mocked(request).mockResolvedValue({requestedCount:1,admittedCount:1,alreadyAdmittedCount:0,items:[]});
  const items=[{candidateId:'candidate',resumeVersionId:'resume',jdVersionId:'jd-version',defaultMatchRunId:'default-run'}];

  await talentApi.admitScreening(items);

  expect(request).toHaveBeenLastCalledWith('/screening-admissions',expect.objectContaining({
    method:'POST',
    body:{items},
  }));
  const options=vi.mocked(request).mock.calls.at(-1)?.[1];
  expect(typeof options?.idempotencyKey).toBe('string');
});

it('links a historical intake batch to one exact JD version', async () => {
  vi.mocked(request).mockResolvedValue({id:'batch-1'});

  await talentApi.linkBatchJd('batch-1','jd-version-2');

  expect(request).toHaveBeenLastCalledWith('/intake-batches/batch-1/jd-version',{
    method:'PUT',
    body:{jdVersionId:'jd-version-2',reason:'HR 核对历史收件并补关联 JD'},
  });
});

it('starts a pending intake with the latest server version',async()=>{
  const selected={id:'intake-1',version:1} as ResumeIntake;
  vi.mocked(request)
    .mockResolvedValueOnce({id:'intake-1',status:'PENDING_IMPORT',importStatus:'PENDING',scanStatus:'CLEAN',jdVersionId:'jd-v1',version:7})
    .mockResolvedValueOnce({id:'intake-1',status:'PARSING',importStatus:'PROCESSING',version:8});

  await expect(importCurrentIntake(selected)).resolves.toMatchObject({outcome:'STARTED'});
  expect(request).toHaveBeenNthCalledWith(1,'/intakes/intake-1');
  expect(request).toHaveBeenNthCalledWith(2,'/intakes/intake-1/import',{method:'POST',body:{version:'7'}});
});

it('does not submit an intake already started by another page',async()=>{
  vi.mocked(request).mockResolvedValue({id:'intake-1',status:'PARSING',importStatus:'PROCESSING',version:2});

  await expect(importCurrentIntake({id:'intake-1'} as ResumeIntake)).resolves.toMatchObject({outcome:'ALREADY_PROCESSING'});
  expect(request).toHaveBeenCalledExactlyOnceWith('/intakes/intake-1');
});

it('rejects a row whose latest state is no longer directly importable',async()=>{
  vi.mocked(request).mockResolvedValue({id:'intake-1',status:'IDENTITY_REVIEW_REQUIRED',importStatus:'PENDING',scanStatus:'CLEAN',jdVersionId:'jd-v1',version:2});

  await expect(importCurrentIntake({id:'intake-1'} as ResumeIntake)).rejects.toThrow('当前收件状态已变化');
  expect(request).toHaveBeenCalledExactlyOnceWith('/intakes/intake-1');
});

it('confirms an identity-review intake with the latest server version',async()=>{
  const selected={id:'intake-1',status:'IDENTITY_REVIEW_REQUIRED',version:1} as ResumeIntake;
  vi.mocked(request)
    .mockResolvedValueOnce({...selected,version:7})
    .mockResolvedValueOnce({...selected,status:'CONFIRMED',version:8});

  await expect(confirmCurrentIntake(selected,{fullName:'合成候选人',email:'synthetic-candidate@example.invalid',phone:'13800000000'}))
    .resolves.toMatchObject({status:'CONFIRMED'});
  expect(request).toHaveBeenNthCalledWith(1,'/intakes/intake-1');
  expect(request).toHaveBeenNthCalledWith(2,'/intakes/intake-1/confirm',{
    method:'POST',body:{fullName:'合成候选人',email:'synthetic-candidate@example.invalid',phone:'13800000000',version:7},
  });
});


it('counts all matching intakes with the same filters and an abortable request', async () => {
  vi.mocked(request).mockResolvedValue({ count: 121, nextCursor: null, hasMore: false });
  const controller = new AbortController();
  expect(await talentApi.countIntakes('PENDING', { query: '简历', name: '张三', from: '2026-09-01T00:00:00Z', to: '2026-10-01T00:00:00Z', duplicateContent: true }, controller.signal)).toBe(121);
  const [path, options] = vi.mocked(request).mock.calls[0]!;
  const url = new URL(path, 'https://example.test');
  expect(url.pathname).toBe('/intakes/count');
  expect(Object.fromEntries(url.searchParams)).toEqual({ query: '简历', name: '张三', from: '2026-09-01T00:00:00Z', to: '2026-10-01T00:00:00Z', duplicateContent: 'true', importStatus: 'PENDING' });
  expect(options?.signal).toBe(controller.signal);
});

it('requests isolated reparse for an already received identity-review intake',async()=>{
  vi.mocked(request).mockResolvedValue({id:'intake-1',status:'PARSING',importStatus:'PROCESSING',version:8});
  await talentApi.reparse({id:'intake-1',version:7} as ResumeIntake);
  expect(request).toHaveBeenCalledExactlyOnceWith('/intakes/intake-1/reparse',{
    method:'POST',body:{version:7,reason:'HR 重新解析身份信息缺失的收件'},
  });
});

it('requests a versioned default-score recalculation after resume reparse',async()=>{
  vi.mocked(request).mockResolvedValue({id:'intake-1',status:'COMPLETED',scoreStatus:'QUEUED',version:8});
  await talentApi.recalculateScore({id:'intake-1',version:7} as ResumeIntake);
  expect(request).toHaveBeenCalledExactlyOnceWith('/intakes/intake-1/score/recalculate',{
    method:'POST',body:{version:7,reason:'HR 重新解析简历后更新岗位默认评分'},
  });
});

it('maps unified problem filters to the server status query for lists and totals',async()=>{
  vi.mocked(request)
    .mockResolvedValueOnce({items:[],nextCursor:null,hasMore:false})
    .mockResolvedValueOnce({count:3,nextCursor:null,hasMore:false});

  await talentApi.intakes(undefined,undefined,{problemStatus:'IDENTITY_REVIEW'});
  await expect(talentApi.countIntakes(undefined,{problemStatus:'FAILED'})).resolves.toBe(3);

  expect(request).toHaveBeenNthCalledWith(1,'/intakes?status=IDENTITY_REVIEW&limit=20');
  expect(request).toHaveBeenNthCalledWith(2,'/intakes/count?status=FAILED',{signal:null});
});

it('keeps duplicate intake filtering together with a custom page size and cursor', async () => {
  vi.mocked(request).mockResolvedValue({items:[],hasMore:false,nextCursor:null});
  await talentApi.intakes('PENDING','next+/=',{query:'简历',duplicateContent:true},50);
  const url=new URL(vi.mocked(request).mock.calls[0]![0],'https://example.test');
  expect(Object.fromEntries(url.searchParams)).toEqual({query:'简历',duplicateContent:'true',importStatus:'PENDING',cursor:'next+/=',limit:'50'});
  await talentApi.intakes(undefined,undefined,{duplicateContent:false});
  expect(request).toHaveBeenLastCalledWith('/intakes?limit=20');
});

it('读取上传任务分页，并用任务 ID 在服务端筛选解析收件',async()=>{
  vi.mocked(request).mockResolvedValueOnce({items:[],hasMore:false,nextCursor:null}).mockResolvedValueOnce({items:[],hasMore:false,nextCursor:null}).mockResolvedValueOnce({count:1,hasMore:false,nextCursor:null});
  await talentApi.batches('next+/=',10);
  await talentApi.intakes(undefined,undefined,{batchId:'batch-1'});
  await talentApi.countIntakes(undefined,{batchId:'batch-1'});
  expect(request).toHaveBeenNthCalledWith(1,'/intake-batches?cursor=next%2B%2F%3D&limit=10');
  expect(request).toHaveBeenNthCalledWith(2,'/intakes?batchId=batch-1&limit=20');
  expect(request).toHaveBeenNthCalledWith(3,'/intakes/count?batchId=batch-1',{signal:null});
});
