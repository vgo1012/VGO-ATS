import { afterEach, describe, expect, it, vi } from 'vitest';
import { interviewApi, type FeedbackView } from './api';
const feedback=(author:string)=>({feedback:{authorUserId:author}} as FeedbackView);
afterEach(()=>vi.restoreAllMocks());
describe('find saved feedback across authorized pages',()=>{
  it('continues beyond the first page instead of presenting an existing draft as empty',async()=>{
    const own=feedback('author');
    const page=vi.spyOn(interviewApi,'feedbackPage').mockResolvedValueOnce({items:Array.from({length:50},()=>feedback('other')),hasMore:true,nextCursor:'page2'})
      .mockResolvedValueOnce({items:[own],hasMore:false,nextCursor:null});
    expect(await interviewApi.feedbackByAuthor('interview','author')).toBe(own);
    expect(page).toHaveBeenLastCalledWith('interview','page2');
  });
  it('propagates later-page failures instead of allowing a blank overwrite',async()=>{
    vi.spyOn(interviewApi,'feedbackPage').mockResolvedValueOnce({items:[],hasMore:true,nextCursor:'page2'}).mockRejectedValueOnce(new Error('403'));
    await expect(interviewApi.feedbackByAuthor('interview','author')).rejects.toThrow('403');
  });
});
