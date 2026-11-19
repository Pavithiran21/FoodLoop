import { Navigate, Outlet, useLocation } from 'react-router';
import { LoadingScreen } from '../components/StatePanels';
import { ForbiddenPage } from '../pages/ErrorPages';
import { homePathFor, isVerifiedDonor, useAuth } from './AuthContext';

/**
 * Pages for one or more roles. Without a login you go to Log in (and back here afterwards); with the wrong role
 * you see the 403 page for that area. This only decides what is shown: the server refuses every request from the
 * wrong role with 403 anyway.
 */
export function RequireRole({ roles }) {
  const { user, isLoading } = useAuth();
  const location = useLocation();
  if (isLoading) {
    return <LoadingScreen />;
  }
  if (!user) {
    return <Navigate to="/" replace state={{ from: location }} />;
  }
  if (!roles.includes(user.role)) {
    return <ForbiddenPage area={roles.includes('ADMIN') ? 'admin' : roles.includes('DONOR') ? 'donor' : 'receiver'} />;
  }
  return <Outlet />;
}

/** New listing: only a verified donor (a waiting or not-verified donor sees why). */
export function RequireVerifiedDonor() {
  const { user } = useAuth();
  if (!isVerifiedDonor(user)) {
    return <ForbiddenPage area="donor" />;
  }
  return <Outlet />;
}

/** Log in, Sign up, /demo and Forgot password. Signed in, you go where you were going, or to your home page. */
export function GuestOnly() {
  const { user, isLoading } = useAuth();
  const location = useLocation();
  if (isLoading) {
    return <LoadingScreen />;
  }
  if (user) {
    const from = location.state?.from;
    const target = from ? from.pathname + from.search : homePathFor(user);
    return <Navigate to={target} replace />;
  }
  return <Outlet />;
}
