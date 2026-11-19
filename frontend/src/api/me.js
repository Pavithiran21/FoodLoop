import { apiRequest } from './client';

export const getMe = () => apiRequest('/me');

/** name, and for a receiver homeLabel, homeLat, homeLng (all three, or all null to clear the home). */
export const updateMe = (body) => apiRequest('/me', { method: 'PATCH', body });

/** { accessToken }: the other sessions are logged out, this one gets a new token. */
export const changePassword = (currentPassword, newPassword) =>
  apiRequest('/me/password', { method: 'POST', body: { currentPassword, newPassword } });

export const deleteAccount = (password) => apiRequest('/me', { method: 'DELETE', body: { password } });
