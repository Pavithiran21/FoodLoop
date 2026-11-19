import { apiBlob, apiRequest, toQuery } from './client';

/** Find food: params are lat, lng, radiusKm, category[], dietary[], when, timeZone, sort, page, size. */
export const findFood = (params) => apiRequest(`/listings${toQuery(params)}`);

/** { listing, status, myClaimId, reported }; lat and lng (optional) give the distance. */
export const getListing = (id, place) =>
  apiRequest(`/listings/${id}${toQuery({ lat: place?.lat, lng: place?.lng })}`);

export const claimListing = (id) => apiRequest(`/listings/${id}/claim`, { method: 'POST' });

export const reportListing = (id, body) => apiRequest(`/listings/${id}/report`, { method: 'POST', body });

export const getListingPhoto = (id) => apiBlob(`/listings/${id}/photo`);
