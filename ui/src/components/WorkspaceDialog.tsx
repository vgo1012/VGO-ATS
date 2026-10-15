import { useAppFeedback } from './useAppFeedback';
import { Modal } from 'antd';
import type { ModalProps } from 'antd';
import { createContext, useCallback, useContext, useId, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { ApiProblemError } from '../api/problem';
import type { DetailOrigin } from './detailMotion';

type WorkspaceDialogProps = Omit<ModalProps, 'onOk'> & {
  size?: 'compact' | 'form' | 'wide';
  motionFrom?: DetailOrigin | undefined;
  resizeSurface?: boolean;
  onOk?: (event: React.MouseEvent<HTMLButtonElement>) => unknown;
};

const widths = { compact: 560, form: 760, wide: 1120 };

type DialogConfig = ModalProps & { 'aria-busy'?: boolean };
type DialogStep = { id: string; props: DialogConfig; source: HTMLElement | null; resizeSurface: boolean };
type FlowState = { steps: DialogStep[]; retainedStep: DialogStep | undefined; frame: DialogConfig | undefined };
type DialogFlow = { container: HTMLDivElement | null; activeId: string | undefined; update: (id: string, props: DialogConfig, source: HTMLElement | null, resizeSurface: boolean) => void; remove: (id: string) => void };
const DialogFlowContext = createContext<DialogFlow | null>(null);

/** Keep the same physical dialog while nested operations replace its contents. */
function DialogSurface({ config, group = false }: { config: DialogConfig; group?: boolean }) {
  const [{ steps, retainedStep, frame }, setFlowState] = useState<FlowState>({ steps: [], retainedStep: undefined, frame: undefined });
  const [container, setContainer] = useState<HTMLDivElement | null>(null);
  useLayoutEffect(() => { container?.closest<HTMLElement>('.ant-modal-body')?.setAttribute('tabindex', '0'); }, [container]);
  const update = useCallback<DialogFlow['update']>((id, props, source, resizeSurface) => {
    setFlowState(current => {
      const index = current.steps.findIndex(step => step.id === id);
      if (current.steps[index]?.props === props && current.steps[index]?.resizeSurface === resizeSurface) return current;
      const next = index < 0 ? [...current.steps, { id, props, source, resizeSurface }] : current.steps.map(step => step.id === id ? { id, props, source, resizeSurface } : step);
      return { steps: next, retainedStep: next.at(-1), frame: current.frame ?? props };
    });
  }, []);
  const remove = useCallback((id: string) => {
    setFlowState(current => {
      if (!current.steps.some(step => step.id === id)) return current;
      const next = current.steps.filter(step => step.id !== id);
      return { ...current, steps: next, retainedStep: next.at(-1) ?? current.retainedStep };
    });
  }, []);
  const active = steps.at(-1) ?? (group ? retainedStep : undefined);
  const previous = useRef<DialogStep | undefined>(undefined);
  useLayoutEffect(() => {
    const exited = previous.current;
    if (exited?.id === active?.id) return;
    if (exited && exited.id !== active?.id && !steps.some(step => step.id === exited.id)) {
      exited.source?.focus({ preventScroll: true });
    }
    previous.current = active;
    if (active) container?.closest('.ant-modal-body')?.scrollTo({ top: 0 });
  }, [active, container, steps]);
  const flow = useMemo(() => ({ container, activeId: active?.id, update, remove }), [container, active?.id, update, remove]);
  const geometry = active?.resizeSurface ? active.props : group ? frame ?? config : config;
  return <Modal {...config} {...active?.props}
    open={group ? steps.length > 0 : Boolean(config.open)}
    forceRender={group || Boolean(config.forceRender)}
    width={geometry.width ?? widths.compact}
    className={`${geometry.className ?? ''} ${group ? 'workspace-dialog-reader' : ''}`}
    rootClassName={geometry.rootClassName ?? ''}
    style={geometry.style ?? {}}
    afterOpenChange={open => { if (!open) setFlowState(current => current.steps.length ? current : { ...current, retainedStep: undefined, frame: undefined }); config.afterOpenChange?.(open); }}
  ><DialogFlowContext.Provider value={flow}>
    <div hidden={Boolean(active)}>{config.children}</div>
    <div ref={setContainer} />
  </DialogFlowContext.Provider></Modal>;
}

function NestedDialogStep({ flow, config, resizeSurface }: { flow: DialogFlow; config: DialogConfig; resizeSurface: boolean }) {
  const { container, activeId, update, remove } = flow;
  const id = useId();
  const source = useRef<HTMLElement | null>(null);
  const wasOpen = useRef(false);
  const content = useRef<HTMLDivElement>(null);
  useLayoutEffect(() => {
    if (config.open) {
      if (!wasOpen.current) source.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
      update(id, config, source.current, resizeSurface);
    } else remove(id);
    if (Boolean(config.open) !== wasOpen.current && (config.open ? Boolean(container) : activeId !== id)) {
      wasOpen.current = Boolean(config.open);
      config.afterOpenChange?.(Boolean(config.open));
    }
  }, [config, container, activeId, update, remove, id, resizeSurface]);
  useLayoutEffect(() => () => remove(id), [remove, id]);
  useLayoutEffect(() => {
    if (config.open && activeId === id) content.current?.focus({ preventScroll: true });
  }, [config.open, activeId, id]);
  if (!container || (!config.open && config.destroyOnHidden && activeId !== id)) return null;
  return createPortal(<div ref={content} className="workspace-dialog-step-content" tabIndex={-1} hidden={activeId !== id}>{config.children}</div>, container);
}

/** Sibling creation/result dialogs can share one surface through an async handoff. */
export function WorkspaceDialogGroup({ children }: { children: React.ReactNode }) {
  return <DialogSurface group config={{ centered: true, width: widths.wide, className: 'workspace-dialog workspace-dialog-wide workspace-dialog-reader', rootClassName: 'workspace-dialog-root', transitionName: 'workspace-dialog-motion', maskTransitionName: 'workspace-mask-motion', children }} />;
}

/** One focus layer for an operation; field validation and API state remain owned by the caller. */
export function WorkspaceDialog({ size = 'compact', width, className, rootClassName, confirmLoading, onOk, onCancel, footer, motionFrom, resizeSurface = false, ...props }: WorkspaceDialogProps) {
  const flow = useContext(DialogFlowContext);
  const submitting = useRef(false);
  const [validating, setValidating] = useState(false);
  const { message } = useAppFeedback();
  const busy = Boolean(confirmLoading || validating);
  const detailMotion = footer === null && motionFrom;

  const submit = async (event: React.MouseEvent<HTMLButtonElement>) => {
    if (submitting.current || busy) return;
    submitting.current = true;
    setValidating(true);
    const dialog = event.currentTarget.closest('.ant-modal');
    try { await onOk?.(event); }
    catch (error) {
      // Ant Form already displays field validation errors beside the fields.
      if (error && typeof error === 'object' && 'errorFields' in error) {
        requestAnimationFrame(() => setTimeout(() => {
          const currentStep = dialog?.querySelector('.workspace-dialog-step-content:not([hidden])') ?? dialog;
          const invalid = currentStep?.querySelector<HTMLElement>('.ant-form-item-has-error input, .ant-form-item-has-error textarea, .ant-form-item-has-error [role="combobox"]');
          invalid?.focus();
          invalid?.scrollIntoView({ block: 'nearest' });
        }, 0));
      } else {
        message.error(error instanceof ApiProblemError ? error.problem.detail : error instanceof Error ? error.message : '操作未完成，请重试。');
      }
    } finally { submitting.current = false; setValidating(false); }
  };

  const config: DialogConfig = {
    centered: true, width: width ?? widths[size], transitionName: 'workspace-dialog-motion', maskTransitionName: 'workspace-mask-motion', ...props,
    rootClassName: `workspace-dialog-root ${rootClassName ?? ''}`,
    className: `workspace-dialog workspace-dialog-${size} ${footer === null && size !== 'compact' ? 'workspace-dialog-reader' : ''} ${detailMotion ? 'workspace-dialog-detail' : ''} ${className ?? ''}`,
    style: { ...props.style, ...(detailMotion ? { '--detail-x': `${detailMotion.x}px`, '--detail-y': `${detailMotion.y}px`, transformOrigin: 'center' } : {}) },
    footer, confirmLoading: busy, 'aria-busy': busy, keyboard: !busy && props.keyboard !== false,
    mask: busy ? { closable: false } : props.mask ?? { closable: footer === null },
    closable: busy ? { disabled: true } : props.closable ?? true,
    cancelButtonProps: { ...props.cancelButtonProps, disabled: Boolean(busy || props.cancelButtonProps?.disabled) },
    okButtonProps: { ...props.okButtonProps, disabled: Boolean(busy || props.okButtonProps?.disabled) },
    onCancel: event => { if (!busy) onCancel?.(event); }, onOk: event => void submit(event),
  };
  return flow ? <NestedDialogStep flow={flow} config={config} resizeSurface={resizeSurface} /> : <DialogSurface config={config} />;
}
