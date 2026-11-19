import { useSyncExternalStore } from 'react';

/** True while the CSS media query matches, for example useMediaQuery('(max-width: 859px)'). */
export function useMediaQuery(query) {
  return useSyncExternalStore(
    (onChange) => {
      const list = window.matchMedia(query);
      list.addEventListener('change', onChange);
      return () => list.removeEventListener('change', onChange);
    },
    () => window.matchMedia(query).matches,
  );
}

/** The phone layout (below 860 px): a menu sheet, floating buttons, a sticky Claim bar. */
export function useIsPhone() {
  return useMediaQuery('(max-width: 859px)');
}

/** Below 1150 px the top-bar links move into the account menu (UI-UX "Layout and responsive"). */
export function useIsNarrow() {
  return useMediaQuery('(max-width: 1149px)');
}
