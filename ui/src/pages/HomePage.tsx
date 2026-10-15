import { useNavigate } from 'react-router-dom';
import { LandingExperience } from '../brand/LandingExperience';

export function HomePage() {
  const navigate = useNavigate();

  return <LandingExperience onEnterWorkspace={() => void navigate('/login')} />;
}
