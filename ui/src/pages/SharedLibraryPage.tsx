import { Typography } from 'antd';
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { canDownloadResume } from '../auth/permissions';
import { useSession } from '../auth/session';
import { detailOrigin, type DetailOrigin } from '../components/detailMotion';
import { CandidateDialog } from '../talent/CandidateDetails';
import { TalentSearchPanel } from './TalentSearchPanel';

export function SharedLibraryPage() {
  const { actor } = useSession();
  const [params, setParams] = useSearchParams();
  const candidateId = params.get('candidate') ?? undefined;
  const [drawerCandidateId, setDrawerCandidateId] = useState(candidateId);
  const [candidateOrigin, setCandidateOrigin] = useState<DetailOrigin>();
  if (candidateId && candidateId !== drawerCandidateId) setDrawerCandidateId(candidateId);
  const openCandidate = (id: string) => {
    setCandidateOrigin(detailOrigin(document.activeElement));
    setParams(previous => { const next = new URLSearchParams(previous); next.set('candidate', id); return next; });
  };
  const closeCandidate = () => setParams(previous => { const next = new URLSearchParams(previous); next.delete('candidate'); return next; });

  return <div className="workspace-page shared-library-page pagination-workspace">
    <section className="ledger-heading"><div><Typography.Title level={1}>共享简历库</Typography.Title></div></section>
    <TalentSearchPanel onOpenCandidate={openCandidate} />
    <CandidateDialog compact sharedLibrary key={drawerCandidateId} {...(drawerCandidateId ? { candidateId: drawerCandidateId } : {})} open={Boolean(candidateId)} motionFrom={candidateOrigin} canDownload={canDownloadResume(actor)} onClose={closeCandidate} />
  </div>;
}
