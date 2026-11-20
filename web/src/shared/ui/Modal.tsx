import { useEffect, useId, useRef, type ReactNode } from 'react';
import { Button } from './Button';
import './table-modal.css';

export type ModalProps = {
  open: boolean;
  title: string;
  description?: string;
  children?: ReactNode;
  actions: ReactNode;
  onClose: () => void;
};

/** Controlled native dialog: the browser provides focus containment and background inertness. */
export function Modal({ open, title, description, children, actions, onClose }: ModalProps) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const descriptionId = useId();
  useEffect(() => {
    const dialog = ref.current;
    if (!open || !dialog) return;
    const previousFocus = document.activeElement;
    const overflow = document.body.style.overflow;
    dialog.showModal();
    document.body.style.overflow = 'hidden';
    return () => {
      dialog.close();
      document.body.style.overflow = overflow;
      if (previousFocus instanceof HTMLElement && previousFocus.isConnected) previousFocus.focus();
    };
  }, [open]);
  return (
    <dialog
      ref={ref}
      className="ui-modal"
      aria-labelledby={titleId}
      aria-describedby={description ? descriptionId : undefined}
      onKeyDown={(event) => {
        if (event.key !== 'Tab') return;
        const focusable = [
          ...event.currentTarget.querySelectorAll<HTMLElement>(
            'button:not(:disabled), a[href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])',
          ),
        ].filter((element) => element.getClientRects().length > 0);
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        const active = document.activeElement;
        if (!first || !last) {
          event.preventDefault();
          return;
        }
        if (event.shiftKey && (active === first || !focusable.includes(active as HTMLElement))) {
          event.preventDefault();
          last.focus();
        } else if (!event.shiftKey && active === last) {
          event.preventDefault();
          first.focus();
        }
      }}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
    >
      <h2 id={titleId} tabIndex={-1} autoFocus>
        {title}
      </h2>
      {description && <p id={descriptionId}>{description}</p>}
      {children}
      <div className="ui-modal__actions">{actions}</div>
    </dialog>
  );
}

export function ConfirmModal({
  onConfirm,
  confirmLabel = '작성 취소',
  cancelLabel = '계속 작성',
  ...props
}: Omit<ModalProps, 'actions' | 'children'> & {
  onConfirm: () => void;
  confirmLabel?: string;
  cancelLabel?: string;
}) {
  return (
    <Modal
      {...props}
      actions={
        <>
          <Button className="ui-modal__discard" onClick={onConfirm}>
            {confirmLabel}
          </Button>
          <Button className="ui-modal__continue" onClick={props.onClose}>
            {cancelLabel}
          </Button>
        </>
      }
    />
  );
}
