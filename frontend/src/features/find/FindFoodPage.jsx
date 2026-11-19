import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { IconAdjustmentsHorizontal, IconList, IconMap, IconMapPin } from '@tabler/icons-react';
import { lazy, Suspense, useEffect, useState } from 'react';
import { findFood } from '../../api/listings';
import { keys } from '../../api/queryClient';
import { FoodPicture } from '../../components/FoodPicture';
import { Pager } from '../../components/Pager';
import { EmptyState, ErrorPanel, Skeleton } from '../../components/StatePanels';
import { Ticket } from '../../components/Ticket';
import { useDebouncedValue } from '../../hooks/useDebouncedValue';
import { useIsPhone } from '../../hooks/useMediaQuery';
import { useNow } from '../../hooks/useNow';
import { usePageTitle } from '../../hooks/usePageTitle';
import { pageFrom, useUrlFilters } from '../../hooks/useUrlFilters';
import { CATEGORIES, DIETARY_FILTERS, SORTS, WHEN } from '../../lib/labels';
import { shortPlace } from '../../lib/listingText';
import { browserZone } from '../../lib/time';
import { useSearchPlace } from '../../place/SearchPlaceContext';

// Leaflet is loaded only when someone opens the map.
const FoodMap = lazy(() => import('./FoodMap'));

const DEFAULT_KM = 5;
const MIN_KM = 1;
const MAX_KM = 10;

/** The filters as the URL holds them: ?km=5&cat=BAKERY&cat=COOKED_MEAL&diet=VEGETARIAN&when=TODAY&sort=NEWEST&page=2 */
function readFilters(searchParams) {
  const km = Number(searchParams.get('km'));
  return {
    km: Number.isInteger(km) && km >= MIN_KM && km <= MAX_KM ? km : DEFAULT_KM,
    categories: searchParams.getAll('cat'),
    dietary: searchParams.getAll('diet'),
    when: searchParams.get('when') ?? '',
    sort: searchParams.get('sort') ?? 'CLOSEST',
    page: pageFrom(searchParams),
  };
}

/** "Within 5 km of Indiranagar · bakery and cooked meals · vegetarian · any time" */
function summary(place, km, filters) {
  const parts = [`Within ${km} km of ${shortPlace(place.label, 1)}`];
  const lower = (text) => text.charAt(0).toLowerCase() + text.slice(1);
  const join = (labels) => (labels.length > 1 ? `${labels.slice(0, -1).join(', ')} and ${labels.at(-1)}` : labels[0]);
  const categories = CATEGORIES.filter((entry) => filters.categories.includes(entry.value)).map((entry) => lower(entry.label));
  if (categories.length) parts.push(join(categories));
  const dietary = DIETARY_FILTERS.filter((entry) => filters.dietary.includes(entry.value)).map((entry) => lower(entry.label));
  if (dietary.length) parts.push(dietary.join(', '));
  parts.push(lower(WHEN.find((entry) => entry.value === filters.when)?.label ?? 'Any time'));
  return parts.join(' · ');
}

function Filters({ filters, km, setKm, update, onClear }) {
  const toggle = (list, value) => (list.includes(value) ? list.filter((item) => item !== value) : [...list, value]);
  return (
    <aside className="filters" aria-label="Filters">
      <fieldset>
        <legend>Distance</legend>
        <label htmlFor="find-distance" className="sr-only">
          Distance in kilometres
        </label>
        <input
          type="range"
          id="find-distance"
          min={MIN_KM}
          max={MAX_KM}
          value={km}
          aria-valuetext={`${km} ${km === 1 ? 'kilometre' : 'kilometres'}`}
          onChange={(event) => setKm(Number(event.target.value))}
        />
        <div className="muted xs flex justify-between">
          <span>1 km</span>
          <b>Within {km} km</b>
          <span>10 km</span>
        </div>
      </fieldset>
      <fieldset>
        <legend>Category</legend>
        {CATEGORIES.map((category) => (
          <label key={category.value} className="chk">
            <input
              type="checkbox"
              checked={filters.categories.includes(category.value)}
              onChange={() => update({ cat: toggle(filters.categories, category.value) })}
            />{' '}
            {category.label}
          </label>
        ))}
      </fieldset>
      <fieldset>
        <legend>Dietary</legend>
        <div className="chips">
          {DIETARY_FILTERS.map((tag) => (
            <button
              key={tag.value}
              type="button"
              className="chip"
              aria-pressed={filters.dietary.includes(tag.value)}
              onClick={() => update({ diet: toggle(filters.dietary, tag.value) })}
            >
              {tag.label}
            </button>
          ))}
        </div>
      </fieldset>
      <fieldset>
        <legend>Pick up</legend>
        <div className="chips">
          {WHEN.map((option) => (
            <label key={option.value || 'any'} className="rchip">
              <input
                type="radio"
                name="find-when"
                checked={filters.when === option.value}
                onChange={() => update({ when: option.value })}
              />
              <span>{option.label}</span>
            </label>
          ))}
        </div>
      </fieldset>
      <button type="button" className="btn btn-text btn-sm" onClick={onClear}>
        Clear filters
      </button>
    </aside>
  );
}

