import { useCallback, useMemo, useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { SearchPlaceContext } from './SearchPlaceContext';

export const SEARCH_PLACE_KEY = 'fl-search-place';

function readStored() {
  try {
    const stored = JSON.parse(window.sessionStorage.getItem(SEARCH_PLACE_KEY));
    return stored && Number.isFinite(stored.lat) && Number.isFinite(stored.lng) ? stored : null;
  } catch {
    return null;
  }
}

/**
 * The receiver's search place: a place picked in the location picker (kept for this visit), else the saved home
 * from the user's profile, else nothing (Find food then asks "Choose where to look for food").
 */
export function SearchPlaceProvider({ children }) {
  const { user } = useAuth();
  const [chosen, setChosen] = useState(readStored);
  // The location picker dialog: the top bar's location chip and Find food's "Choose a location" both open it.
  const [pickerOpen, setPickerOpen] = useState(false);
  const savedHome = user?.home ?? null;

  const choosePlace = useCallback((place) => {
    const next = { label: place.label, lat: place.lat, lng: place.lng, fromHome: false };
    window.sessionStorage.setItem(SEARCH_PLACE_KEY, JSON.stringify(next));
    setChosen(next);
  }, []);

  const backToHome = useCallback(() => {
    window.sessionStorage.removeItem(SEARCH_PLACE_KEY);
    setChosen(null);
  }, []);

  const value = useMemo(() => {
    const home = savedHome ? { label: savedHome.label, lat: savedHome.lat, lng: savedHome.lng, fromHome: true } : null;
    return {
      place: chosen ?? home,
      hasHome: Boolean(home),
      choosePlace,
      backToHome,
      pickerOpen,
      openPicker: () => setPickerOpen(true),
      closePicker: () => setPickerOpen(false),
    };
  }, [chosen, savedHome, choosePlace, backToHome, pickerOpen]);

  return <SearchPlaceContext.Provider value={value}>{children}</SearchPlaceContext.Provider>;
}
