import { useQuery } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router';
import { getHistory } from '../../api/donor';
import { keys } from '../../api/queryClient';
import { FoodPicture } from '../../components/FoodPicture';
import { Pager } from '../../components/Pager';
import { EmptyState, ErrorPanel, Skeleton } from '../../components/StatePanels';
import { StatusTag } from '../../components/StatusTag';
import { usePageTitle } from '../../hooks/usePageTitle';
import { pageFrom, useUrlFilters } from '../../hooks/useUrlFilters';
import { formatDay } from '../../lib/time';

const OUTCOMES = [
  { value: '', label: 'All outcomes' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'EXPIRED', label: 'Expired' },
  { value: 'WITHDRAWN', label: 'Withdrawn' },
];

/** History (/donor/history): closed listings older than today, with the receiver and the outcome. */
export function HistoryPage() {
  usePageTitle('History');
  const navigate = useNavigate();
  const [searchParams, update] = useUrlFilters();
  const outcome = OUTCOMES.some((entry) => entry.value === searchParams.get('outcome')) ? searchParams.get('outcome') : '';
  const page = pageFrom(searchParams);
  const history = useQuery({
    queryKey: keys.history(outcome, page),
    queryFn: () => getHistory(outcome || undefined, page),
  });

  return (
    <div className="card overflow-hidden">
      <div className="row-flex px-5 py-4">
        <h1 className="fill text-[22px]">History</h1>
        <label htmlFor="history-outcome" className="sr-only">
          Outcome
        </label>
        <select
          id="history-outcome"
          className="inp inp-sm"
          value={outcome}
          onChange={(event) => update({ outcome: event.target.value })}
        >
          {OUTCOMES.map((entry) => (
            <option key={entry.value || 'all'} value={entry.value}>
              {entry.label}
            </option>
          ))}
        </select>
      </div>
      {history.isPending && (
        <div className="px-5 pb-5">
          <Skeleton label="Loading your history…" blocks={0} lines={3} />
        </div>
      )}
      {history.isError && <ErrorPanel error={history.error} title="Couldn't load your history" onRetry={() => history.refetch()} />}
      {history.data && history.data.totalItems === 0 && (
        <EmptyState art={<FoodPicture category="BAKERY" width={80} />} title="No closed listings yet">
          Listings move here the day after they are completed, expire or are withdrawn.
        </EmptyState>
      )}
      {history.data && history.data.totalItems > 0 && (
        <>
          <div className="tablewrap">
            <table className="tbl cards">
              <thead>
                <tr>
                  <th scope="col">Listing</th>
                  <th scope="col">Date</th>
                  <th scope="col">Receiver</th>
                  <th scope="col">Outcome</th>
                </tr>
              </thead>
              <tbody>
                {history.data.items.map((listing) => (
                  <tr key={listing.id} className="click" onClick={() => navigate(`/donor/listings/${listing.id}`)}>
                    <td>
                      <Link className="row-link" to={`/donor/listings/${listing.id}`}>
                        <b>{listing.title}</b>
                      </Link>
                      <div className="muted xs">{listing.quantity}</div>
                    </td>
                    <td data-l="Date">{formatDay(listing.closedAt ?? listing.pickupUntil, listing.timeZone)}</td>
                    <td data-l="Receiver">{listing.claim?.receiverName ?? '—'}</td>
                    <td>
                      <StatusTag status={listing.status} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pager data={history.data} showSize onPage={(next) => update({ page: next }, { replace: false })} />
        </>
      )}
    </div>
  );
}
