import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { SourceChannelModal } from './SourceChannelModal';

describe('SourceChannelModal', () => {
  afterEach(cleanup);
  it('focuses the invalid name and lets the user correct and save it', async () => {
    const onCreate = vi.fn().mockResolvedValue(undefined);
    render(<ConfigProvider theme={{ token: { motion: false } }}><App><QueryClientProvider client={new QueryClient()}>
      <SourceChannelModal value="new" onClose={vi.fn()} onSaved={vi.fn().mockResolvedValue(undefined)} onCreate={onCreate} onUpdate={vi.fn()}/>
    </QueryClientProvider></App></ConfigProvider>);
    const save = screen.getByRole('button', { name: /保存渠道/ });
    const name = screen.getByRole('textbox', { name: '名称' });
    const scrollIntoView = vi.fn();
    name.scrollIntoView = scrollIntoView;
    save.focus();
    fireEvent.click(save);
    await screen.findByText('请输入渠道名称');
    await waitFor(() => expect(name).toHaveFocus());
    expect(scrollIntoView).toHaveBeenCalledWith({ block: 'nearest' });
    expect(onCreate).not.toHaveBeenCalled();
    fireEvent.change(name, { target: { value: '  校园招聘  ' } });
    fireEvent.click(save);
    await waitFor(() => expect(onCreate).toHaveBeenCalledExactlyOnceWith({ name: '校园招聘', channelType: 'MANUAL' }));
  });
  it('creates a channel without asking the user for a code', async () => {
    const onCreate = vi.fn().mockResolvedValue(undefined);
    render(
      <ConfigProvider theme={{ token: { motion: false } }}>
        <App>
          <QueryClientProvider client={new QueryClient()}>
            <SourceChannelModal
              value="new"
              onClose={vi.fn()}
              onSaved={vi.fn().mockResolvedValue(undefined)}
              onCreate={onCreate}
              onUpdate={vi.fn()}
            />
          </QueryClientProvider>
        </App>
      </ConfigProvider>,
    );

    expect(screen.queryByRole('textbox', { name: /编码/ })).not.toBeInTheDocument();
    expect(await screen.findByText('渠道编码将在保存时自动生成')).toBeInTheDocument();
    fireEvent.change(screen.getByRole('textbox', { name: '名称' }), { target: { value: '内部推荐' } });
    fireEvent.click(screen.getByRole('button', { name: /保存渠道/ }));

    await waitFor(() =>
      expect(onCreate).toHaveBeenCalledWith({ name: '内部推荐', channelType: 'MANUAL' }),
    );
  });
});
