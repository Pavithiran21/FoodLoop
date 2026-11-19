import { formatTime, formatWhen } from '../../lib/time';

// The claim tracker's steps, worked out from one ClaimResponse. The order is the state machine's:
// Listed -> Claimed -> Ready -> Picked up -> Completed. A cancelled or expired claim stops at the last step it
// reached and ends with a step that says who ended it and when.

const ORDER = ['LISTED', 'CLAIMED', 'READY', 'PICKED_UP', 'COMPLETED'];

/** The other side's name, as the viewer sees it. */
function otherSide(claim) {
  return claim.viewerRole === 'DONOR' ? claim.receiverName : claim.donorName;
}

function whoCancelled(claim) {
  const by = claim.cancelledBy;
  if (!by) return 'someone';
  if (by.you) return 'you';
  if (by.role === 'ADMIN') return 'an admin';
  return by.role === 'DONOR' ? claim.donorName : claim.receiverName;
}

/** The text and time of each step, for the receiver or the donor looking at it. */
function stepContent(claim, key, now) {
  const zone = claim.listing.timeZone;
  const asReceiver = claim.viewerRole === 'RECEIVER';
  const at = (instant) => (instant ? formatWhen(instant, zone, now) : null);
  switch (key) {
    case 'LISTED':
      return {
        title: 'Listed',
        text: `${claim.donorName} listed ${claim.listing.quantity}`,
        time: at(claim.listing.createdAt),
      };
    case 'CLAIMED':
      return {
        title: asReceiver ? 'Claimed by you' : `Claimed by ${claim.receiverName}`,
        text: asReceiver ? `${claim.donorName} was emailed` : 'You were emailed',
        time: at(claim.claimedAt),
      };
    case 'READY':
      return {
        title: 'Ready for pick-up',
        text: claim.readyAt
          ? asReceiver
            ? `${claim.donorName} packed it`
            : `You marked it as packed; ${claim.receiverName} was emailed`
          : asReceiver
            ? `${claim.donorName} packs it, and you're emailed`
            : `Mark it as packed when it's ready; ${claim.receiverName} is emailed`,
        time: at(claim.readyAt),
      };
    case 'PICKED_UP':
      return {
        title: 'Picked up',
        text: claim.pickedUpAt
          ? asReceiver
            ? `${claim.donorName} confirmed the handover.${claim.status === 'PICKED_UP' ? ' Did you get everything?' : ''}`
            : `You confirmed the handover to ${claim.receiverName}`
          : asReceiver
            ? `Show the claim number at pick-up by ${formatTime(claim.listing.pickupUntil, zone)}`
            : 'Confirm the handover when they show the claim number',
        time: at(claim.pickedUpAt),
      };
    default: {
      // COMPLETED
      let text;
      if (claim.completedAt) {
        text = claim.autoCompleted
          ? 'Completed by itself 24 hours after the handover'
          : asReceiver
            ? 'You confirmed you got it'
            : `${claim.receiverName} confirmed they got it`;
      } else if (claim.pickedUpAt) {
        const auto = formatWhen(new Date(new Date(claim.pickedUpAt).getTime() + 24 * 3_600_000), zone, now);
        text = asReceiver
          ? `When you confirm, or by itself at ${auto.charAt(0).toLowerCase()}${auto.slice(1)}`
          : `When ${claim.receiverName} confirms, or by itself at ${auto.charAt(0).toLowerCase()}${auto.slice(1)}`;
      } else {
        text = asReceiver ? 'When you confirm you got it' : `When ${claim.receiverName} confirms they got it`;
      }
      return { title: 'Completed', text, time: at(claim.completedAt) };
    }
  }
}

/** The step a claim in this status is on (its index in ORDER). */
function currentIndex(claim) {
  if (claim.status === 'CANCELLED' || claim.status === 'EXPIRED') {
    if (claim.pickedUpAt) return 3;
    if (claim.readyAt) return 2;
    return 1;
  }
  return ORDER.indexOf(claim.status);
}

/**
 * [{ key, title, text, time, state }] where state is 'done', 'now' (the step the claim is on and its buttons go
 * in), 'todo', 'ended' (cancelled) or 'lapsed' (expired).
 */
export function claimSteps(claim, now) {
  const reached = currentIndex(claim);
  const finished = claim.status === 'CANCELLED' || claim.status === 'EXPIRED';

  const steps = ORDER.slice(0, finished ? reached + 1 : ORDER.length).map((key, index) => {
    let state = 'todo';
    if (index < reached || claim.status === 'COMPLETED' || finished) {
      state = 'done';
    } else if (index === reached) {
      state = 'now';
    }
    return { key, ...stepContent(claim, key, now), state };
  });

  if (claim.status === 'CANCELLED') {
    const backOnList = claim.listing.status === 'LISTED';
    steps.push({
      key: 'CANCELLED',
      title: `Cancelled by ${whoCancelled(claim)}`,
      text: `${backOnList ? 'The food went back on the list' : 'The pick-up window had ended, so the listing closed'}, and ${
        claim.cancelledBy?.you ? `${otherSide(claim)} was emailed` : 'you were emailed'
      }.`,
      time: claim.cancelledAt ? formatWhen(claim.cancelledAt, claim.listing.timeZone, now) : null,
      state: 'ended',
    });
  }
  if (claim.status === 'EXPIRED') {
    steps.push({
      key: 'EXPIRED',
      title: 'Expired',
      text: `Not collected by ${formatTime(claim.listing.pickupUntil, claim.listing.timeZone)}, when the pick-up window ended. Closed automatically; both sides were emailed.`,
      time: claim.expiredAt ? formatWhen(claim.expiredAt, claim.listing.timeZone, now) : null,
      state: 'lapsed',
    });
  }
  return steps;
}

/** Each move's button: its words, its style and the toast after it worked. */
export const MOVE_BUTTONS = {
  MARK_READY: { label: 'Mark as packed', className: 'btn-main', toast: (claim) => `Marked as packed · ${claim.receiverName} was emailed` },
  CONFIRM_HANDOVER: { label: 'Confirm handover', className: 'btn-main', toast: () => 'Handover confirmed · picked up' },
  CONFIRM_RECEIPT: { label: 'I got it', className: 'btn-tang', toast: () => 'Thanks! Claim completed.' },
};
