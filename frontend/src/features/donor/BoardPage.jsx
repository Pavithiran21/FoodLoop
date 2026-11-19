import { useQueries } from '@tanstack/react-query';
import { IconPackage, IconPlus, IconWalk } from '@tabler/icons-react';
import { Link } from 'react-router';
import { getBoardColumn } from '../../api/donor';
import { keys } from '../../api/queryClient';
import { isVerifiedDonor, useAuth } from '../../auth/AuthContext';
import { FoodPicture } from '../../components/FoodPicture';
import { ErrorPanel } from '../../components/StatePanels';
import { StatusTag } from '../../components/StatusTag';
import { useIsPhone } from '../../hooks/useMediaQuery';
import { useNow } from '../../hooks/useNow';
import { usePageTitle } from '../../hooks/usePageTitle';
import { initials } from '../../lib/labels';
import { formatTime, formatWindow } from '../../lib/time';
import { useClaimMove } from '../claims/useClaimMove';
import { MOVE_BUTTONS } from '../claims/claimSteps';
import { DonorStatusBanner } from './DonorStatusBanner';

const COLUMNS = ['LISTED', 'CLAIMED', 'READY', 'PICKED_UP', 'DONE_TODAY'];
// On a phone the board is one list, the claims that need the donor first.
const PHONE_ORDER = ['CLAIMED', 'READY', 'LISTED', 'PICKED_UP', 'DONE_TODAY'];

const EMPTY_TEXT = {
  LISTED: 'Nothing listed right now',
  CLAIMED: 'No new claims',
  READY: 'Nothing packed and waiting',
  PICKED_UP: 'Nothing waiting for the receiver to confirm',
  DONE_TODAY: 'Nothing finished today yet',
};

/** One card's detail line, by column. */
function cardLine(listing, now) {
  const zone = listing.timeZone;
  if (listing.status === 'LISTED') {
    return `${listing.quantity} · pick up ${formatWindow(listing.pickupFrom, listing.pickupUntil, zone, now).replace(/^Today /, '')}`;
  }
  if (listing.status === 'PICKED_UP') {
    return `${listing.quantity} · picked up; completes when the receiver confirms, or by itself after 24 hours`;
  }
  if (['COMPLETED', 'EXPIRED', 'WITHDRAWN'].includes(listing.status) && listing.closedAt) {
    return `${listing.quantity} · closed ${formatTime(listing.closedAt, zone)}`;
  }
  return `${listing.quantity} · pick up by ${formatTime(listing.pickupUntil, zone)}`;
}

/** A board card. It opens the listing's read-only view; the claim's one allowed next step is the only button. */
function BoardCard({ listing, now, onMove, moving }) {
  const claim = listing.claim;
  const nextMove = claim?.allowedActions?.find((action) => action !== 'CANCEL');
  const needsDonor = Boolean(nextMove);
  const link = claim && !['LISTED', 'WITHDRAWN'].includes(listing.status) ? `/claims/${claim.id}` : `/donor/listings/${listing.id}`;
  const doneTag =
    listing.status === 'EXPIRED' && !claim
      ? 'Expired · not claimed'
      : listing.status === 'EXPIRED'
        ? 'Expired · not collected'
        : null;

  if (!needsDonor) {
    return (
      <Link className="mini" to={link}>
        <b>{listing.title}</b>
        <div className="muted xs">{cardLine(listing, now)}</div>
        {claim && listing.status === 'PICKED_UP' && <div className="muted xs mt-1">{claim.receiverName} · {claim.number}</div>}
        {['COMPLETED', 'EXPIRED', 'WITHDRAWN'].includes(listing.status) && (
          <div className="mt-1.5">
            <StatusTag status={listing.status}>{doneTag}</StatusTag>
          </div>
        )}
      </Link>
    );
  }
  return (
    <div className="mini needs">
      <Link className="mini-link" to={link}>
        <b>{listing.title}</b>
      </Link>
      <div className="who">
        <span className={`av ${listing.status === 'READY' ? 'av-ready' : 'av-tang'}`} aria-hidden="true">
          {initials(claim.receiverName)}
        </span>
        <span className="min-w-0 truncate">
          {claim.receiverName} · {claim.number}
        </span>
      </div>
      <div className="muted xs mb-2">{cardLine(listing, now)}</div>
      <button
        type="button"
        className="btn btn-main btn-sm btn-block"
        onClick={() => onMove(claim, nextMove)}
        disabled={moving}
      >
        {nextMove === 'MARK_READY' ? <IconPackage size={16} aria-hidden="true" /> : <IconWalk size={16} aria-hidden="true" />}{' '}
        {MOVE_BUTTONS[nextMove].label}
        <span className="sr-only">: {listing.title}</span>
      </button>
    </div>
  );
}

