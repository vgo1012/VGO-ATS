import { useQuery } from '@tanstack/react-query';
import { interviewApi } from './api';

export function useInterviewMaterials(interviewId:string) {
  return useQuery({queryKey:['interview-materials',interviewId],queryFn:()=>interviewApi.materials(interviewId),retry:false});
}
