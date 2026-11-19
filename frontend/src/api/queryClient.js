import { QueryClient } from '@tanstack/react-query';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      // 4xx answers are final (a 404 stays a 404); only a network failure or a 5xx is retried once.
      retry: (failureCount, error) => failureCount < 1 && (error?.status === 0 || error?.status >= 500),
    },
  },
});

// One place for the cache keys, so a change invalidates exactly what it made stale.
export const keys = {
  demo: ['demo'],
  findFood: (params) => ['find-food', params],
  listing: (id, place) => ['listing', String(id), place],
  listingPhoto: (id, version) => ['listing-photo', String(id), version],
  myClaims: (tab, page) => ['claims', tab, page],
  activeCount: ['claims', 'active-count'],
  claim: (id) => ['claim', String(id)],
  board: (state) => ['donor-listings', 'board', state],
  history: (outcome, page) => ['donor-listings', 'history', outcome, page],
  donorListing: (id) => ['donor-listings', 'one', String(id)],
  adminDonors: (status, page) => ['admin', 'donors', status, page],
  donorCounts: ['admin', 'donor-counts'],
  openReports: ['admin', 'open-reports'],
  adminReports: (status, page) => ['admin', 'reports', status, page],
  adminReport: (id) => ['admin', 'report', String(id)],
  accounts: (params) => ['admin', 'accounts', params],
};

/** After a claim moves (claim, ready, handover, receipt, cancel), every view of listings and claims is stale. */
export function invalidateClaimViews() {
  ['find-food', 'listing', 'claims', 'claim', 'donor-listings'].forEach((root) =>
    queryClient.invalidateQueries({ queryKey: [root] }),
  );
}

/** After any Admin decision, the queues and the nav counts are stale. */
export function invalidateAdminViews() {
  queryClient.invalidateQueries({ queryKey: ['admin'] });
}
