import { useCallback, useEffect, useMemo, useState } from 'react';
import { logout } from '../api/auth';
import { refreshSession, setAccessToken, setSessionEndedHandler } from '../api/client';
import { queryClient } from '../api/queryClient';
import { AuthContext } from './AuthContext';

/**
 * Holds who is logged in. The access token stays in memory in api/client.js; on a page load the HttpOnly refresh
 * cookie gets a new one (POST /auth/refresh), so a reload keeps the person logged in.
 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  // True after a 401 that a refresh couldn't fix. Log in shows "Your session has ended" until the next log-in
  // clears it (clearing it on the redirect would let a guard redirect again without the notice).
  const [sessionEnded, setSessionEnded] = useState(false);

  useEffect(() => {
    let active = true;
    refreshSession().then((session) => {
      if (active) {
        setUser(session?.user ?? null);
        setIsLoading(false);
      }
    });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    setSessionEndedHandler(() => {
      setAccessToken(null);
      queryClient.clear();
      window.sessionStorage.removeItem('fl-search-place');
      setUser(null);
      setSessionEnded(true);
    });
  }, []);

  const signIn = useCallback((session) => {
    queryClient.clear();
    setAccessToken(session.accessToken);
    setSessionEnded(false);
    setUser(session.user);
  }, []);

  const signOut = useCallback(async () => {
    try {
      await logout();
    } catch {
      // The server clears the cookie when it answers; if it can't be reached we still log out here.
    }
    setAccessToken(null);
    queryClient.clear();
    window.sessionStorage.removeItem('fl-search-place'); // the receiver's place for this visit
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({ user, isLoading, sessionEnded, signIn, signOut, setUser }),
    [user, isLoading, sessionEnded, signIn, signOut],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
