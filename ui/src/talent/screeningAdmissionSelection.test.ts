import { expect, it } from 'vitest';
import type { CandidatePoolStatus, ScreeningCandidate } from './api';
import { selectScreeningContexts, ScreeningSelectionError } from './screeningAdmissionSelection';

const score=(candidateId:string,jdVersionId='version'):ScreeningCandidate=>({candidateId,resumeVersionId:`resume-${candidateId}`,jdVersionId,defaultMatchRunId:`run-${candidateId}-${jdVersionId}`,defaultResult:{professionalScore:80,bonusScore:0,referenceScore:80,mustTier:'PASS'},admissionStatus:'READY',admissionId:null,admissionVersion:null,admittedAt:null});
const status=(candidateId:string):CandidatePoolStatus=>({candidateId,resumeVersionId:`resume-${candidateId}`,libraryEligible:false,screenedOut:false,canScreenOut:true,stages:[],jobContexts:[{jdId:'job',jdVersionId:'version',title:'岗位',versionNo:1,resumeVersionId:`resume-${candidateId}`,status:'PENDING',stage:null,applicationId:null,matchRunId:null}]});

it('rejects cancelled historical jobs and requires shared transfer before screening',()=>{
  const state=status('a'); state.jobContexts![0]!.status='FINISHED';
  expect(()=>selectScreeningContexts(['a'],[score('a')],[state])).toThrow();
  expect(()=>selectScreeningContexts(['a'],[score('a')],[{...status('a'),libraryEligible:true}])).toThrow();
});

it('uses the current claimed run instead of an older score for the same JD',()=>{
  const state=status('a'),current=score('a');
  state.jobContexts![0]!.matchRunId=current.defaultMatchRunId;
  const historical={...current,defaultMatchRunId:'cancelled-old-run'};
  expect(selectScreeningContexts(['a'],[historical,current],[state])).toEqual([{context:current,jdId:'job'}]);
  expect(()=>selectScreeningContexts(['a'],[historical],[state])).toThrow();
});

it('reports every affected candidate and reason without blaming valid selections',()=>{
  expect.assertions(3);
  try {
    selectScreeningContexts(['missing','valid','ambiguous','incomplete'],[score('valid'),score('ambiguous'),score('ambiguous'),score('incomplete')],[status('missing'),status('valid'),status('ambiguous'),{...status('incomplete'),jobContexts:[]}]);
  }catch(error){
    expect(error).toBeInstanceOf(ScreeningSelectionError);
    const issues=(error as ScreeningSelectionError).issues;
    expect(issues.map(issue=>issue.candidateId)).toEqual(['missing','ambiguous','incomplete']);
    expect(issues.map(issue=>issue.reason)).toEqual(['所选简历尚未完成公司默认评分，请刷新后重试','所选简历关联了多个已评分岗位，无法确定唯一岗位','已评分岗位上下文不完整，请刷新后重试']);
  }
});

it('preserves selection order and original score objects while ignoring other resumes and candidates',()=>{
  const a=score('a'),b=score('b');
  const historical={...a,resumeVersionId:'old-resume'};
  const result=selectScreeningContexts(['b','a'],[a,historical,score('unselected'),b],[status('a'),status('b')]);
  expect(result).toEqual([{context:b,jdId:'job'},{context:a,jdId:'job'}]);
  expect(result[0]?.context).toBe(b);
});

it('filters the selected logical job before resolving scored versions',()=>{
  const state=status('a');
  state.jobContexts!.push({...state.jobContexts![0]!,jdId:'other-job',jdVersionId:'other-version'});
  const chosen=score('a');
  expect(selectScreeningContexts(['a'],[score('a','other-version'),chosen],[state],'job'))
    .toEqual([{context:chosen,jdId:'job'}]);
  expect(()=>selectScreeningContexts(['a'],[score('a','other-version')],[state],'job'))
    .toThrow('所选简历尚未完成公司默认评分');
});

it('keeps duplicate scores ambiguous even when they reference the same JD',()=>{
  expect(()=>selectScreeningContexts(['a'],[score('a'),score('a')],[status('a')]))
    .toThrow('所选简历关联了多个已评分岗位');
});

it('rejects missing current state, stale resumes and incomplete or conflicting job context',()=>{
  expect(()=>selectScreeningContexts(['a'],[score('a')],[])).toThrow('尚未完成公司默认评分');
  expect(()=>selectScreeningContexts(['a'],[{...score('a'),resumeVersionId:'old'}],[status('a')]))
    .toThrow('尚未完成公司默认评分');
  const state=status('a');
  expect(()=>selectScreeningContexts(['a'],[score('a')],[{...state,jobContexts:[]}]))
    .toThrow('已评分岗位上下文不完整');
  state.jobContexts!.push({...state.jobContexts![0]!,jdId:'conflicting-job'});
  expect(()=>selectScreeningContexts(['a'],[score('a')],[state])).toThrow('已评分岗位上下文不完整');
});

it('reads candidate identities once when resolving a 1000-person batch',()=>{
  let reads=0;
  const ids=Array.from({length:1000},(_,index)=>`candidate-${index}`);
  const contexts=ids.map(id=>({...score(id),get candidateId(){reads++;return id;}}));
  const result=selectScreeningContexts(ids,contexts,ids.map(status));
  expect(result).toHaveLength(1000);
  expect(reads).toBeLessThanOrEqual(1000);
  expect(result.every((value,index)=>value.context===contexts[index])).toBe(true);
});
