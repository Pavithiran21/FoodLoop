import { useQuery } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router';
import { getMyClaims } from '../../api/claims';
import { keys } from '../../api/queryClient';
import { FoodPicture } from '../../components/FoodPicture';
import { Pager } from '../../components/Pager';
import { EmptyState, ErrorPanel, Skeleton } from '../../components/StatePanels';
import { StatusTag } from '../../components/StatusTag';
import { Tabs } from '../../components/Tabs';
import { useNavCounts } from '../../layout/navigation';
import { useNow } from '../../hooks/useNow';
import { usePageTitle } from '../../hooks/usePageTitle';
import { pageFrom, useUrlFilters } from '../../hooks/useUrlFilters';
import { formatDay, formatTime, formatWindow } from '../../lib/time';

/** The second line of a row: "FL-2041 · Crumb & Co. · pick up Today 6:00 – 8:00 pm", or how a past claim ended. */
function rowLine(claim, now) {
  const zone = claim.timeZone;
  const start = `${claim.number} · ${claim.donorName}`;
  switch (claim.status) {
    case 'CLAIMED':
      return `${start} · pick up ${formatWindow(claim.pickupFrom, claim.pickupUntil, zone, now)}`;
    case 'READY':
      return `${start} · ready · pick up by ${formatTime(claim.pickupUntil, zone)}`;
    case 'PICKED_UP':
      return `${start} · picked up ${formatTime(claim.updatedAt, zone)}`;
    case 'EXPIRED':
      return `${start} · ${formatDay(claim.updatedAt, zone)} · not collected in time`;
    case 'CANCELLED':
      return `${start} · ${formatDay(claim.updatedAt, zone)} · cancelled`;
    default:
      return `${start} · ${formatDay(claim.updatedAt, zone)}`;
  }
}

/** My claims (/claims, Receiver): Active and Past tabs; a row opens the claim. */
export function MyClaimsPage() {
  usePageTitle('My claims');
  const now = useNow();
  const navigate = useNavigate();
  const [searchParams, update] = useUrlFilters();
  const tab = searchParams.get('tab') === 'PAST' ? 'PAST' : 'ACTIVE';
  const page = pageFrom(searchParams);
  const { activeClaims } = useNavCounts('RECEIVER');
  const claims = useQuery({ queryKey: keys.myClaims(tab, page), queryFn: () => getMyClaims(tab, page) });

  return (
    <div className="card mx-auto max-w-[1000px] overflow-hidden">
      <div className="px-5 pt-5 pb-1">
        <h1 className="mb-3 text-[24px]">My claims</h1>
        <Tabs
          label="Claims"
          value={tab}
          onChange={(value) => update({ tab: value === 'ACTIVE' ? null : value })}
          tabs={[
            { value: 'ACTIVE', label: activeClaims > 0 ? `Active · ${activeClaims}` : 'Active' },
            { value: 'PAST', label: 'Past' },
          ]}
          className="mb-3.5"
        />
      </div>
      {claims.isPending && (
        <div className="px-5 pb-5">
          <Skeleton label="Loading your claims…" blocks={0} lines={3} />
        </div>
      )}
      {claims.isError && <ErrorPanel error={claims.error} title="Couldn't load your claims" onRetry={() => claims.refetch()} />}
      {claims.data && claims.data.totalItems === 0 && (
        <EmptyState
          art={<FoodPicture category="COOKED_MEAL" width={80} />}
          title={tab === 'ACTIVE' ? 'No claims yet' : 'No past claims yet'}
          action={
            <Link className="btn btn-soft" to="/home">
              Find food
            </Link>
          }
        >
          {tab === 'ACTIVE' ? 'Claim food near you and it shows here until you collect it.' : 'Completed, cancelled and expired claims show here.'}
        </EmptyState>
      )}
      {claims.data && claims.data.totalItems > 0 && (
        <>
          <table className="tbl cards">
            <caption className="sr-only">{tab === 'ACTIVE' ? 'Active claims' : 'Past claims'}</caption>
            <tbody>
              {claims.data.items.map((claim) => (
                <tr key={claim.id} className="click" onClick={() => navigate(`/claims/${claim.id}`)}>
                  <td>
                    <Link className="row-link" to={`/claims/${claim.id}`}>
                      <b>{claim.title}</b>
                    </Link>
                    <div className="muted xs">{rowLine(claim, now)}</div>
                  </td>
                  <td className="num">
                    <StatusTag status={claim.status} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pager data={claims.data} onPage={(next) => update({ page: next }, { replace: false })} />
        </>
      )}
    </div>
  );
}
