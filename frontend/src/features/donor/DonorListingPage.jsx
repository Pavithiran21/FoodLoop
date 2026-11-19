import { useMutation, useQuery } from '@tanstack/react-query';
import { IconArrowLeft, IconPencil } from '@tabler/icons-react';
import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { getDonorListing, withdrawListing } from '../../api/donor';
import { keys, queryClient } from '../../api/queryClient';
import { Dialog } from '../../components/Dialog';
import { ListingPhoto } from '../../components/ListingPhoto';
import { Skeleton } from '../../components/StatePanels';
import { StatusTag } from '../../components/StatusTag';
import { useNow } from '../../hooks/useNow';
import { usePageTitle } from '../../hooks/usePageTitle';
import { categoryLabel, dietaryText, statusLabel } from '../../lib/labels';
import { withZone } from '../../lib/listingText';
import { stubColour } from '../../lib/pictures';
import { formatWindow, zoneShortName } from '../../lib/time';
import { NotFoundPage, ServerErrorPanel } from '../../pages/ErrorPages';
import { useToast } from '../../toast/ToastContext';

/** The donor's own listing (/donor/listings/:id): read-only facts and its claim; Edit and Withdraw while unclaimed. */
export function DonorListingPage() {
  const { id } = useParams();
  const now = useNow();
  const navigate = useNavigate();
  const { showToast, showError } = useToast();
  const [withdrawOpen, setWithdrawOpen] = useState(false);
  const listingQuery = useQuery({ queryKey: keys.donorListing(id), queryFn: () => getDonorListing(id) });
  usePageTitle(listingQuery.data?.title ?? 'Listing');

  const withdraw = useMutation({
    mutationFn: () => withdrawListing(id),
    onSuccess: () => {
      setWithdrawOpen(false);
      showToast('Listing withdrawn');
      navigate('/donor');
    },
    onError: (error) => {
      setWithdrawOpen(false);
      showError(error.message);
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['donor-listings'] }),
  });

  if (listingQuery.isPending) {
    return (
      <div className="card pad mx-auto max-w-[760px]">
        <Skeleton label="Loading the listing…" blocks={1} lines={3} columns={1} />
      </div>
    );
  }
  if (listingQuery.isError) {
    return listingQuery.error.status === 404 ? (
      <NotFoundPage />
    ) : (
      <ServerErrorPanel error={listingQuery.error} onRetry={() => listingQuery.refetch()} />
    );
  }

  const listing = listingQuery.data;
  const zone = listing.timeZone;
  const editable = listing.status === 'LISTED';
  const claim = listing.claim;

  return (
    <div className="card pad mx-auto max-w-[760px]">
      <Link to="/donor" className="back">
        <IconArrowLeft size={16} aria-hidden="true" /> My listings
      </Link>
      <div className="mt-3 flex flex-wrap items-start gap-5">
        <div className="thumb h-[120px]! w-[144px]!" style={{ background: stubColour(listing.category) }}>
          <ListingPhoto
            listingId={listing.id}
            hasPhoto={listing.hasPhoto}
            category={listing.category}
            alt={`Photo of ${listing.title}`}
            pictureWidth={104}
          />
        </div>
        <div className="fill">
          <div className="row-flex">
            <h1 className="fill text-[24px]">{listing.title}</h1>
            <StatusTag status={listing.status} />
          </div>
          <dl className="dl">
            <dt>Quantity</dt>
            <dd>{listing.quantity}</dd>
            <dt>Category</dt>
            <dd>{categoryLabel(listing.category)}</dd>
            <dt>Dietary</dt>
            <dd>{dietaryText(listing.dietary)}</dd>
            <dt>Pick up</dt>
            <dd>
              {formatWindow(listing.pickupFrom, listing.pickupUntil, zone, now)} {zoneShortName(zone, listing.pickupFrom)}
            </dd>
            <dt>Best before</dt>
            <dd>{withZone(listing.bestBefore, zone, now)}</dd>
            <dt>Pickup address</dt>
            <dd>{listing.pickupAddress}</dd>
            <dt>Claim</dt>
            <dd>
              {claim ? (
                <Link to={`/claims/${claim.id}`}>
                  {claim.number} · {claim.receiverName} · {statusLabel(claim.status)}
                </Link>
              ) : (
                'None yet'
              )}
            </dd>
            {listing.closedAt && (
              <>
                <dt>Closed</dt>
                <dd>
                  {statusLabel(listing.status)} · {withZone(listing.closedAt, zone, now)}
                </dd>
              </>
            )}
          </dl>
          {editable ? (
            <>
              <div className="row-flex">
                <Link className="btn btn-line btn-sm" to={`/donor/listings/${listing.id}/edit`}>
                  <IconPencil size={16} aria-hidden="true" /> Edit
                </Link>
                <button type="button" className="btn btn-text btn-sm btn-text-err" onClick={() => setWithdrawOpen(true)}>
                  Withdraw
                </button>
              </div>
              <p className="muted xs mt-2.5">Edit and Withdraw are only possible while nobody has claimed it.</p>
            </>
          ) : (
            claim &&
            ['CLAIMED', 'READY'].includes(claim.status) && (
              <p className="muted xs">
                A listing can be changed only while nobody has claimed it. You can cancel the claim from{' '}
                <Link to={`/claims/${claim.id}`}>the claim</Link> instead.
              </p>
            )
          )}
        </div>
      </div>

      <Dialog open={withdrawOpen} onClose={() => setWithdrawOpen(false)} labelledBy="withdraw-title" describedBy="withdraw-text">
        <h2 id="withdraw-title">Withdraw "{listing.title}"?</h2>
        <p id="withdraw-text" className="muted sm mt-1.5">
          It's no longer shown to receivers. Nobody has claimed it yet.
        </p>
        <div className="acts">
          <button type="button" className="btn btn-line" onClick={() => setWithdrawOpen(false)} data-autofocus>
            Keep listed
          </button>
          <button type="button" className="btn btn-danger" onClick={() => withdraw.mutate()} disabled={withdraw.isPending}>
            {withdraw.isPending ? 'Withdrawing…' : 'Withdraw'}
          </button>
        </div>
      </Dialog>
    </div>
  );
}
