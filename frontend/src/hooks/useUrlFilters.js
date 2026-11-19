import { useCallback } from 'react';
import { useSearchParams } from 'react-router';

/**
 * Filters kept in the URL, so a reload or the Back button keeps them. Each change starts from the live URL
 * (window.location.search), not from the params of the last render, so two quick changes (a click and a key
 * press) can't overwrite each other (found in GigVault). The router runs without transitions (main.jsx), so a
 * checkbox bound to the URL changes the moment it is clicked.
 *
 * update({ key: value }) sets a value; null, undefined, '' or an empty array removes it. Any change but a page
 * change goes back to page 1.
 */
export function useUrlFilters() {
  const [searchParams, setSearchParams] = useSearchParams();

  const update = useCallback(
    (changes, { replace = true } = {}) => {
      const next = new URLSearchParams(window.location.search);
      Object.entries(changes).forEach(([key, value]) => {
        next.delete(key);
        if (Array.isArray(value)) {
          value.forEach((item) => next.append(key, item));
        } else if (value !== null && value !== undefined && value !== '') {
          next.set(key, String(value));
        }
      });
      if (!('page' in changes)) {
        next.delete('page');
      }
      setSearchParams(next, { replace });
    },
    [setSearchParams],
  );

  return [searchParams, update];
}

/** A 1-based page number from the URL (anything odd becomes 1). */
export function pageFrom(searchParams) {
  const page = Number(searchParams.get('page'));
  return Number.isInteger(page) && page > 0 ? page : 1;
}