/** Find food (/home, Receiver): listed food inside its pick-up window, around the search place. */
export function FindFoodPage() {
  usePageTitle('Find food');
  const now = useNow();
  const phone = useIsPhone();
  const { place, openPicker } = useSearchPlace();
  const [searchParams, update] = useUrlFilters();
  const filters = readFilters(searchParams);
  const [showFilters, setShowFilters] = useState(false);

  // The slider moves freely; the search (and the URL) follow once it has rested for a moment.
  const [km, setKm] = useState(filters.km);
  const debouncedKm = useDebouncedValue(km, 350);
  useEffect(() => {
    const inUrl = readFilters(new URLSearchParams(window.location.search)).km;
    if (debouncedKm !== inUrl) {
      update({ km: debouncedKm === DEFAULT_KM ? null : debouncedKm });
    }
  }, [debouncedKm, update]);

  const params = place
    ? {
        lat: place.lat,
        lng: place.lng,
        radiusKm: debouncedKm,
        category: filters.categories,
        dietary: filters.dietary,
        when: filters.when || undefined,
        timeZone: filters.when ? browserZone() : undefined,
        sort: filters.sort,
        page: filters.page,
        size: 20,
      }
    : null;

  const results = useQuery({
    queryKey: keys.findFood(params),
    queryFn: () => findFood(params),
    enabled: Boolean(place),
    placeholderData: keepPreviousData,
  });

  function clearFilters() {
    setKm(DEFAULT_KM);
    update({ km: null, cat: [], diet: [], when: null, sort: null });
  }

  if (!place) {
    return (
      <div className="card pad edge-tang mx-auto max-w-[640px]">
        <h1 className="text-[22px]">Choose where to look for food</h1>
        <p className="muted sm mt-1">You haven't saved a home location yet. Pick a place to see food near it.</p>
        <button type="button" className="btn btn-main btn-sm mt-3" onClick={openPicker}>
          <IconMapPin size={17} aria-hidden="true" /> Choose a location
        </button>
      </div>
    );
  }

  const data = results.data;
  const count = data?.totalItems ?? 0;
  // List or map, kept in the URL (?view=map) like the filters.
  const view = searchParams.get('view') === 'map' ? 'map' : 'list';

  return (
    <div className="browse">
      {(!phone || showFilters) && (
        <Filters filters={filters} km={km} setKm={setKm} update={update} onClear={clearFilters} />
      )}
      <section aria-labelledby="find-title" className="min-w-0">
        <div className="row-flex mb-3.5 items-end!">
          <div className="fill">
            <h1 id="find-title" className="text-[26px]" aria-live="polite">
              {results.isPending ? 'Finding food near you…' : `${count} ${count === 1 ? 'listing' : 'listings'} near you`}
            </h1>
            <div className="muted sm">{summary(place, km, filters)}</div>
          </div>
          {phone && (
            <button
              type="button"
              className="btn btn-line btn-sm"
              aria-expanded={showFilters}
              onClick={() => setShowFilters((value) => !value)}
            >
              <IconAdjustmentsHorizontal size={17} aria-hidden="true" /> Filters
            </button>
          )}
          <div className="chips" role="group" aria-label="Show results as">
            <button type="button" className="chip" aria-pressed={view === 'list'} onClick={() => update({ view: null })}>
              <IconList size={15} aria-hidden="true" /> List
            </button>
            <button type="button" className="chip" aria-pressed={view === 'map'} onClick={() => update({ view: 'map' })}>
              <IconMap size={15} aria-hidden="true" /> Map
            </button>
          </div>
          <div>
            <label htmlFor="find-sort" className="sr-only">
              Sort
            </label>
            <select id="find-sort" className="inp inp-sm" value={filters.sort} onChange={(event) => update({ sort: event.target.value })}>
              {SORTS.map((sort) => (
                <option key={sort.value} value={sort.value}>
                  {sort.label}
                </option>
              ))}
            </select>
          </div>
        </div>

        {results.isPending && (
          <div className="card pad">
            <Skeleton label="Finding food near you…" blocks={phone ? 1 : 2} lines={1} columns={phone ? 1 : 2} />
          </div>
        )}
        {results.isError && (
          <div className="card">
            <ErrorPanel error={results.error} title="Couldn't load food near you" onRetry={() => results.refetch()} />
          </div>
        )}
        {data && count === 0 && (
          <div className="card">
            <EmptyState art={<FoodPicture category="FRUIT" width={90} />} title="Nothing near you right now"
              action={
                km < MAX_KM ? (
                  <button type="button" className="btn btn-soft" onClick={() => setKm(MAX_KM)}>
                    Search within 10 km
                  </button>
                ) : (
                  <button type="button" className="btn btn-soft" onClick={clearFilters}>
                    Clear filters
                  </button>
                )
              }
            >
              Most food is listed between 5 and 9 pm. Try a wider distance or fewer filters.
            </EmptyState>
          </div>
        )}
        {data && count > 0 && view === 'map' && (
          <>
            <Suspense fallback={<div className="card pad"><Skeleton label="Loading the map…" blocks={1} lines={1} columns={1} /></div>}>
              <FoodMap place={place} km={debouncedKm} listings={data.items} />
            </Suspense>
            <Pager data={data} onPage={(page) => update({ page }, { replace: false })} className="pager border-t-0! px-0! pt-4!" />
          </>
        )}
        {data && count > 0 && view === 'list' && (
          <>
            <div className="tickets" aria-busy={results.isFetching}>
              {data.items.map((listing) => (
                <Ticket key={listing.id} listing={listing} now={now} />
              ))}
            </div>
            <Pager data={data} onPage={(page) => update({ page }, { replace: false })} className="pager border-t-0! px-0! pt-4!" />
          </>
        )}
      </section>
    </div>
  );
}