/** My listings (/donor): one column per state, ending in "Done today"; older items are in History. */
export function BoardPage() {
  usePageTitle('My listings');
  const { user } = useAuth();
  const now = useNow();
  const phone = useIsPhone();
  const verified = isVerifiedDonor(user);
  const columns = useQueries({
    queries: COLUMNS.map((state) => ({ queryKey: keys.board(state), queryFn: () => getBoardColumn(state) })),
  });
  const move = useClaimMove();
  const byState = Object.fromEntries(COLUMNS.map((state, index) => [state, columns[index]]));
  const needYou = (byState.CLAIMED.data?.totalItems ?? 0) + (byState.READY.data?.totalItems ?? 0);
  const failed = columns.find((column) => column.isError);
  const loaded = columns.every((column) => column.data);
  const empty = loaded && columns.every((column) => column.data.totalItems === 0);

  function onMove(claim, action) {
    move.mutate({ claimId: claim.id, move: action, toast: () => MOVE_BUTTONS[action].toast({ receiverName: claim.receiverName }) });
  }

  return (
    <>
      <DonorStatusBanner />
      <div className="row-flex mb-4">
        <div className="fill">
          <h1 className="text-[26px]">{user.donor?.businessName}</h1>
          <div className="muted sm">
            {needYou > 0 ? `${needYou} ${needYou === 1 ? 'claim needs' : 'claims need'} you now.` : 'Nothing needs you right now.'}
          </div>
        </div>
        {!phone &&
          (verified ? (
            <Link className="btn btn-tang" to="/donor/listings/new">
              <IconPlus size={18} aria-hidden="true" /> List surplus food
            </Link>
          ) : (
            <button type="button" className="btn btn-tang" disabled title="Listing opens once an admin verifies you">
              <IconPlus size={18} aria-hidden="true" /> List surplus food
            </button>
          ))}
      </div>

      {failed && (
        <div className="card mb-4">
          <ErrorPanel error={failed.error} title="Couldn't load your listings" onRetry={() => columns.forEach((column) => column.refetch())} />
        </div>
      )}
      {empty && !failed ? (
        <div className="card state">
          <FoodPicture category="BAKERY" width={90} />
          <h2 className="text-[19px]">No listings yet</h2>
          <p className="muted sm">{verified ? "List what's left at closing time and nearby receivers can claim it." : 'Listing opens once an admin verifies you.'}</p>
          {verified && (
            <Link className="btn btn-tang" to="/donor/listings/new">
              <IconPlus size={18} aria-hidden="true" /> List surplus food
            </Link>
          )}
        </div>
      ) : (
        <div className="board card bg-panel! p-5 max-[859px]:p-4">
          {(phone ? PHONE_ORDER : COLUMNS).map((state) => {
            const column = byState[state];
            const items = column.data?.items ?? [];
            if (phone && column.data && items.length === 0) {
              return null;
            }
            return (
              <section key={state} className="col" aria-label={state === 'DONE_TODAY' ? 'Done today' : undefined}>
                <h2 className="col-head">
                  <StatusTag status={state} />
                  <em>{column.data?.totalItems ?? '…'}</em>
                </h2>
                {column.isPending && <div className="skel blk" aria-busy="true" />}
                {items.map((listing) => (
                  <BoardCard key={listing.id} listing={listing} now={now} onMove={onMove} moving={move.isPending} />
                ))}
                {column.data && items.length === 0 && <div className="col-empty">{EMPTY_TEXT[state]}</div>}
              </section>
            );
          })}
        </div>
      )}
    </>
  );
}
