import { useQuery } from '@tanstack/react-query';
import {
  IconArrowLeft,
  IconClockX,
  IconDirections,
  IconFlag,
  IconHandGrab,
  IconHeartHandshake,
  IconListCheck,
  IconPackage,
  IconWalk,
  IconX,
} from '@tabler/icons-react';
import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { getClaim, reportReceiver } from '../../api/claims';
import { reportListing } from '../../api/listings';
import { keys, queryClient } from '../../api/queryClient';
import { FoodPicture } from '../../components/FoodPicture';
import { ReportDialog } from '../../components/ReportDialog';
import { Skeleton } from '../../components/StatePanels';
import { StatusTag } from '../../components/StatusTag';
import { useNow } from '../../hooks/useNow';
import { usePageTitle } from '../../hooks/usePageTitle';
import { LISTING_REPORT_REASONS, RECEIVER_REPORT_REASONS } from '../../lib/labels';
import { withZone } from '../../lib/listingText';
import { formatTime, formatWindow } from '../../lib/time';
import { NotFoundPage, ServerErrorPanel } from '../../pages/ErrorPages';
import { useToast } from '../../toast/ToastContext';
import { CancelClaimDialog } from './CancelClaimDialog';
import { MOVE_BUTTONS, claimSteps } from './claimSteps';
import { useClaimMove } from './useClaimMove';

function StepIcon({ stepKey }) {
  const props = { size: 20, 'aria-hidden': 'true' };
  switch (stepKey) {
    case 'LISTED':
      return <IconListCheck {...props} />;
    case 'CLAIMED':
      return <IconHandGrab {...props} />;
    case 'READY':
      return <IconPackage {...props} />;
    case 'PICKED_UP':
      return <IconWalk {...props} />;
    case 'CANCELLED':
      return <IconX {...props} />;
    case 'EXPIRED':
      return <IconClockX {...props} />;
    default:
      return <IconHeartHandshake {...props} />;
  }
}

function MoveIcon({ move }) {
  if (move === 'MARK_READY') return <IconPackage size={17} aria-hidden="true" />;
  if (move === 'CONFIRM_HANDOVER') return <IconWalk size={17} aria-hidden="true" />;
  return <IconHeartHandshake size={17} aria-hidden="true" />;
}

/** "OpenStreetMap directions to the pick-up point" (walking, from wherever the receiver is). */
function directionsUrl(pickup) {
  return `https://www.openstreetmap.org/directions?engine=fossgis_osrm_foot&route=%3B${pickup.lat}%2C${pickup.lng}`;
}

/** The forest side panel: the claim number to show at pick-up, the place and the best-before reminder. */
function SidePanel({ claim, now }) {
  const zone = claim.listing.timeZone;
  const asReceiver = claim.viewerRole === 'RECEIVER';
  let headline;
  if (claim.status === 'PICKED_UP' || (claim.status === 'COMPLETED' && claim.pickedUpAt)) {
    headline = `Picked up at ${formatTime(claim.pickedUpAt, zone)}`;
  } else if (claim.status === 'CANCELLED') {
    headline = 'This claim was cancelled';
  } else if (claim.status === 'EXPIRED') {
    headline = 'This claim expired';
  } else {
    headline = `Pick up ${formatWindow(claim.listing.pickupFrom, claim.listing.pickupUntil, zone, now)}`;
  }
  const open = ['CLAIMED', 'READY'].includes(claim.status);
  return (
    <aside className="side" aria-label="Pick-up details">
      <div className="kicker">{asReceiver ? 'SHOW THIS AT PICKUP' : 'CLAIM NUMBER'}</div>
      <div className="code">{claim.number}</div>
      <h3 className="mt-3.5 text-[19px]">{headline}</h3>
      <p className="sm mt-1 mb-3.5">
        {asReceiver ? claim.donorName : `For ${claim.receiverName}`}
        <br />
        {claim.pickup.address}
      </p>
      {asReceiver && open && (
        <a className="btn btn-tang btn-block" href={directionsUrl(claim.pickup)} target="_blank" rel="noopener noreferrer">
          <IconDirections size={18} aria-hidden="true" /> Directions (OpenStreetMap)
        </a>
      )}
      <div className="note-box">
        Best before <b>{withZone(claim.listing.bestBefore, zone, now, { capital: false })}</b>. Food safety is the donor's responsibility.
      </div>
    </aside>
  );
}

/**
 * The claim tracker (/claims/:id) for its receiver and its listing's donor: the journey stepper, the buttons the
 * API allows right now (allowedActions, never worked out here), cancel and report. Anyone else gets the 404 page.
 */
