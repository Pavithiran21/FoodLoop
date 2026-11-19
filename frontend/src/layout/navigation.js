import { useQuery } from '@tanstack/react-query';
import { getDonorCounts, getReports } from '../api/admin';
import { getActiveCount } from '../api/claims';
import { keys } from '../api/queryClient';

// The pages each role's top bar links to. `match` decides when a link is the open page (aria-current="page").

const RECEIVER_LINKS = [
  { to: '/home', label: 'Find food', countKey: null, match: (path) => path === '/home' || path.startsWith('/listings') },
  { to: '/claims', label: 'My claims', countKey: 'activeClaims', match: (path) => path.startsWith('/claims') },
];

const DONOR_LINKS = [
  {
    to: '/donor',
    label: 'My listings',
    countKey: null,
    match: (path) => (path.startsWith('/donor') && !path.startsWith('/donor/history')) || path.startsWith('/claims'),
  },
  { to: '/donor/history', label: 'History', countKey: null, match: (path) => path.startsWith('/donor/history') },
];

const ADMIN_LINKS = [
  { to: '/admin/donors', label: 'Verify donors', countKey: 'waitingDonors', match: (path) => path.startsWith('/admin/donors') },
  { to: '/admin/reports', label: 'Reports', countKey: 'openReports', match: (path) => path.startsWith('/admin/reports') },
  { to: '/admin/accounts', label: 'Accounts', countKey: null, match: (path) => path.startsWith('/admin/accounts') },
];

export function linksFor(role) {
  if (role === 'ADMIN') return ADMIN_LINKS;
  if (role === 'DONOR') return DONOR_LINKS;
  return RECEIVER_LINKS;
}

/**
 * The butter count badges: a receiver's active claims; an Admin's waiting donors (/admin/donors/counts) and open
 * reports (/admin/reports?status=OPEN&size=1, whose totalItems is the count).
 */
export function useNavCounts(role) {
  const isReceiver = role === 'RECEIVER';
  const isAdmin = role === 'ADMIN';
  const active = useQuery({ queryKey: keys.activeCount, queryFn: getActiveCount, enabled: isReceiver });
  const donors = useQuery({ queryKey: keys.donorCounts, queryFn: getDonorCounts, enabled: isAdmin });
  const reports = useQuery({ queryKey: keys.openReports, queryFn: () => getReports('OPEN', 1, 1), enabled: isAdmin });
  return {
    activeClaims: active.data?.active ?? 0,
    waitingDonors: donors.data?.waiting ?? 0,
    openReports: reports.data?.totalItems ?? 0,
  };
}
