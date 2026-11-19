import { IconHome, IconSearch } from '@tabler/icons-react';
import { useState } from 'react';
import { Dialog } from '../../components/Dialog';
import { FieldError } from '../../components/Fields';
import { PlaceSearch } from '../../components/PlaceSearch';
import { useSearchPlace } from '../../place/SearchPlaceContext';

/**
 * "Where should we look?": the saved home (only when there is one) or another address found on OpenStreetMap.
 * The chosen place is used for this visit; the home itself is saved in Profile.
 */
export function LocationPicker() {
  const { pickerOpen, closePicker } = useSearchPlace();
  return (
    <Dialog open={pickerOpen} onClose={closePicker} labelledBy="where-title">
      <PickerForm />
    </Dialog>
  );
}

function PickerForm() {
  const { place, hasHome, choosePlace, backToHome, closePicker } = useSearchPlace();
  const [from, setFrom] = useState(hasHome && (!place || place.fromHome) ? 'home' : 'other');
  const [found, setFound] = useState(null);
  const [problem, setProblem] = useState(null);

  function use() {
    if (from === 'home') {
      backToHome();
    } else if (found) {
      choosePlace(found);
    } else {
      setProblem('Choose a location to search near.');
      return;
    }
    closePicker();
  }

  return (
    <div>
      <h2 id="where-title" className="mb-3">
        Where should we look?
      </h2>
      {hasHome && (
        <fieldset className="plain-fieldset">
          <legend className="sr-only">Search near</legend>
          <div className="chips mb-3.5">
            <label className="rchip">
              <input type="radio" name="where-from" checked={from === 'home'} onChange={() => setFrom('home')} />
              <span>
                <IconHome size={15} aria-hidden="true" /> My home location
              </span>
            </label>
            <label className="rchip">
              <input type="radio" name="where-from" checked={from === 'other'} onChange={() => setFrom('other')} />
              <span>
                <IconSearch size={15} aria-hidden="true" /> Another address
              </span>
            </label>
          </div>
        </fieldset>
      )}
      {from === 'other' ? (
        <PlaceSearch
          id="where-search"
          label="Address or area"
          buttonLabel="Search"
          selected={found}
          initialText={place && !place.fromHome ? place.label : ''}
          onSelect={(next) => {
            setFound(next);
            setProblem(null);
          }}
        />
      ) : (
        <p className="sm mb-3.5">
          Find food near <b>{place?.fromHome ? place.label : 'your home'}</b>.
        </p>
      )}
      <FieldError id="where-error" message={problem} />
      <div className="row-flex justify-end">
        <button type="button" className="btn btn-text btn-sm" onClick={closePicker} data-autofocus>
          Cancel
        </button>
        <button type="button" className="btn btn-main btn-sm" onClick={use}>
          Use this place
        </button>
      </div>
      <p className="muted xs mt-2.5">
        Address search by OpenStreetMap (Nominatim).{' '}
        {hasHome ? 'Your home location is set in Profile.' : 'Without a saved home, only another address can be used; a home is set in Profile.'}
      </p>
    </div>
  );
}
