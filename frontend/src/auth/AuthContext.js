import { createContext, useContext } from 'react';

/**
 * { user, isLoading, sessionEnded, signIn(session), signOut(), setUser(user) }
 * user is the API's user: { id, name, email, role, demo, home, donor } or null. donor carries the verification
 * status that decides whether the donor can list food.
 */
export const AuthContext = createContext(null);

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error('useAuth must be used inside <AuthProvider>');
  }
  return value;
}

/** Where each role lands after logging in (UI-UX screen 1). */
export function homePathFor(user) {
  if (user?.role === 'ADMIN') {
    return '/admin/donors';
  }
  if (user?.role === 'DONOR') {
    return '/donor';
  }
  return '/home';
}

export const isVerifiedDonor = (user) => user?.role === 'DONOR' && user.donor?.verificationStatus === 'VERIFIED';
