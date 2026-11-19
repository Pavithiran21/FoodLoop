import { IconBan, IconCheck, IconX } from '@tabler/icons-react';
import { useCallback, useMemo, useRef, useState } from 'react';
import { ToastContext } from './ToastContext';

const SHOW_FOR_MS = 5000;

/** Toasts at the bottom right: butter cards with a forest square icon; errors pale red with a red icon. */
export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const nextId = useRef(1);

  const dismiss = useCallback((id) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const showToast = useCallback(
    (message, { icon: Icon = IconCheck } = {}) => {
      const id = nextId.current++;
      setToasts((current) => [...current, { id, message, Icon, error: false }]);
      window.setTimeout(() => dismiss(id), SHOW_FOR_MS);
    },
    [dismiss],
  );

  const showError = useCallback((message) => {
    const id = nextId.current++;
    // The same error twice (a double click) shows once.
    setToasts((current) =>
      current.some((toast) => toast.error && toast.message === message)
        ? current
        : [...current, { id, message, Icon: IconBan, error: true }],
    );
  }, []);

  const value = useMemo(() => ({ showToast, showError }), [showToast, showError]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="toasts">
        {toasts.map(({ id, message, Icon, error }) =>
          error ? (
            <div key={id} className="toast err" role="alert">
              <span className="ic">
                <Icon size={17} aria-hidden="true" />
              </span>
              <span>{message}</span>
              <button type="button" className="x" aria-label="Close" onClick={() => dismiss(id)}>
                <IconX size={18} aria-hidden="true" />
              </button>
            </div>
          ) : (
            <div key={id} className="toast" role="status">
              <span className="ic">
                <Icon size={17} aria-hidden="true" />
              </span>
              <span>{message}</span>
            </div>
          ),
        )}
      </div>
    </ToastContext.Provider>
  );
}
