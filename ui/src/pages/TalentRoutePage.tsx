import { Navigate, useLocation } from 'react-router-dom';
import { TalentWorkspacePage } from './TalentWorkspacePage';

export function LegacyIntakeRoute() {
  const {search}=useLocation();
  const params=new URLSearchParams(search);
  params.set('view','pool');params.set('intake','1');
  return <Navigate to={`/talents?${params}`} replace/>;
}

export function TalentRoutePage() {
  const location = useLocation();
  const params = new URLSearchParams(location.search);
  const legacyView = params.get('view');
  const retiredFilters = [...params.keys()].filter(key => key.startsWith('todo_'));
  if (legacyView === 'todo' || retiredFilters.length) {
    if (legacyView === 'todo') params.set('view', 'pool');
    retiredFilters.forEach(key => params.delete(key));
    return <Navigate to={`/talents?${params}`} replace />;
  }
  if (legacyView && ['library', 'intakes', 'channels', 'conflicts'].includes(legacyView)) {
    params.delete('view');
    const target = legacyView === 'library' ? '/shared-library' : legacyView === 'intakes' ? '/settings/intakes' : legacyView === 'channels' ? '/admin/source-channels' : '/talents';
    if (legacyView === 'conflicts') params.set('view', 'pool');
    return <Navigate to={`${target}?${params}`} replace />;
  }
  return <TalentWorkspacePage />;
}
