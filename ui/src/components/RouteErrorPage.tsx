import { Button, Result, Typography } from 'antd';
import { isRouteErrorResponse, useRouteError } from 'react-router-dom';

export function NotFoundPage() {
  return (
    <main className="error-page">
      <Result
        status="404"
        title="页面不存在"
        subTitle={<Typography.Text type="secondary">请检查地址，或返回首页继续工作。</Typography.Text>}
        extra={<Button type="primary" href="/">返回首页</Button>}
      />
    </main>
  );
}

export function RouteErrorPage() {
  const error = useRouteError();
  const status = isRouteErrorResponse(error) ? error.status : 500;
  const detail = status === 404 ? '请检查地址，或返回首页继续工作。' : '请稍后重试或返回首页。';

  return (
    <main className="error-page">
      <Result
        status={status === 404 ? '404' : '500'}
        title={status === 404 ? '页面不存在' : '页面加载失败'}
        subTitle={<Typography.Text type="secondary">{detail}</Typography.Text>}
        extra={
          <Button type="primary" href="/">
            返回首页
          </Button>
        }
      />
    </main>
  );
}
