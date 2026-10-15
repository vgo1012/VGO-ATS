import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { FeedbackAlert } from './FeedbackAlert';

function mount(children: ReactNode) {
  return render(
    <ConfigProvider theme={{ token: { motion: false } }}>
      <App>{children}</App>
    </ConfigProvider>,
  );
}

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

describe('FeedbackAlert', () => {
  it('shows an error for five seconds even while hovered', () => {
    vi.useFakeTimers({ toFake: ['Date', 'requestAnimationFrame', 'cancelAnimationFrame', 'setTimeout', 'clearTimeout'] });
    mount(<FeedbackAlert type="error" title="导入失败" description="请检查网络后重试" />);

    expect(screen.getByText('导入失败')).toBeInTheDocument();
    fireEvent.mouseEnter(document.querySelector('.ant-notification-notice')!);

    act(() => { vi.advanceTimersByTime(4_999); });
    expect(screen.getByText('导入失败')).toBeInTheDocument();

    // Ant Design advances its duration clock on animation frames; the next frame
    // after the five-second boundary performs the close.
    act(() => { vi.advanceTimersByTime(32); });
    expect(screen.queryByText('导入失败')).not.toBeInTheDocument();
  });

  it('provides an accessible manual close control', () => {
    mount(<FeedbackAlert type="error" title="保存失败" />);

    fireEvent.click(screen.getByRole('button', { name: '关闭错误提示' }));

    expect(screen.queryByText('保存失败')).not.toBeInTheDocument();
  });

  it('does not reopen the same occurrence but shows a new occurrence with the same title', () => {
    const firstOccurrence = new Error('连接中断');
    const view = mount(<FeedbackAlert type="error" title="连接中断" occurrence={firstOccurrence} />);

    fireEvent.click(screen.getByRole('button', { name: '关闭错误提示' }));
    view.rerender(
      <ConfigProvider theme={{ token: { motion: false } }}>
        <App><FeedbackAlert type="error" title="连接中断" occurrence={firstOccurrence} /></App>
      </ConfigProvider>,
    );

    expect(screen.queryByText('连接中断')).not.toBeInTheDocument();

    view.rerender(
      <ConfigProvider theme={{ token: { motion: false } }}>
        <App><FeedbackAlert type="error" title="连接中断" occurrence={new Error('连接中断')} /></App>
      </ConfigProvider>,
    );

    expect(screen.getByText('连接中断')).toBeInTheDocument();
  });

  it('destroys the notification when its error source unmounts', () => {
    const view = mount(<FeedbackAlert type="error" title="即将离开" />);

    expect(screen.getByText('即将离开')).toBeInTheDocument();
    view.rerender(
      <ConfigProvider theme={{ token: { motion: false } }}>
        <App />
      </ConfigProvider>,
    );

    expect(screen.queryByText('即将离开')).not.toBeInTheDocument();
  });

  it('shows a new notification when the error changes', () => {
    const view = mount(<FeedbackAlert type="error" title="首次失败" />);

    fireEvent.click(screen.getByRole('button', { name: '关闭错误提示' }));
    view.rerender(
      <ConfigProvider theme={{ token: { motion: false } }}>
        <App><FeedbackAlert type="error" title="再次失败" /></App>
      </ConfigProvider>,
    );

    expect(screen.queryByText('首次失败')).not.toBeInTheDocument();
    expect(screen.getByText('再次失败')).toBeInTheDocument();
  });

  it('turns a dynamic info status into an error notification', () => {
    const view = mount(<FeedbackAlert type="info" title="任务处理中" />);

    expect(document.querySelector('.ant-alert-info')).toBeInTheDocument();
    expect(document.querySelector('.ant-notification-notice')).not.toBeInTheDocument();

    view.rerender(
      <ConfigProvider theme={{ token: { motion: false } }}>
        <App><FeedbackAlert type="error" title="任务失败" /></App>
      </ConfigProvider>,
    );

    expect(document.querySelector('.ant-alert-info')).not.toBeInTheDocument();
    expect(document.querySelector('.ant-notification-notice')).toBeInTheDocument();
    expect(screen.getByText('任务失败')).toBeInTheDocument();
  });

  it('closes notifications independently and keeps recovery actions usable', () => {
    const retry = vi.fn();
    mount(<><FeedbackAlert type="error" title="读取失败" action={<button type="button" onClick={retry}>重试读取</button>} /><FeedbackAlert type="error" title="保存失败" /></>);

    fireEvent.click(screen.getByRole('button', { name: '重试读取' }));
    expect(retry).toHaveBeenCalledOnce();

    fireEvent.click(screen.getAllByRole('button', { name: '关闭错误提示' })[0]!);
    expect(screen.queryByText('读取失败')).not.toBeInTheDocument();
    expect(screen.getByText('保存失败')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '重试读取' })).toBeInTheDocument();
  });
});
