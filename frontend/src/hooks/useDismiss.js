import { useEffect } from 'react';

/** While `open`, a click outside `ref` or the Esc key calls onDismiss (the account menu, the phone sheet). */
export function useDismiss(ref, open, onDismiss) {
  useEffect(() => {
    if (!open) {
      return undefined;
    }
    const onPointer = (event) => {
      if (ref.current && !ref.current.contains(event.target)) {
        onDismiss();
      }
    };
    const onKey = (event) => {
      if (event.key === 'Escape') {
        onDismiss();
      }
    };
    document.addEventListener('mousedown', onPointer);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onPointer);
      document.removeEventListener('keydown', onKey);
    };
  }, [ref, open, onDismiss]);
}
