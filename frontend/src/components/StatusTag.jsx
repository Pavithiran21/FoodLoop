import { statusClass, statusLabel } from '../lib/labels';

/** A listing or claim state as a coloured tag with its word (colour is never the only signal). */
export function StatusTag({ status, children }) {
  return <span className={`tag ${statusClass(status)}`}>{children ?? statusLabel(status)}</span>;
}
