import 'leaflet/dist/leaflet.css';
import { Link } from 'react-router';
import { Circle, CircleMarker, MapContainer, Popup, TileLayer, Tooltip } from 'react-leaflet';
import { distanceText } from '../../lib/listingText';

// The Find food results on an OpenStreetMap map (loaded only when Map is chosen, so Leaflet stays out
// of the main bundle). Each listing sits at its approximate spot: the API rounds it to about 1 km, so
// the map shows the neighbourhood, never the donor's door; the full address comes only with a claim.
// Listings that round to the same spot share one marker with their count, and its popup lists them all,
// so no listing hides under another. Circle markers are SVG, so no marker images are needed. Tiles follow
// the OSM tile usage policy: attribution shown, the standard tile URL, no bulk loading.

const LEAF = '#3d8b4f';
const TANG = '#f2862e';

/** A zoom that fits the search circle in the map: 1 km -> 15, 5 km -> 13, 10 km -> 12. */
function zoomFor(km) {
  return Math.max(10, Math.min(16, Math.round(15 - Math.log2(km))));
}

/** One entry per approximate spot: { key, lat, lng, listings }. */
function bySpot(listings) {
  const spots = new Map();
  for (const listing of listings) {
    const key = `${listing.approxLat},${listing.approxLng}`;
    if (!spots.has(key)) spots.set(key, { key, lat: listing.approxLat, lng: listing.approxLng, listings: [] });
    spots.get(key).listings.push(listing);
  }
  return [...spots.values()];
}

export default function FoodMap({ place, km, listings }) {
  return (
    <div className="card overflow-hidden" role="region" aria-label="Map of food near you">
      <MapContainer
        key={`${place.lat},${place.lng},${km}`}
        center={[place.lat, place.lng]}
        zoom={zoomFor(km)}
        scrollWheelZoom={false}
        style={{ height: 520, width: '100%' }}
      >
        <TileLayer
          url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          maxZoom={18}
        />
        <Circle center={[place.lat, place.lng]} radius={km * 1000} pathOptions={{ color: LEAF, weight: 1.5, fillOpacity: 0.05, className: 'search-area' }} />
        {bySpot(listings).map((spot) => (
          <CircleMarker
            key={spot.key}
            center={[spot.lat, spot.lng]}
            radius={spot.listings.length > 1 ? 14 : 10}
            pathOptions={{ color: '#ffffff', weight: 2, fillColor: TANG, fillOpacity: 0.95, className: 'food-marker' }}
          >
            {spot.listings.length > 1 && (
              <Tooltip permanent direction="center" className="food-count" opacity={1}>
                {spot.listings.length}
              </Tooltip>
            )}
            <Popup>
              <ul className="food-popup">
                {spot.listings.map((listing) => (
                  <li key={listing.id}>
                    <Link to={`/listings/${listing.id}`} className="font-semibold">
                      {listing.title}
                    </Link>
                    <div className="muted xs">
                      {listing.quantity} · {listing.donor.businessName}
                      {listing.distanceKm != null ? ` · ${distanceText(listing.distanceKm)}` : ''}
                    </div>
                  </li>
                ))}
              </ul>
            </Popup>
          </CircleMarker>
        ))}
      </MapContainer>
      <p className="muted xs px-4 py-2.5">Spots are approximate (about 1 km). You get the exact pick-up address once your claim is made.</p>
    </div>
  );
}
