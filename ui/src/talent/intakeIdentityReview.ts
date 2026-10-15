import type { IdentityConflict, ResumeIntake } from './api';

const conflictReasons:Record<string,string>={
  STRONG_IDENTITY_MISMATCH:'手机号、邮箱或所选档案指向不同候选人',
  CONTACT_NAME_MISMATCH:'联系方式匹配已有档案，但姓名不一致',
  NAME_MATCH_CONTACT_MISMATCH:'姓名相同，但联系方式未匹配已有档案',
};

export const requiresAdminIdentityReview=(conflict:IdentityConflict)=>conflict.conflictType==='CANDIDATE_ACCESS_REQUIRED'||!conflict.candidateIdA;

export function identityConflictReview(conflict:IdentityConflict):{reason:string;advice:string}{
  if(requiresAdminIdentityReview(conflict))return {
    reason:conflict.conflictType==='CANDIDATE_ACCESS_REQUIRED'?'联系方式与已有档案一致，疑似同一人多份简历':'关联档案不可查看，请联系有权限的负责人核验',
    advice:'请联系已有档案负责人核实候选人归属和后续沟通；确认前不要自动合并或重复建档。当前账号无法查看或处理关联档案。',
  };
  return {
    reason:conflictReasons[conflict.conflictType]??'身份信息存在冲突，具体差异需人工核验',
    advice:'对照本次简历与已有档案的姓名、手机号和邮箱，核实后选择合适的处理方式；差异不代表哪一方填写错误。',
  };
}

export function intakeIdentityReview(value:ResumeIntake,conflict?:IdentityConflict):{reason:string;advice:string}{
  if(conflict)return identityConflictReview(conflict);
  if(value.failureCode==='IDENTITY_CONFLICT_REQUIRES_REVIEW')return {
    reason:'身份信息与已有候选人存在冲突，具体差异待读取',
    advice:'请刷新核对状态并读取冲突详情；读取失败时重试或联系有权限的负责人，核验前不能按普通缺失信息确认入库。',
  };
  if(value.failureCode==='RESUME_VERSION_DUPLICATE_CONTENT')return {
    reason:'候选人档案中已有相同内容的简历版本',
    advice:'请核对已有档案与简历版本，确认本次收件的归属，不要重复导入；不要仅凭文件内容相同合并候选人。',
  };
  if(value.status==='READY_FOR_CONFIRMATION'&&!value.failureCode)return {
    reason:'待确认入库',
    advice:'请核对本次简历与候选人姓名、联系方式，确认后完成入库；信息有疑问时先联系负责人核验。',
  };
  if((value.failureCode&&value.failureCode!=='IDENTITY_REVIEW_REQUIRED')||
      (value.parsedFullName===undefined&&value.parsedPhone===undefined&&value.parsedEmail===undefined))return {
    reason:'身份核对原因暂未明确',
    advice:'请刷新核对状态并核实原始简历；仍无法确认时联系负责人排查，不要猜填身份信息。',
  };
  const missingName=!value.parsedFullName?.trim();
  const missingContacts=!value.parsedPhone?.trim()&&!value.parsedEmail?.trim();
  if(missingName||missingContacts)return {
    reason:`未识别到${missingName?'姓名':''}${missingName&&missingContacts?'、':''}${missingContacts?'手机号和邮箱':''}，无法自动核对身份`,
    advice:missingName
      ?'请核对原始简历并补充真实姓名；如能核实联系方式可一并补充，不要猜填。'
      :'请核对原始简历，能核实的手机号或邮箱可补充；原文确实未提供联系方式时，可核实姓名后人工确认，不必虚构联系方式。',
  };
  return {reason:'身份信息需要人工核对，具体原因暂未明确',advice:'请核对原始简历中的姓名与联系方式，并刷新查看是否存在身份冲突；不确定时联系负责人核验。'};
}
