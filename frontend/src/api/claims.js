import { apiRequest, toQuery } from './client';

export const getMyClaims = (tab, page) => apiRequest(`/claims${toQuery({ tab, page })}`);

export const getActiveCount = () => apiRequest('/claims/active-count');

export const getClaim = (id) => apiRequest(`/claims/${id}`);

// Each move the API allows (ClaimMove in the backend) has its own endpoint. The screens show only the moves
// listed in the claim's allowedActions, so this map is the whole link between a button and its request.
const MOVE_PATHS = {
  MARK_READY: 'ready',
  CONFIRM_HANDOVER: 'picked-up',
  CONFIRM_RECEIPT: 'complete',
  CANCEL: 'cancel',
};

export const moveClaim = (id, move) => apiRequest(`/claims/${id}/${MOVE_PATHS[move]}`, { method: 'POST' });

export const reportReceiver = (id, body) => apiRequest(`/claims/${id}/report`, { method: 'POST', body });
