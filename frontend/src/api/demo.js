import { apiRequest } from './client';

/** { roles: [{ role, email }] } in demo mode; a 404 when DEMO_MODE is off. */
export const getDemo = () => apiRequest('/demo', { auth: false });

export const demoLogin = (role) => apiRequest('/demo/login', { method: 'POST', body: { role }, auth: false });
