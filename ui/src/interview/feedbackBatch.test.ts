import { afterEach, expect, it, vi } from 'vitest';
import { request } from '../api/http';
import { interviewApi } from './api';

vi.mock('../api/http',()=>({request:vi.fn(),requestFile:vi.fn()}));
afterEach(()=>vi.resetAllMocks());
const item=(id:string)=>({feedback:{id,interviewId:'a',status:'SUBMITTED',createTime:'2026-09-01T00:00:00Z'},currentVersion:{createTime:'2026-09-01T00:00:00Z'}});

it('跨页完整读取并规范化日期，固定排序去重面试集合',async()=>{
  vi.mocked(request).mockResolvedValueOnce({items:[item('first')],visibleInterviewIds:['a','b'],hasMore:true,nextCursor:'next+/='})
    .mockResolvedValueOnce({items:[item('last')],visibleInterviewIds:['a','b'],hasMore:false,nextCursor:null});
  const result=await interviewApi.feedbackBatchAll(['b','a','a']);
  expect(result.items.map(value=>value.feedback.id)).toEqual(['first','last']);
  expect(result.items[1]?.currentVersion.createdAt).toBe('2026-09-01T00:00:00Z');
  expect(request).toHaveBeenNthCalledWith(1,'/interview-feedback-batch?interviewIds=a&interviewIds=b&limit=20');
  expect(request).toHaveBeenNthCalledWith(2,'/interview-feedback-batch?interviewIds=a&interviewIds=b&limit=20&cursor=next%2B%2F%3D');
});

it('空集合不发请求，超限集合不提交',async()=>{
  expect(await interviewApi.feedbackBatchAll([])).toEqual({items:[],visibleInterviewIds:[]});
  await expect(interviewApi.feedbackBatchAll(Array.from({length:101},(_,i)=>String(i)))).rejects.toThrow('100');
  expect(request).not.toHaveBeenCalled();
});

it('重复游标或后续页面失败不能伪装完整历史',async()=>{
  vi.mocked(request).mockResolvedValue({items:[],visibleInterviewIds:['a'],hasMore:true,nextCursor:'same'});
  await expect(interviewApi.feedbackBatchAll(['a'])).rejects.toThrow('分页读取异常');
  vi.mocked(request).mockReset().mockResolvedValueOnce({items:[item('first')],visibleInterviewIds:['a'],hasMore:true,nextCursor:'next'}).mockRejectedValueOnce(new Error('权限变化'));
  await expect(interviewApi.feedbackBatchAll(['a'])).rejects.toThrow('权限变化');
});
