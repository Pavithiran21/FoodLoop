import { IconLock, IconPlugConnectedX } from '@tabler/icons-react';
import { Link } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { FoodPicture } from '../components/FoodPicture';
import { usePageTitle } from '../hooks/usePageTitle';

function homeLink(user) {
  if (!user) {
    return { to: '/', label: 'Go to Log in' };
  }
  if (user.role === 'DONOR') {
    return { to: '/donor', label: 'Back to My listings' };
  }
  if (user.role === 'ADMIN') {
    return { to: '/admin/donors', label: 'Back to Verify donors' };
  }
  return { to: '/home', label: 'Back to Find food' };
}

const FORBIDDEN_TEXT = {
  donor: {
    title: 'Only verified donors can list food',
    receiver: "Receivers claim food; they can't create listings.",
    ADMIN: "Admins verify donors and handle reports; they don't list food.",
    DONOR: "An admin checks every new donor first. You'll get an email once you're verified.",
  },
  receiver: {
    title: 'Only receivers can claim food',
    DONOR: "Donors list food; they can't search for or claim it.",
    ADMIN: "Admins verify donors and handle reports; they don't claim food.",
  },
  admin: {
    title: 'This page is for admins',
    DONOR: 'Verifying donors, reports and accounts are admin tools.',
    RECEIVER: 'Verifying donors, reports and accounts are admin tools.',
  },
};

/** 403: the page is for another role (the server refuses the requests too). Shown inside the app shell. */
export function ForbiddenPage({ area }) {
  const { user } = useAuth();
  const text = FORBIDDEN_TEXT[area];
  usePageTitle(text.title);
  const back = homeLink(user);
  const reason = user?.role === 'RECEIVER' && area === 'donor' ? text.receiver : text[user?.role];
  return (
    <div className="card state mx-auto max-w-[560px]">
      <IconLock size={36} className="mx-auto" color="var(--tang-d)" aria-hidden="true" />
      <h1 className="mt-1.5 text-[22px]">{text.title}</h1>
      {reason && <p className="muted sm">{reason}</p>}
      <Link className="btn btn-soft" to={back.to}>
        {back.label}
      </Link>
    </div>
  );
}

/** 404, also for someone else's claim or listing (the API answers 404 so ids can't be probed). */
export function NotFoundPage() {
  const { user } = useAuth();
  usePageTitle('Page not found');
  const back = homeLink(user);
  return (
    <div className="card state mx-auto max-w-[560px]">
      <FoodPicture category="FRUIT" width={74} />
      <h1 className="mt-1.5 text-[22px]">We can't find that page</h1>
      <p className="muted sm">The listing may have been withdrawn or has expired.</p>
      <Link className="btn btn-soft" to={back.to}>
        {user?.role === 'RECEIVER' ? 'Go to Find food' : back.label}
      </Link>
    </div>
  );
}

/** A 5xx or a failed network call on a whole page: Try again, and the request id for support. */
export function ServerErrorPanel({ error, onRetry }) {
  return (
    <div className="card state mx-auto max-w-[560px]" role="alert">
      <IconPlugConnectedX size={36} className="mx-auto" color="var(--muted)" aria-hidden="true" />
      <h1 className="mt-1.5 text-[22px]">Something went wrong</h1>
      <p className="muted sm">
        {error?.status === 0
          ? 'Check your connection and try again.'
          : "It's not you. Try again in a moment. If you were logged out, log in again."}
        {error?.requestId && <span className="xs block">Request ID: {error.requestId}</span>}
      </p>
      {onRetry && (
        <button type="button" className="btn btn-line" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  );
}

