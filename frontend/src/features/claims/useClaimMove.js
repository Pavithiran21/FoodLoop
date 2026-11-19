import { useMutation } from '@tanstack/react-query';
import { moveClaim } from '../../api/claims';
import { invalidateClaimViews } from '../../api/queryClient';
import { useToast } from '../../toast/ToastContext';

/**
 * Makes one claim move (MARK_READY, CONFIRM_HANDOVER, CONFIRM_RECEIPT or CANCEL). On success the toast confirms it;
 * on a 409 or 422 (the other side moved first, or the move isn't allowed any more) the backend's message shows as
 * an error toast. Either way every list and claim view is refreshed, so the buttons match the new state.
 */
export function useClaimMove({ onDone } = {}) {
  const { showToast, showError } = useToast();
  return useMutation({
    mutationFn: ({ claimId, move }) => moveClaim(claimId, move),
    onSuccess: (claim, { toast }) => {
      if (toast) {
        showToast(typeof toast === 'function' ? toast(claim) : toast);
      }
      onDone?.(claim);
    },
    onError: (error) => showError(error.message),
    onSettled: () => invalidateClaimViews(),
  });
}
