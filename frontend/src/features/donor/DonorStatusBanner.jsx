import { useMutation } from '@tanstack/react-query';
import { IconFileX, IconHourglassHigh, IconPencil } from '@tabler/icons-react';
import { Link } from 'react-router';
import { requestReview } from '../../api/donor';
import { useAuth } from '../../auth/AuthContext';
import { useToast } from '../../toast/ToastContext';

/**
 * The banner on My listings for a donor who can't list yet: "Waiting for verification", or "Your donor account
 * wasn't verified" with the admin's reason, Edit business details and Ask for review again (back to Waiting).
 */
export function DonorStatusBanner() {
  const { user, setUser } = useAuth();
  const { showToast, showError } = useToast();
  const ask = useMutation({
    mutationFn: requestReview,
    onSuccess: (updated) => {
      setUser(updated);
      showToast('Review requested · an admin will check again');
    },
    onError: (error) => showError(error.message),
  });
  const status = user.donor?.verificationStatus;

  if (status === 'WAITING') {
    return (
      <div className="card pad mb-5 border-2 border-butter" role="status">
        <div className="flex gap-3">
          <IconHourglassHigh size={28} color="var(--tang-d)" aria-hidden="true" className="shrink-0" />
          <div>
            <h2 className="text-[19px]">Waiting for verification</h2>
            <p className="muted sm mt-1">
              An admin checks every new donor. You can fill in your profile now; listing opens once you're verified,
              and we'll email you.
            </p>
          </div>
        </div>
      </div>
    );
  }
  if (status === 'NOT_VERIFIED') {
    return (
      <div className="card pad mb-5 border-2 border-err-s" role="alert">
        <div className="flex gap-3">
          <IconFileX size={28} color="var(--err)" aria-hidden="true" className="shrink-0" />
          <div>
            <h2 className="text-[19px]">Your donor account wasn't verified</h2>
            <p className="muted sm mt-1">
              Reason from the admin: "{user.donor.rejectionReason}" Fix your details and ask again; listing stays
              closed until an admin verifies you.
            </p>
            <div className="row-flex mt-3">
              <Link className="btn btn-line btn-sm" to="/profile">
                <IconPencil size={16} aria-hidden="true" /> Edit business details
              </Link>
              <button type="button" className="btn btn-main btn-sm" onClick={() => ask.mutate()} disabled={ask.isPending}>
                {ask.isPending ? 'Asking…' : 'Ask for review again'}
              </button>
            </div>
          </div>
        </div>
      </div>
    );
  }
  return null;
}
