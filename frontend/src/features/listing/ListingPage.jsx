import { useMutation, useQuery } from '@tanstack/react-query';
import {
  IconAlertTriangle,
  IconArrowLeft,
  IconBuildingStore,
  IconFlag,
  IconHandGrab,
  IconInfoCircle,
  IconPhoto,
  IconReceipt,
  IconRosetteDiscountCheck,
} from '@tabler/icons-react';
import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { getClaim } from '../../api/claims';
import { claimListing, getListing, reportListing } from '../../api/listings';
import { invalidateClaimViews, keys, queryClient } from '../../api/queryClient';
import { ListingPhoto } from '../../components/ListingPhoto';
import { ReportDialog } from '../../components/ReportDialog';
import { Skeleton } from '../../components/StatePanels';
import { StatusTag } from '../../components/StatusTag';
import { useIsPhone } from '../../hooks/useMediaQuery';
import { useNow } from '../../hooks/useNow';
import { usePageTitle } from '../../hooks/usePageTitle';
import { LISTING_REPORT_REASONS, dietaryText } from '../../lib/labels';
import { bestBeforeText, distanceText, windowText } from '../../lib/listingText';
import { pictureName, stubColour } from '../../lib/pictures';
import { NotFoundPage, ServerErrorPanel } from '../../pages/ErrorPages';
import { useSearchPlace } from '../../place/SearchPlaceContext';
import { useToast } from '../../toast/ToastContext';

/** The food-safety note, on every listing (PROJECTS.md: food safety is the donor's responsibility). */
export function SafetyNote() {
  return (
    <div className="alert a-tang my-3.5" role="note">
      <IconInfoCircle size={20} aria-hidden="true" />
      <div>
        <b>Food safety is the donor's responsibility.</b> Check the food when you collect it and eat it before the
        best-before time. FoodLoop doesn't inspect food.
      </div>
    </div>
  );
}

/** "View your claim FL-2041": the receiver's own claim on this listing replaces Claim. */
function YourClaimLink({ claimId }) {
  const claim = useQuery({ queryKey: keys.claim(claimId), queryFn: () => getClaim(claimId) });
  return (
    <Link className="btn btn-tang fill" to={`/claims/${claimId}`}>
      <IconReceipt size={18} aria-hidden="true" /> View your claim{claim.data ? ` ${claim.data.number}` : ''}
    </Link>
  );
}

/** Why a listing that isn't Listed can't be claimed (and isn't this receiver's). */
function ClosedPanel({ status }) {
  const text =
    status === 'WITHDRAWN'
      ? 'The donor withdrew this listing'
      : status === 'EXPIRED'
        ? 'This listing has expired'
        : 'Someone else claimed this listing';
  return (
    <div className="card pad edge-expired mt-1 shadow-none! bg-panel!">
      <b>{text}</b>
      <div className="muted sm">
        {status === 'EXPIRED' ? 'It was closed automatically when its pick-up window ended. ' : "It isn't available any more. "}
        <Link to="/home">Find other food near you</Link>
      </div>
    </div>
  );
}

