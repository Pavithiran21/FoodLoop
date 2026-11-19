import { createContext, useContext } from 'react';

/**
 * Where a receiver is looking for food on this visit:
 * { place, hasHome, choosePlace(place), backToHome(), pickerOpen, openPicker(), closePicker() }.
 * place is { label, lat, lng, fromHome } or null (no saved home and nothing chosen yet). A place chosen in the
 * location picker lasts for this visit (sessionStorage); the saved home itself is changed in Profile.
 */
export const SearchPlaceContext = createContext(null);

export function useSearchPlace() {
  const value = useContext(SearchPlaceContext);
  if (!value) {
    throw new Error('useSearchPlace must be used inside <SearchPlaceProvider>');
  }
  return value;
}
