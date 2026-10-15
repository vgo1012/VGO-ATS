import { beforeEach,expect,it,vi } from 'vitest';
import { matchingApi, type MatchResult } from './api';
import { selectMatchResults } from './selectMatchResults';
beforeEach(()=>vi.resetAllMocks());
it('历史执行的全选仍使用原运行结果接口',async()=>{
 const results=vi.spyOn(matchingApi,'results').mockResolvedValue({items:[],hasMore:false,nextCursor:null});
 await expect(selectMatchResults('history-run')).resolves.toEqual([]);
 expect(results).toHaveBeenCalledWith('history-run',undefined);
 results.mockRestore();
});
it('岗位汇总全选遍历同一判断依据的所有候选人页',async()=>{
 const loadPage=vi.fn().mockResolvedValueOnce({items:[{resultId:'a',candidateId:'c1'} as MatchResult],nextCursor:'next',hasMore:true}).mockResolvedValueOnce({items:[{resultId:'b',candidateId:'c2'} as MatchResult],nextCursor:null,hasMore:false});
 expect((await selectMatchResults('basis-run',loadPage)).map(item=>item.resultId)).toEqual(['a','b']);
 expect(loadPage).toHaveBeenLastCalledWith('next');
});
it('分页错误时拒绝返回不完整的全选集合',async()=>{
 const loadPage=vi.fn().mockResolvedValueOnce({items:[{resultId:'a'} as MatchResult],nextCursor:'next',hasMore:true}).mockRejectedValueOnce(new Error('连接中断'));
 await expect(selectMatchResults('basis-run',loadPage)).rejects.toThrow('连接中断');
});
it('阻止重复游标形成无限循环',async()=>{
 const loadPage=vi.fn().mockResolvedValue({items:[{resultId:'a'} as MatchResult],nextCursor:'same',hasMore:true});
 await expect(selectMatchResults('basis-run',loadPage)).rejects.toThrow('原有选择未改变');
 expect(loadPage).toHaveBeenCalledTimes(2);
});