/** Listing detail (/listings/:id, Receiver): the facts, the safety note, Claim and Report. */
export function ListingPage() {
  const { id } = useParams();
  const now = useNow();
  const phone = useIsPhone();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const { place } = useSearchPlace();
  const [lost, setLost] = useState(null);
  const [reportOpen, setReportOpen] = useState(false);
  const placeKey = place ? `${place.lat},${place.lng}` : 'none';

  const detail = useQuery({ queryKey: keys.listing(id, placeKey), queryFn: () => getListing(id, place) });
  usePageTitle(detail.data?.listing.title ?? 'Listing');

  const claim = useMutation({
    mutationFn: () => claimListing(id),
    onSuccess: (created) => {
      invalidateClaimViews();
      showToast(`Claimed! Claim number ${created.number}`);
      navigate(`/claims/${created.id}`);
    },
    onError: (error) => {
      // 409: another receiver won the race; 422: the window ended or the donor withdrew it.
      setLost(error);
      queryClient.invalidateQueries({ queryKey: ['listing', String(id)] });
    },
  });

  if (detail.isPending) {
    return (
      <div className="card pad">
        <Skeleton label="Loading the listing…" blocks={1} lines={2} columns={1} />
      </div>
    );
  }
  if (detail.isError) {
    return detail.error.status === 404 ? (
      <NotFoundPage />
    ) : (
      <ServerErrorPanel error={detail.error} onRetry={() => detail.refetch()} />
    );
  }

  const { listing, status, myClaimId, reported } = detail.data;
  // A listing back on the list can be claimed again: this receiver's earlier claim on it (myClaimId) was cancelled.
  const canClaim = status === 'LISTED' && lost?.code !== 'CLAIM_LOST';
  const ownActiveClaim = status !== 'LISTED' && myClaimId;
  const claimButton = canClaim && (
    <button type="button" className="btn btn-tang fill" onClick={() => claim.mutate()} disabled={claim.isPending}>
      <IconHandGrab size={18} aria-hidden="true" /> {claim.isPending ? 'Claiming…' : 'Claim this food'}
    </button>
  );
  const reportControl = reported ? (
    <span className="tag s-expired">
      <IconFlag size={14} aria-hidden="true" /> Reported
    </span>
  ) : (
    <button type="button" className="btn btn-line" onClick={() => setReportOpen(true)} aria-label={phone ? 'Report' : undefined}>
      <IconFlag size={18} aria-hidden="true" />
      {phone ? '' : ' Report'}
    </button>
  );

  return (
    <>
      <article className="card detail">
        <div className="hero-art" style={{ background: stubColour(listing.category) }}>
          <ListingPhoto
            listingId={listing.id}
            hasPhoto={listing.hasPhoto}
            category={listing.category}
            alt={`Photo of ${listing.title}`}
            pictureWidth={300}
          />
          {!listing.hasPhoto && (
            <span className="tag s-white photo-note">
              <IconPhoto size={14} aria-hidden="true" /> No photo · {pictureName(listing.category)} picture
            </span>
          )}
        </div>
        <div className="pad sm:p-[30px]!">
          <Link to="/home" className="back">
            <IconArrowLeft size={16} aria-hidden="true" /> Find food
          </Link>
          <div className="row-flex mt-2.5 gap-2!">
            <StatusTag status={status} />
            {listing.donor.verified && (
              <span className="tag s-listed">
                <IconRosetteDiscountCheck size={14} aria-hidden="true" /> Verified donor
              </span>
            )}
          </div>
          <h1 className="mt-2.5 mb-0.5 text-[32px] leading-tight max-[859px]:text-[24px]">{listing.title}</h1>
          <div className="muted flex flex-wrap items-center gap-1">
            <IconBuildingStore size={16} aria-hidden="true" /> {listing.donor.businessName} · {listing.area}
            {listing.distanceKm != null && ` · ${distanceText(listing.distanceKm)}`}
          </div>
          <div className="facts">
            <div>
              <small>Quantity</small>
              <b>{listing.quantity}</b>
            </div>
            <div>
              <small>Dietary</small>
              <b>{dietaryText(listing.dietary)}</b>
            </div>
            <div>
              <small>Pick up</small>
              <b>{windowText(listing, now)}</b>
            </div>
            <div>
              <small>Best before</small>
              <b>{bestBeforeText(listing, now, { capital: true })}</b>
            </div>
          </div>
          <SafetyNote />

          {lost && (
            <div className="alert a-err mb-3" role="alert">
              <IconAlertTriangle size={20} aria-hidden="true" />
              <div>
                {lost.code === 'CLAIM_LOST' ? (
                  <>
                    <b>Someone claimed this a moment ago.</b> Only one receiver can claim a listing.{' '}
                    <Link to="/home">See more food near you</Link>
                  </>
                ) : (
                  lost.message
                )}
              </div>
            </div>
          )}

          {ownActiveClaim ? (
            <div className="row-flex">
              <YourClaimLink claimId={myClaimId} />
              {reportControl}
            </div>
          ) : status === 'LISTED' ? (
            !phone && (
              <div className="row-flex">
                {claimButton || <span className="tag s-claimed">Claimed</span>}
                {reportControl}
              </div>
            )
          ) : (
            <>
              <ClosedPanel status={status} />
              <div className="row-flex mt-3">{reportControl}</div>
            </>
          )}
          {canClaim && (
            <p className="muted xs mt-2.5">
              You'll see the full pickup address once it's yours. You can cancel any time before pick-up.
            </p>
          )}
        </div>
      </article>

      {phone && status === 'LISTED' && (
        <div className="pbar -mx-4 mt-4">
          {reportControl}
          {claimButton || <span className="tag s-claimed">Claimed</span>}
        </div>
      )}

      <ReportDialog
        open={reportOpen}
        onClose={() => setReportOpen(false)}
        title="Report this listing"
        subtitle="The donor doesn't see who reported. An admin can dismiss the report or suspend the account."
        legend="What's wrong?"
        reasons={LISTING_REPORT_REASONS}
        send={(body) => reportListing(listing.id, body)}
        onSent={({ already, message }) => {
          setReportOpen(false);
          showToast(already ? message : 'Report sent. Thank you.', { icon: IconFlag });
          queryClient.invalidateQueries({ queryKey: ['listing', String(id)] });
          queryClient.invalidateQueries({ queryKey: ['claim'] });
        }}
      />
    </>
  );
}
