import { expect, it } from 'vitest';
import type { RecruitmentApplication, WorkflowStep } from '../recruitment/api';
import type { ClientSubmission } from './api';
import { currentSubmission } from './currentSubmission';
const app={id:'app',candidateId:'c',clientId:'client',demandId:'d',jdId:'jd',jdVersionId:'jv',resumeVersionId:'rv'} as RecruitmentApplication;
const sent=(id:string,time:string,extra:Partial<ClientSubmission>={})=>({id,applicationId:'app',candidateId:'c',clientId:'client',demandId:'d',jdId:'jd',jdVersionId:'jv',resumeVersionId:'rv',deliveryStatus:'SENT',sentAt:time,...extra}) as ClientSubmission;
it.each([
  ['application', {applicationId:'other'}], ['candidate', {candidateId:'other'}],
  ['client', {clientId:'other'}], ['demand', {demandId:'other'}], ['JD', {jdId:'other'}],
  ['JD version', {jdVersionId:'other'}], ['resume', {resumeVersionId:'other'}],
])('rejects a submission with a different %s',(_,extra)=>expect(currentSubmission(app,[sent('wrong','2026-03-01',extra),sent('valid','2026-02-01')])?.id).toBe('valid'));
it('prefers a valid explicit binding over a newer sent submission',()=>expect(currentSubmission(app,[sent('bound','2026-01-01'),sent('newer','2026-02-01')],{clientSubmissionId:'bound'} as WorkflowStep)?.id).toBe('bound'));
it.each(['DRAFT','WITHDRAWN'] as const)('does not treat %s as sent',status=>expect(currentSubmission(app,[sent('invalid','2026-03-01',{deliveryStatus:status}),sent('valid','2026-02-01')])?.id).toBe('valid'));
it('does not fall back when a step binding is invalid',()=>expect(currentSubmission(app,[sent('new','2026-02-01')],{clientSubmissionId:'missing'} as WorkflowStep)).toBeUndefined());
