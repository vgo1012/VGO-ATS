import { App,ConfigProvider } from 'antd';
import { QueryClient,QueryClientProvider } from '@tanstack/react-query';
import { cleanup,fireEvent,render,screen,waitFor } from '@testing-library/react';
import { afterEach,beforeEach,expect,it,vi } from 'vitest';
import { recruitmentApi, type ApplicationDetails, type Communication } from './api';
import { LedgerPendingDialog } from './LedgerPendingDialog';

vi.mock('../display/BusinessReferences',()=>({CandidateName:()=> '候选人',JobName:()=> '岗位'}));
const value={application:{id:'app',candidateId:'candidate',jdId:'jd',clientName:null,terminalAt:null},workflowSteps:[]} as unknown as ApplicationDetails;
const closed=vi.fn();
const changed=vi.fn(()=>Promise.resolve());
const mount=()=>render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient()}><LedgerPendingDialog value={value} onClose={closed} onChanged={changed}/></QueryClientProvider></App></ConfigProvider>);
beforeEach(()=>{Object.defineProperty(HTMLElement.prototype,'scrollIntoView',{configurable:true,value:vi.fn()});});
afterEach(()=>{cleanup();vi.restoreAllMocks();vi.clearAllMocks();});

it('待定仅填写沟通情况，保存追加记录而不推进流程',async()=>{
  const save=vi.spyOn(recruitmentApi,'communicate').mockResolvedValue({id:'record'} as Communication);
  const complete=vi.spyOn(recruitmentApi,'completeStep');
  mount();
  expect(screen.getAllByRole('textbox')).toHaveLength(1);
  for(const label of ['渠道','沟通结果','预约跟进','流程管理','下一步计划（选填）'])expect(screen.queryByText(label)).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('沟通情况'),{target:{value:'候选人希望下周沟通'}});
  fireEvent.click(screen.getByRole('button',{name:'保存记录'}));
  await waitFor(()=>expect(closed).toHaveBeenCalledOnce());
  expect(save).toHaveBeenCalledWith('app',{channel:'OTHER',result:'FOLLOW_UP',summary:'候选人希望下周沟通',nextPlan:''},expect.any(String),expect.objectContaining({action:'PENDING'}));
  expect(complete).not.toHaveBeenCalled();
  expect(changed).toHaveBeenCalledOnce();
});

it('空白不保存，网络失败重试同一条记录',async()=>{
  const save=vi.spyOn(recruitmentApi,'communicate').mockRejectedValueOnce(new Error('网络中断')).mockResolvedValue({id:'record'} as Communication);
  mount();
  fireEvent.change(screen.getByLabelText('沟通情况'),{target:{value:'   '}});
  fireEvent.click(screen.getByRole('button',{name:'保存记录'}));
  await waitFor(()=>expect(document.querySelector('.ant-form-item-explain-error')).not.toBeNull());
  expect(save).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText('沟通情况'),{target:{value:'待再次沟通'}});
  fireEvent.click(screen.getByRole('button',{name:'保存记录'}));
  fireEvent.click(await screen.findByRole('button',{name:'重试保存记录'}));
  await waitFor(()=>expect(save).toHaveBeenCalledTimes(2));
  expect(save.mock.calls[0]).toEqual(save.mock.calls[1]);
});
