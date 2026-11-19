import { IconMapPin } from '@tabler/icons-react';
import { useState } from 'react';
import { searchPlaces } from '../api/places';
import { describedBy } from '../lib/fieldIds';
import { FieldError } from './Fields';

const NOT_FOUND = "Couldn't find that address. Try a nearby landmark or area name.";

/**
 * An address field with a "Find address" button: the text goes to GET /places/search (OpenStreetMap Nominatim,
 * through the backend) and the matches show as a listbox. Choosing one calls onSelect({ label, area, lat, lng });
 * typing again clears the choice (onSelect(null)), because the address must be found again to get its position.
 * Used by sign-up, the business profile, the receiver's home and the location picker.
 */
export function PlaceSearch({
  id,
  label,
  hint,
  error,
  selected,
  onSelect,
  onTextChange,
  buttonLabel = 'Find address',
  initialText = '',
}) {
  const [text, setText] = useState(selected?.label ?? initialText);
  const [results, setResults] = useState(null);
  const [searchError, setSearchError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function search() {
    const query = text.trim();
    if (query.length < 2) {
      setSearchError(NOT_FOUND);
      setResults(null);
      return;
    }
    setBusy(true);
    setSearchError(null);
    try {
      const places = await searchPlaces(query);
      setResults(places);
      if (places.length === 0) {
        setSearchError(NOT_FOUND);
      }
    } catch (problem) {
      setResults(null);
      setSearchError(problem.message);
    } finally {
      setBusy(false);
    }
  }

  function choose(place) {
    setText(place.label);
    setResults(null);
    onSelect(place);
  }

  const shownError = searchError ?? error;
  const listId = `${id}-results`;

  return (
    <div>
      <label className="flabel" htmlFor={id}>
        {label}
      </label>
      <div className="row-flex items-start! gap-2">
        <input
          id={id}
          className="inp"
          style={{ flex: '1 1 200px', width: 'auto' }}
          value={text}
          autoComplete="street-address"
          aria-invalid={shownError ? 'true' : undefined}
          aria-describedby={describedBy(id, { error: shownError, hint })}
          onChange={(event) => {
            setText(event.target.value);
            setSearchError(null);
            onTextChange?.(event.target.value);
            if (selected) {
              onSelect(null);
            }
          }}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              event.preventDefault();
              search();
            }
          }}
        />
        <button type="button" className="btn btn-soft btn-sm mt-[3px]" onClick={search} disabled={busy} aria-controls={results?.length ? listId : undefined}>
          {busy ? 'Finding…' : buttonLabel}
        </button>
      </div>
      <FieldError id={`${id}-error`} message={shownError} />
      {results && results.length > 0 && (
        <ul className="results-list" role="listbox" id={listId} aria-label="Matching places">
          {results.map((place) => (
            <li
              key={`${place.lat},${place.lng}`}
              role="option"
              aria-selected={selected?.lat === place.lat && selected?.lng === place.lng}
              tabIndex={0}
              onClick={() => choose(place)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault();
                  choose(place);
                }
              }}
            >
              <IconMapPin size={18} aria-hidden="true" />
              <span>
                <b>{place.label}</b>
                {place.area && <span className="muted xs block">{place.area}</span>}
              </span>
            </li>
          ))}
        </ul>
      )}
      {selected && !results && (
        <div className="hint flex items-center gap-1.5" role="status">
          <IconMapPin size={15} aria-hidden="true" color="var(--leaf-d)" /> Found on OpenStreetMap
          {selected.area ? `: ${selected.area}` : ''}
        </div>
      )}
      {hint && (
        <div className="hint" id={`${id}-hint`}>
          {hint}
        </div>
      )}
    </div>
  );
}
