import { apiRequest, toQuery } from './client';

export const getDonorCards = (status, page) => apiRequest(`/admin/donors${toQuery({ status, page })}`);

export const getDonorCounts = () => apiRequest('/admin/donors/counts');

export const verifyDonor = (id) => apiRequest(`/admin/donors/${id}/verify`, { method: 'POST' });

export const rejectDonor = (id, reason) =>
  apiRequest(`/admin/donors/${id}/reject`, { method: 'POST', body: { reason } });

export const getReports = (status, page, size) => apiRequest(`/admin/reports${toQuery({ status, page, size })}`);

export const getReport = (id) => apiRequest(`/admin/reports/${id}`);

export const dismissReport = (id) => apiRequest(`/admin/reports/${id}/dismiss`, { method: 'POST' });

export const suspendFromReport = (id, reason) =>
  apiRequest(`/admin/reports/${id}/suspend`, { method: 'POST', body: { reason } });

/** params: q, role, status, page */
export const getAccounts = (params) => apiRequest(`/admin/accounts${toQuery(params)}`);

export const suspendAccount = (id, reason) =>
  apiRequest(`/admin/accounts/${id}/suspend`, { method: 'POST', body: { reason } });

export const reinstateAccount = (id) => apiRequest(`/admin/accounts/${id}/reinstate`, { method: 'POST' });
