import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { Button, Form, Input, Typography } from 'antd';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiProblemError } from '../api/problem';
import { useSession } from '../auth/session';
import '../brand/login-experience.css';

interface PasswordFields {
  currentPassword: string;
  newPassword: string;
  confirmation: string;
}

export function ChangePasswordPage() {
  const session = useSession();
  const navigate = useNavigate();
  const [problem, setProblem] = useState<string>();
  const [submitting, setSubmitting] = useState(false);

  const submit = async (values: PasswordFields) => {
    setProblem(undefined);
    setSubmitting(true);
    try {
      await session.changePassword({ currentPassword: values.currentPassword, newPassword: values.newPassword });
      void navigate('/login', { replace: true });
    } catch (error) {
      setProblem(error instanceof ApiProblemError ? error.problem.detail : '密码更新失败，请稍后重试。');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="login-screen password-screen" id="main-content">
      <section className="login-brand" aria-label="VGO 账号安全">
        <button className="login-logo" type="button" onClick={() => void navigate('/')} aria-label="返回品牌首页"><img src="/assets/vgo-mark.svg" alt="" width="70" height="54" /><span>VGO</span></button>
        <div className="login-illustration">
          <img className="login-brand-art" src="/assets/vgo-talent-connection.png" alt="" width="1376" height="1143" />
        </div>
      </section>
      <section className="login-panel password-panel">
        <Form<PasswordFields> layout="vertical" onFinish={submit}>
          <div className="login-heading"><span>首次登录安全步骤</span><h1>设置新密码</h1><p>请输入新密码，并再次输入以确认。</p></div>
          {problem ? <Alert showIcon type="error" title={problem} /> : null}
          <Form.Item label="当前密码" name="currentPassword" rules={[{ required: true, message: '请输入当前密码' }]}>
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Form.Item
            label="新密码"
            name="newPassword"
            rules={[
              { required: true, whitespace: true, message: '请输入新密码' },
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            label="确认新密码"
            name="confirmation"
            dependencies={['newPassword']}
            rules={[
              { required: true, message: '请再次输入新密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  return !value || getFieldValue('newPassword') === value
                    ? Promise.resolve()
                    : Promise.reject(new Error('两次输入的密码不一致'));
                },
              }),
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Button block type="primary" htmlType="submit" loading={submitting}>更新密码</Button>
        </Form>
        <Typography.Text className="login-foot">完成后将返回登录页，请使用新密码重新登录。</Typography.Text>
      </section>
    </main>
  );
}
