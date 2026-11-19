import { useEffect, useState } from 'react';

/**
 * The current time, refreshed every `everyMs` (a minute by default), so "ends in 1 h 40 m" and "today" stay right
 * on a page left open. Render code reads this instead of calling Date.now(), which React expects to be pure.
 */
export function useNow(everyMs = 60_000) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), everyMs);
    return () => window.clearInterval(timer);
  }, [everyMs]);
  return now;
}
