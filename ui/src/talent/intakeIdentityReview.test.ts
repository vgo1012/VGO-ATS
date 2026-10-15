import { expect, it } from 'vitest';
import type { IdentityConflict, ResumeIntake } from './api';
import { intakeIdentityReview } from './intakeIdentityReview';

const review=(fields:Partial<ResumeIntake>,conflict?:Partial<IdentityConflict>)=>intakeIdentityReview({failureCode:'IDENTITY_REVIEW_REQUIRED',parsedFullName:'测试人员',parsedPhone:null,parsedEmail:null,...fields} as ResumeIntake,conflict?{candidateIdA:'existing',candidateIdB:null,...conflict} as IdentityConflict:undefined);

it('distinguishes missing name, missing both contacts and missing all identity fields',()=>{
  expect(review({}).reason).toBe('未识别到手机号和邮箱，无法自动核对身份');
  expect(review({parsedFullName:'  ',parsedEmail:'person@example.com'}).reason).toBe('未识别到姓名，无法自动核对身份');
  expect(review({parsedFullName:null}).reason).toBe('未识别到姓名、手机号和邮箱，无法自动核对身份');
  expect(review({parsedPhone:'13800000000'}).reason).not.toContain('未识别');
  expect(review({}).advice).toContain('不必虚构联系方式');
});

it.each([
  ['STRONG_IDENTITY_MISMATCH','手机号、邮箱或所选档案指向不同候选人'],
  ['CONTACT_NAME_MISMATCH','联系方式匹配已有档案，但姓名不一致'],
  ['NAME_MATCH_CONTACT_MISMATCH','姓名相同，但联系方式未匹配已有档案'],
])('explains %s using recorded conflict type rather than guessing from missing fields',(conflictType,reason)=>{
  expect(review({parsedFullName:null},{conflictType}).reason).toBe(reason);
});

it('keeps unavailable conflict details and unfamiliar codes explicit',()=>{
  expect(review({failureCode:'IDENTITY_CONFLICT_REQUIRES_REVIEW'}).reason).toBe('身份信息与已有候选人存在冲突，具体差异待读取');
  expect(review({failureCode:'IDENTITY_CONFLICT_REQUIRES_REVIEW'}).advice).toContain('不能按普通缺失信息确认入库');
  expect(review({failureCode:'NEW_REASON',parsedFullName:null}).reason).toBe('身份核对原因暂未明确');
  expect(review({},{conflictType:'NEW_CONFLICT'}).reason).toBe('身份信息存在冲突，具体差异需人工核验');
});

it('guides contact with the existing owner when a matched candidate is outside the current data scope',()=>{
  const explanation=review({},{conflictType:'CANDIDATE_ACCESS_REQUIRED',candidateIdA:null,candidateIdB:null});
  expect(explanation.reason).toBe('联系方式与已有档案一致，疑似同一人多份简历');
  expect(explanation.advice).toContain('联系已有档案负责人核实候选人归属和后续沟通');
  expect(explanation.advice).toContain('不要自动合并或重复建档');
  expect(explanation.advice).toContain('当前账号无法查看或处理关联档案');
});

it('does not infer missing fields when an older response omits all parsed identity properties',()=>{
  const value={failureCode:'IDENTITY_REVIEW_REQUIRED'} as ResumeIntake;
  expect(intakeIdentityReview(value).reason).toBe('身份核对原因暂未明确');
});

it('identifies duplicate resume content without presenting it as missing contacts',()=>{
  expect(review({failureCode:'RESUME_VERSION_DUPLICATE_CONTENT'}).reason).toBe('候选人档案中已有相同内容的简历版本');
});

it('explains a ready intake as manual confirmation without inventing an identity exception',()=>{
  const explanation=review({status:'READY_FOR_CONFIRMATION',failureCode:null,parsedPhone:'13800000000'});
  expect(explanation.reason).toBe('待确认入库');
  expect(explanation.advice).toContain('核对本次简历');
  expect(explanation.advice).not.toContain('异常身份');
});