export function ClaimPage() {
  const { id } = useParams();
  const now = useNow();
  const { showToast } = useToast();
  const [cancelOpen, setCancelOpen] = useState(false);
  const [reportOpen, setReportOpen] = useState(false);
  const claimQuery = useQuery({ queryKey: keys.claim(id), queryFn: () => getClaim(id) });
  const move = useClaimMove({ onDone: () => setCancelOpen(false) });
  usePageTitle(claimQuery.data ? `Claim ${claimQuery.data.number}` : 'Claim');

  if (claimQuery.isPending) {
    return (
      <div className="card pad">
        <Skeleton label="Loading the claim…" blocks={1} lines={3} columns={1} />
      </div>
    );
  }
  if (claimQuery.isError) {
    return claimQuery.error.status === 404 ? (
      <NotFoundPage />
    ) : (
      <ServerErrorPanel error={claimQuery.error} onRetry={() => claimQuery.refetch()} />
    );
  }

  const claim = claimQuery.data;
  const asDonor = claim.viewerRole === 'DONOR';
  const steps = claimSteps(claim, now);
  const stepMoves = claim.allowedActions.filter((action) => action !== 'CANCEL');
  const canCancel = claim.allowedActions.includes('CANCEL');

  function makeMove(action) {
    move.mutate({ claimId: claim.id, move: action, toast: MOVE_BUTTONS[action].toast });
  }

  return (
    <>
      <Link to={asDonor ? '/donor' : '/claims'} className="back mb-3">
        <IconArrowLeft size={16} aria-hidden="true" /> {asDonor ? 'My listings' : 'My claims'}
      </Link>
      <div className="card tracker">
        <div className="steps">
          <div className="row-flex mb-5">
            <FoodPicture category={claim.listing.category} width={56} />
            <div className="fill">
              <h1 className="text-[22px]">{claim.listing.title}</h1>
              <div className="muted sm">
                Claim {claim.number} · {asDonor ? claim.receiverName : claim.donorName}
              </div>
            </div>
            <StatusTag status={claim.status} />
          </div>
          <ol className="step-list" aria-label="Claim progress">
            {steps.map((step) => {
              const current = step.state === 'now' || step.state === 'ended' || step.state === 'lapsed';
              return (
                <li key={step.key} className={`step ${step.state}`} aria-current={current ? 'step' : undefined}>
                  <span className="dot">
                    <StepIcon stepKey={step.key} />
                  </span>
                  <div className="min-w-0">
                    <h3>{step.title}</h3>
                    <div className="muted sm">{step.text}</div>
                    {step.state === 'now' && stepMoves.length > 0 && (
                      <div className="row-flex mt-2.5">
                        {stepMoves.map((action) => (
                          <button
                            key={action}
                            type="button"
                            className={`btn btn-sm ${MOVE_BUTTONS[action].className}`}
                            onClick={() => makeMove(action)}
                            disabled={move.isPending}
                          >
                            <MoveIcon move={action} /> {MOVE_BUTTONS[action].label}
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                  <span className="muted xs text-right">{step.time ?? ''}</span>
                </li>
              );
            })}
          </ol>

          <div className="row-flex mt-1 gap-1!">
            {canCancel && (
              <button type="button" className="btn btn-text btn-sm btn-text-err" onClick={() => setCancelOpen(true)}>
                {asDonor ? 'Cancel this claim' : 'Cancel claim'}
              </button>
            )}
            {claim.reported ? (
              <span className="tag s-expired">
                <IconFlag size={14} aria-hidden="true" /> Reported
              </span>
            ) : (
              <button type="button" className="btn btn-text btn-sm" onClick={() => setReportOpen(true)}>
                <IconFlag size={16} aria-hidden="true" /> {asDonor ? 'Report receiver' : 'Report a problem with this listing'}
              </button>
            )}
          </div>
          {canCancel && (
            <p className="muted xs mt-1">
              A claim can be cancelled until it's picked up; {asDonor ? claim.receiverName : claim.donorName} is emailed.
            </p>
          )}
        </div>
        <SidePanel claim={claim} now={now} />
      </div>

      <CancelClaimDialog
        claim={claim}
        open={cancelOpen}
        now={now}
        busy={move.isPending}
        onClose={() => setCancelOpen(false)}
        onConfirm={() => move.mutate({ claimId: claim.id, move: 'CANCEL', toast: 'Claim cancelled' })}
      />
      <ReportDialog
        open={reportOpen}
        onClose={() => setReportOpen(false)}
        title={asDonor ? `Report ${claim.receiverName}` : 'Report this listing'}
        subtitle={
          asDonor
            ? `About claim ${claim.number} · ${claim.listing.title} (${claim.listing.quantity})`
            : 'The donor doesn\'t see who reported. An admin can dismiss the report or suspend the account.'
        }
        legend={asDonor ? 'What happened?' : "What's wrong?"}
        reasons={asDonor ? RECEIVER_REPORT_REASONS : LISTING_REPORT_REASONS}
        send={(body) => (asDonor ? reportReceiver(claim.id, body) : reportListing(claim.listing.id, body))}
        onSent={({ already, message }) => {
          setReportOpen(false);
          showToast(already ? message : 'Report sent. Thank you.', { icon: IconFlag });
          queryClient.invalidateQueries({ queryKey: keys.claim(id) });
          queryClient.invalidateQueries({ queryKey: ['listing'] });
        }}
      />
    </>
  );
}
