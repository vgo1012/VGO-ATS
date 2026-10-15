import { expect,it } from 'vitest';
import { talentHandoff } from './talentHandoff';
it('从人才池带入去重候选人与岗位，忽略非法状态',()=>{
 expect(talentHandoff({candidateIds:['a','a','b',null,1],jdId:'jd'})).toEqual({candidateIds:['a','b'],jdId:'jd'});
 expect(talentHandoff(null)).toEqual({candidateIds:[]});
 expect(talentHandoff({candidateIds:Array.from({length:1001},(_,i)=>String(i))})).toEqual({candidateIds:[]});
});
