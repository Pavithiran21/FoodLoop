import { apiRequest } from './client';

export const login = (email, password) =>
  apiRequest('/auth/login', { method: 'POST', body: { email, password }, auth: false });

export const register = (body) => apiRequest('/auth/register', { method: 'POST', body, auth: false });

export const logout = () => apiRequest('/auth/logout', { method: 'POST', auth: false });

export const forgotPassword = (email) =>
  apiRequest('/auth/forgot-password', { method: 'POST', body: { email }, auth: false });

export const resetPassword = (token, password) =>
  apiRequest('/auth/reset-password', { method: 'POST', body: { token, password }, auth: false });
