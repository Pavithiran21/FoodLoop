import { IconPlus, IconReceipt } from '@tabler/icons-react';
import { Suspense } from 'react';
import { Link, Outlet, useLocation } from 'react-router';
import { isVerifiedDonor, useAuth } from '../auth/AuthContext';
import { LocationPicker } from '../features/find/LocationPicker';
import { LoadingScreen } from '../components/StatePanels';
import { useIsPhone } from '../hooks/useMediaQuery';
import { SearchPlaceProvider } from '../place/SearchPlaceProvider';
import { useNavCounts } from './navigation';
import { TopBar } from './TopBar';

/** The receiver's floating "My claims" button on phones (not on the claim pages or a listing with its Claim bar). */
function ReceiverFab() {
  const { pathname } = useLocation();
  const { activeClaims } = useNavCounts('RECEIVER');
  if (pathname.startsWith('/claims') || pathname.startsWith('/listings')) {
    return null;
  }
  return (
    <Link className="fab" to="/claims">
      <IconReceipt size={20} aria-hidden="true" /> My claims{activeClaims > 0 ? ` · ${activeClaims}` : ''}
    </Link>
  );
}

/** The donor's floating "List food" button on phones, on the board and History, once verified. */
function DonorFab({ user }) {
  const { pathname } = useLocation();
  if (!isVerifiedDonor(user) || !(pathname === '/donor' || pathname === '/donor/history')) {
    return null;
  }
  return (
    <Link className="fab" to="/donor/listings/new">
      <IconPlus size={20} aria-hidden="true" /> List food
    </Link>
  );
}

function Shell({ user }) {
  const phone = useIsPhone();
  return (
    <>
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:top-2 focus:left-2 focus:z-50 focus:rounded-xl focus:bg-white focus:p-3">
        Skip to content
      </a>
      <TopBar user={user} />
      <main id="main" className="page">
        <Suspense fallback={<LoadingScreen />}>
          <Outlet />
        </Suspense>
      </main>
      {phone && user.role === 'RECEIVER' && <ReceiverFab />}
      {phone && user.role === 'DONOR' && <DonorFab user={user} />}
      {user.role === 'RECEIVER' && <LocationPicker />}
    </>
  );
}

/**
 * Every signed-in page: the role's top bar above the page. A receiver's pages also get the search place (where
 * Find food looks) and the location picker; it is keyed by the user, so the next person to log in on this tab
 * starts from their own home.
 */
export function AppLayout() {
  const { user } = useAuth();
  if (user.role === 'RECEIVER') {
    return (
      <SearchPlaceProvider key={user.id}>
        <Shell user={user} />
      </SearchPlaceProvider>
    );
  }
  return <Shell user={user} />;
}
