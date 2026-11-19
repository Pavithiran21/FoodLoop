import { useEffect, useRef } from 'react';

/**
 * A FoodLoop dialog: a native <dialog> opened with showModal(), so the browser traps focus, makes the page behind
 * inert and closes it on Esc. It is only in the page while open. The safe button ("Keep my claim") gets
 * data-autofocus and is focused first; without one, the browser focuses the first control.
 * Title and text ids come from useDialogIds().
 */
export function Dialog({ open, onClose, labelledBy, describedBy, children, wide = false }) {
  if (!open) {
    return null;
  }
  return (
    <OpenDialog onClose={onClose} labelledBy={labelledBy} describedBy={describedBy} wide={wide}>
      {children}
    </OpenDialog>
  );
}

function OpenDialog({ onClose, labelledBy, describedBy, children, wide }) {
  const ref = useRef(null);
  const onCloseRef = useRef(onClose);

  useEffect(() => {
    onCloseRef.current = onClose;
  }, [onClose]);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog.open) {
      dialog.showModal();
    }
    const first = dialog.querySelector('[data-autofocus]');
    if (first) {
      first.focus();
    }
    // Esc: the browser fires "cancel"; we close through React state instead, so the parent decides.
    const handleCancel = (event) => {
      event.preventDefault();
      onCloseRef.current();
    };
    dialog.addEventListener('cancel', handleCancel);
    return () => dialog.removeEventListener('cancel', handleCancel);
  }, []);

  return (
    <dialog
      ref={ref}
      className="dlg"
      aria-labelledby={labelledBy}
      aria-describedby={describedBy}
      style={wide ? { maxWidth: 560 } : undefined}
    >
      {children}
    </dialog>
  );
}
