import {
  EyeInvisibleOutlined,
  EyeOutlined,
  LoadingOutlined,
  LockOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { useRef, useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { ApiProblemError } from '../api/problem';
import { useSession } from '../auth/session';
import { loginDestination } from '../auth/workspaceAccess';
import '../brand/login-experience.css';

interface LoginFields {
  workEmail: string;
  password: string;
}

const rememberedAccountKey = 'vgo.remembered-work-email';

function readRememberedAccount(): string {
  try { return window.localStorage.getItem(rememberedAccountKey) ?? ''; }
  catch { return ''; }
}

export function LoginPage() {
  const session = useSession();
  const navigate = useNavigate();
  const location = useLocation();
  const [form, setForm] = useState<LoginFields>(() => ({
    workEmail: readRememberedAccount(),
    password: '',
  }));
  const [rememberAccount, setRememberAccount] = useState(Boolean(form.workEmail));
  const [showPassword, setShowPassword] = useState(false);
  const [help, setHelp] = useState<'reset' | 'contact'>();
  const [loginError, setLoginError] = useState<string>();
  const [submitting, setSubmitting] = useState(false);
  const submissionRef = useRef(false);
  const errorRef = useRef<HTMLParagraphElement>(null);

  if (session.actor) {
    return <Navigate replace to={session.actor.mustChangePassword ? '/change-password' : loginDestination(session.actor, (location.state as { from?: string } | null)?.from)} />;
  }

  const showLoginError = (message: string) => {
    setHelp(undefined);
    setLoginError(message);
    window.requestAnimationFrame(() => errorRef.current?.scrollIntoView?.({ block: 'nearest' }));
  };

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (submissionRef.current) return;
    const submittedForm = new FormData(event.currentTarget);
    const submittedWorkEmail = submittedForm.get('workEmail');
    const submittedPassword = submittedForm.get('password');
    const workEmail = typeof submittedWorkEmail === 'string' ? submittedWorkEmail.trim() : '';
    const password = typeof submittedPassword === 'string' ? submittedPassword : '';
    if (!workEmail || !password) {
      showLoginError('请输入账号和密码。');
      return;
    }

    setHelp(undefined);
    setLoginError(undefined);
    submissionRef.current = true;
    setSubmitting(true);
    try {
      const actor = await session.login({ workEmail, password });
      // Remembering an email is optional; storage policy must not fail authentication.
      try {
        if (rememberAccount) window.localStorage.setItem(rememberedAccountKey, workEmail);
        else window.localStorage.removeItem(rememberedAccountKey);
      } catch { /* The authenticated session remains usable without local storage. */ }
      const requested = (location.state as { from?: string } | null)?.from;
      void navigate(actor.mustChangePassword ? '/change-password' : loginDestination(actor, requested), { replace: true });
    } catch (error) {
      showLoginError(error instanceof ApiProblemError ? error.problem.detail : '登录失败，请稍后重试。');
    } finally {
      submissionRef.current = false;
      setSubmitting(false);
    }
  };

  return (
    <main className="login-screen" id="main-content">
      <section className="login-brand" aria-label="VGO AI 招聘品牌介绍">
        <button className="login-logo" type="button" onClick={() => void navigate('/')} aria-label="返回品牌首页">
          <img src="/assets/vgo-mark.svg" alt="" width="70" height="54" />
          <span>VGO</span>
        </button>
        <div className="login-brand-copy">
          <h2><span>让人才与机会</span><span>在这里相遇</span></h2>
          <p>连接简历、岗位与招聘流程，让每一次选择更有依据。</p>
        </div>
        <div className="login-illustration">
          <img className="login-brand-art" src="/assets/vgo-talent-connection.png" alt="" width="1376" height="1143" />
        </div>
      </section>

      <section className="login-panel">
        <form onSubmit={submit} noValidate aria-busy={submitting}>
          <div className="login-heading">
            <h1>欢迎回来</h1>
            <p>登录工作台，继续今天的招聘工作。</p>
          </div>

          <div className="login-field">
            <label htmlFor="login-account">账号</label>
            <div className="login-input-shell">
              <UserOutlined aria-hidden="true" />
              <input id="login-account" name="workEmail" type="text" placeholder="请输入账号或工作邮箱" disabled={submitting} value={form.workEmail} onChange={(event) => { setForm({ ...form, workEmail: event.target.value }); setLoginError(undefined); }} autoComplete="username" />
            </div>
          </div>

          <div className="login-field">
            <label htmlFor="login-password">密码</label>
            <div className="login-input-shell">
              <LockOutlined aria-hidden="true" />
              <input id="login-password" name="password" type={showPassword ? 'text' : 'password'} placeholder="请输入密码" disabled={submitting} value={form.password} onChange={(event) => { setForm({ ...form, password: event.target.value }); setLoginError(undefined); }} autoComplete="current-password" />
              <button type="button" onClick={() => setShowPassword((value) => !value)} aria-label={showPassword ? '隐藏密码' : '显示密码'} aria-pressed={showPassword}>
                {showPassword ? <EyeOutlined /> : <EyeInvisibleOutlined />}
              </button>
            </div>
          </div>

          <div className="login-meta">
            <label title="仅记住工作邮箱，不保存密码">
              <input type="checkbox" disabled={submitting} checked={rememberAccount} onChange={(event) => setRememberAccount(event.target.checked)} />
              <span>记住账号</span>
            </label>
            <button type="button" onClick={() => { setHelp('reset'); setLoginError(undefined); }}>忘记密码？</button>
          </div>
          {help === 'reset' ? <p className="login-help" role="status">请联系组织管理员重置密码；重置后首次登录需设置新密码。</p> : null}
          {loginError ? <p className="login-error" role="alert" ref={errorRef}>{loginError}</p> : null}

          <p className="login-help">在浏览器提示时选择保存密码，下次可自动填充。</p>


          <button className="login-submit" type="submit" disabled={submitting}>
            {submitting ? <><LoadingOutlined className="login-spin" aria-hidden="true" /> 正在验证身份</> : '登录'}
          </button>

          <div className="login-support">
            <span>有问题？</span>
            <button type="button" onClick={() => { setHelp('contact'); setLoginError(undefined); }}>联系管理员</button>
          </div>
          {help === 'contact' ? <p className="login-help" role="status">请联系为你分配工作账号的组织管理员，协助处理登录问题。</p> : null}
        </form>
      </section>
    </main>
  );
}
