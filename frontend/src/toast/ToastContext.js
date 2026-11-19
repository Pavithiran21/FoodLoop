import { createContext, useContext } from 'react';

/**
 * { showToast(message, { icon }), showError(message) }
 * A toast is a butter card (role="status") that goes after 5 seconds; an error is pale red (role="alert") and
 * stays until it is closed.
 */
export const ToastContext = createContext(null);

export function useToast() {
  const value = useContext(ToastContext);
  if (!value) {
    throw new Error('useToast must be used inside <ToastProvider>');
  }
  return value;
}
