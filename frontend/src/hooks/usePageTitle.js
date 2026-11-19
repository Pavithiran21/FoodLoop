import { useEffect } from 'react';

/** "My claims · FoodLoop" in the browser tab. */
export function usePageTitle(title) {
  useEffect(() => {
    document.title = title ? `${title} · FoodLoop` : 'FoodLoop';
  }, [title]);
}
