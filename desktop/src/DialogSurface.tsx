import { useEffect, useRef, type ReactNode } from 'react';
import { flushSync } from 'react-dom';

export function DialogSurface({
  title,
  className,
  onClose,
  dismissible = true,
  dismissOnBackdrop = false,
  children,
}: {
  title: string;
  className: string;
  onClose: () => void;
  dismissible?: boolean;
  dismissOnBackdrop?: boolean;
  children: ReactNode;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const closing = useRef(false);
  const pendingDismiss = useRef<AbortController | null>(null);
  useEffect(() => {
    const element = dialog.current;
    if (!element) return;
    if (element.showModal) element.showModal();
    else element.open = true;
    element.querySelector<HTMLElement>('[data-dialog-autofocus]')?.focus({ preventScroll: true });
    return () => {
      pendingDismiss.current?.abort();
      if (element.close) element.close();
      else element.open = false;
    };
  }, []);

  function dismiss() {
    const element = dialog.current;
    if (!element || !dismissible || closing.current) return;
    closing.current = true;
    // Closing the native dialog immediately releases its focus trap. CSS keeps it
    // in the top layer for the visual exit while the parent still owns its state.
    if (element.close) element.close();
    else element.open = false;
    const exit = element.getAnimations?.() ?? [];
    if (!exit.length) {
      onClose();
      return;
    }
    element.inert = true;
    const controller = new AbortController();
    pendingDismiss.current = controller;
    function finish(interrupted: boolean) {
      if (controller.signal.aborted) return;
      controller.abort();
      pendingDismiss.current = null;
      if (dialog.current !== element) return;
      if (interrupted) flushSync(onClose);
      else onClose();
    }
    // Let the next action run against the new state, even if it reopens this dialog.
    const interrupt = () => finish(true);
    const capture = { capture: true, signal: controller.signal };
    document.addEventListener('pointerdown', interrupt, capture);
    document.addEventListener('keydown', interrupt, capture);
    document.addEventListener('click', interrupt, capture);
    void Promise.allSettled(exit.map((animation) => animation.finished)).then(() => finish(false));
  }

  return (
    <dialog
      ref={dialog}
      className={`motion-dialog ${className}`}
      aria-label={title}
      onCancel={(event) => {
        event.preventDefault();
        dismiss();
      }}
      onClick={(event) => {
        const target = event.target instanceof Element ? event.target : null;
        const dismissButton = target?.closest('[data-dialog-dismiss]');
        if (dismissButton?.closest('dialog') === event.currentTarget) {
          dismiss();
        } else if (dismissOnBackdrop && event.target === event.currentTarget) {
          const { left, right, top, bottom } = event.currentTarget.getBoundingClientRect();
          if (
            event.clientX < left ||
            event.clientX > right ||
            event.clientY < top ||
            event.clientY > bottom
          )
            dismiss();
        }
      }}
    >
      {children}
    </dialog>
  );
}
