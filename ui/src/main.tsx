import React from 'react';
import ReactDOM from 'react-dom/client';
import { AppProviders } from './app/AppProviders';
import { registerPreloadErrorRecovery } from './app/preloadRecovery';
import './styles/global.css';
import './talent/talent-filters.css';
import './styles/candidate-tables.css';
import './styles/table-actions.css';
import './styles/interactions.css';

const root = document.getElementById('root');

if (!root) {
  throw new Error('缺少应用挂载节点 #root');
}

registerPreloadErrorRecovery();

ReactDOM.createRoot(root).render(
  <React.StrictMode>
    <AppProviders />
  </React.StrictMode>,
);
