import { Dialog } from '../../components/Dialog';

/**
 * "Cancel your claim?" (receiver) or "Cancel Meera's claim?" (donor). The safe button is focused first. The text
 * says what happens to the listing: back on the list while the pick-up window is open, closed after it has ended.
 */
export function CancelClaimDialog({ claim, open, onClose, onConfirm, busy, now }) {
  const asDonor = claim.viewerRole === 'DONOR';
  const windowOpen = new Date(claim.listing.pickupUntil).getTime() > now;
  const firstName = claim.receiverName.split(' ')[0];
  const title = claim.listing.title;

  let text;
  if (asDonor) {
    text = windowOpen
      ? `${claim.receiverName} is emailed. The pick-up window is still open, so ${title} goes back on the list. Once the window has ended, cancelling closes the listing instead.`
      : `${claim.receiverName} is emailed. The pick-up window has ended, so cancelling closes the listing.`;
  } else {
    text = windowOpen
      ? `${title} goes back on the list so someone else can claim it, because the pick-up window hasn't ended. ${claim.donorName} is emailed.`
      : `The pick-up window has ended, so cancelling closes the listing. ${claim.donorName} is emailed.`;
  }

  return (
    <Dialog open={open} onClose={onClose} labelledBy="cancel-title" describedBy="cancel-text">
      <h2 id="cancel-title">{asDonor ? `Cancel ${firstName}'s claim?` : 'Cancel your claim?'}</h2>
      <p id="cancel-text" className="muted sm mt-1.5">
        {text}
      </p>
      <div className="acts">
        <button type="button" className="btn btn-line" onClick={onClose} data-autofocus>
          {asDonor ? 'Keep the claim' : 'Keep my claim'}
        </button>
        <button type="button" className="btn btn-danger" onClick={onConfirm} disabled={busy}>
          {busy ? 'Cancelling…' : 'Cancel claim'}
        </button>
      </div>
    </Dialog>
  );
}
