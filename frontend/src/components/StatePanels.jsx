import { IconCloudOff, IconPlugConnectedX } from '@tabler/icons-react';
import { LogoMark } from './Logo';

/** While the session is being restored on a page load. */
export function LoadingScreen() {
  return (
    <div className="grid min-h-[60vh] place-items-center" aria-busy="true">
      <div className="row-flex muted">
        <LogoMark size={36} />
        <span>Loading FoodLoop…</span>
      </div>
    </div>
  );
}

/** Skeleton lines and blocks while a list loads ("Finding food near you…"). */
export function Skeleton({ label = 'Loading…', blocks = 2, lines = 1, columns = 2 }) {
  return (
    <div aria-busy="true" role="status">
      <b className="sm">{label}</b>
      <div className="mt-2 grid gap-3" style={{ gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }}>
        {Array.from({ length: blocks }, (_, index) => (
          <div key={index} className="skel blk" />
        ))}
      </div>
      {Array.from({ length: lines }, (_, index) => (
        <div key={index} className="skel" style={{ width: `${60 - index * 15}%` }} />
      ))}
    </div>
  );
}

/** An empty list: a picture or icon, a title, one line and an optional action. */
export function EmptyState({ art, title, children, action }) {
  return (
    <div className="state">
      {art}
      <h3>{title}</h3>
      {children && <p className="muted sm">{children}</p>}
      {action}
    </div>
  );
}

/**
 * A list or page that couldn't load: "Couldn't load …" with Try again. A 5xx shows the request id, so a support
 * person can find the log line.
 */
export function ErrorPanel({ error, title = "Couldn't load this", onRetry }) {
  const offline = error?.status === 0;
  const Icon = offline ? IconCloudOff : IconPlugConnectedX;
  return (
    <div className="state" role="alert">
      <Icon size={36} className="mx-auto" color="var(--muted)" aria-hidden="true" />
      <h3>{title}</h3>
      <p className="muted sm">
        {offline ? 'Check your connection and try again.' : error?.message || "It's not you. Try again in a moment."}
        {error?.requestId && error.status >= 500 && <span className="block xs">Request ID: {error.requestId}</span>}
      </p>
      {onRetry && (
        <button type="button" className="btn btn-line" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  );
}
