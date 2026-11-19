import { apiRequest, toQuery } from './client';

/** Up to 5 { label, area, lat, lng } from OpenStreetMap Nominatim, through the backend. */
export const searchPlaces = (q) => apiRequest(`/places/search${toQuery({ q })}`);
