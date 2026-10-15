import { ArrowUpOutlined, FileTextOutlined, SearchOutlined, TeamOutlined } from '@ant-design/icons';
import './landing-experience.css';

export function LandingExperience({ onEnterWorkspace }: { onEnterWorkspace: () => void }) {
  return (
    <div className="landing-home">
      <header className="landing-header">
        <div className="landing-brand" role="img" aria-label="VGO">
          <img src="/assets/vgo-mark.svg" alt="" width="54" height="54" />
          <span>VGO</span>
        </div>
        <button className="landing-login" type="button" onClick={onEnterWorkspace}>
          登录工作台 <ArrowUpOutlined rotate={45} aria-hidden="true" />
        </button>
      </header>
      <main className="landing-hero" aria-labelledby="landing-title">
        <div className="landing-copy">
          <h1 className="landing-title" id="landing-title">
            <span>让人才与机会</span>
            <span>在这里相遇</span>
          </h1>
          <p className="landing-description">连接简历、岗位与招聘流程，让每一次选择更有依据。</p>
        </div>
        <div className="landing-visual">
          <img
            className="landing-art"
            src="/assets/vgo-talent-connection.png"
            alt=""
            width="1376"
            height="1143"
            fetchPriority="high"
          />
        </div>
      </main>
      <footer className="landing-footer">
        <ul className="landing-capabilities" aria-label="招聘能力">
          <li><FileTextOutlined aria-hidden="true" /><span>简历解析</span></li>
          <li><SearchOutlined aria-hidden="true" /><span>智能匹配</span></li>
          <li><TeamOutlined aria-hidden="true" /><span>招聘协同</span></li>
        </ul>
      </footer>
    </div>
  );
}
