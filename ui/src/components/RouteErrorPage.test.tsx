import { cleanup, render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { afterEach, expect, it } from 'vitest';
import { RouteErrorPage } from './RouteErrorPage';

afterEach(cleanup);

it('hides unexpected error details from the public error page', async () => {
  const router = createMemoryRouter([{
    path: '/',
    element: <div />,
    hydrateFallbackElement: <div />,
    loader: () => { throw new Error('internal-host=private.example.invalid'); },
    errorElement: <RouteErrorPage />,
  }]);

  render(<RouterProvider router={router} />);

  expect(await screen.findByText('页面加载失败')).toBeInTheDocument();
  expect(screen.getByRole('main')).toHaveTextContent('请稍后重试或返回首页。');
  expect(screen.getByRole('main')).not.toHaveTextContent('private.example.invalid');
});

it('does not display untrusted route response status text', async () => {
  const routeError = Object.assign(new Error('route failed'), {
    status: 404, statusText: 'private route detail', internal: false, data: null,
  });
  const router = createMemoryRouter([{
    path: '/',
    element: <div />,
    hydrateFallbackElement: <div />,
    loader: () => { throw routeError; },
    errorElement: <RouteErrorPage />,
  }]);

  render(<RouterProvider router={router} />);

  expect(await screen.findByText('页面不存在')).toBeInTheDocument();
  expect(screen.getByRole('main')).toHaveTextContent('请检查地址，或返回首页继续工作。');
  expect(screen.getByRole('main')).not.toHaveTextContent('private route detail');
});
