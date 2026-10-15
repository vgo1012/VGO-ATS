import { App, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { CopyInterviewInfo } from './CopyInterviewInfo';
import { interviewInfoText } from './interviewInfoText';
import type { InterviewDetails, InterviewMaterial } from './api';

const material={candidateName:'示例甲',jdTitle:'IDC运维工程师',jdCode:'JD-980980999'} as InterviewMaterial;
vi.mock('./useInterviewMaterials',()=>({useInterviewMaterials:()=>({data:{candidateName:'示例甲',jdTitle:'IDC运维工程师',jdCode:'JD-980980999'}})}));
const value={interview:{id:'i',status:'SCHEDULED',stepCode:'INTERVIEW_ROUND_1',roundNo:1,interviewType:'INTERNAL',scheduledStartAt:'2026-09-20T03:40:00Z',scheduledEndAt:'2026-09-20T04:10:00Z',meetingMode:'ONLINE'},participants:[],externalCalendar:{mode:'WECOM_MEETING',status:'SYNCED',meetingCode:'123456789',meetingLink:'https://meeting.example.test/join'}} as unknown as InterviewDetails;
afterEach(()=>{cleanup();vi.unstubAllGlobals();});

it('copies the current schedule and meeting details without internal evaluation or history',async()=>{
  const writeText=vi.fn().mockResolvedValue(undefined);
  vi.stubGlobal('navigator',{clipboard:{writeText}});
  render(<App><CopyInterviewInfo value={value}/></App>);
  fireEvent.click(screen.getByRole('button',{name:/复制面试信息/}));
  await waitFor(()=>expect(writeText).toHaveBeenCalledOnce());
  const text=writeText.mock.calls[0]![0] as string;
  for(const part of ['示例甲','IDC运维工程师','JD-980980999','2026/09/20 11:40','2026/09/20 12:10','北京时间','123456789','https://meeting.example.test/join'])expect(text).toContain(part);
  expect(text).not.toContain('面评');
});

it('offers selectable text when the clipboard is denied',async()=>{
  vi.stubGlobal('navigator',{clipboard:{writeText:vi.fn().mockRejectedValue(new Error('denied'))}});
  render(<ConfigProvider theme={{token:{motion:false}}}><App><CopyInterviewInfo value={value}/></App></ConfigProvider>);
  fireEvent.click(screen.getByRole('button',{name:/复制面试信息/}));
  expect(await screen.findByRole('textbox',{name:'待复制的面试信息'})).toHaveValue(interviewInfoText(value,material));
});

it('does not copy cancelled meeting access and explains missing access information',()=>{
  expect(interviewInfoText({...value,interview:{...value.interview,status:'CANCELLED'}},material)).not.toContain('123456789');
  const pending={...value,externalCalendar:{...value.externalCalendar!,meetingCode:null,meetingLink:null,status:'PENDING' as const}};
  expect(interviewInfoText(pending,material)).toContain('尚未生成，待 HR 补充');
  expect(interviewInfoText({...value,externalCalendar:null,interview:{...value.interview,meetingMode:'ONSITE',locationText:'北京会议室'}},material)).toContain('面试地点：北京会议室');
});

it('omits unsafe meeting links and falls back to a valid interview link',()=>{
  const unsafe={...value,externalCalendar:{...value.externalCalendar!,meetingLink:'javascript:alert(1)'}};
  expect(interviewInfoText(unsafe,material)).not.toContain('javascript:');
  expect(interviewInfoText({...unsafe,interview:{...unsafe.interview,meetingUrl:'https://meeting.example.test/backup'}},material)).toContain('会议链接：https://meeting.example.test/backup');
});
