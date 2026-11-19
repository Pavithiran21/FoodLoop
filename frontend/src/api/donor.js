import { apiRequest, toQuery } from './client';

export const updateDonorProfile = (body) => apiRequest('/donor/profile', { method: 'PATCH', body });

export const requestReview = () => apiRequest('/donor/profile/review-request', { method: 'POST' });

/** One board column: LISTED, CLAIMED, READY, PICKED_UP or DONE_TODAY (100 per column is plenty for one day). */
export const getBoardColumn = (state) => apiRequest(`/donor/listings${toQuery({ state, size: 100 })}`);

export const getHistory = (outcome, page) => apiRequest(`/donor/listings/history${toQuery({ outcome, page })}`);

export const getDonorListing = (id) => apiRequest(`/donor/listings/${id}`);

export const createListing = (body) => apiRequest('/donor/listings', { method: 'POST', body });

export const updateListing = (id, body) => apiRequest(`/donor/listings/${id}`, { method: 'PUT', body });

export const withdrawListing = (id) => apiRequest(`/donor/listings/${id}/withdraw`, { method: 'POST' });

export function uploadPhoto(id, file) {
  const form = new FormData();
  form.append('photo', file);
  return apiRequest(`/donor/listings/${id}/photo`, { method: 'PUT', form });
}

export const removePhoto = (id) => apiRequest(`/donor/listings/${id}/photo`, { method: 'DELETE' });
