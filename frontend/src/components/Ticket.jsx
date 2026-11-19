import { IconBuildingStore, IconClockExclamation, IconClockHour4, IconMapPin } from '@tabler/icons-react';
import { Link } from 'react-router';
import { ticketTag } from '../lib/labels';
import { stubColour } from '../lib/pictures';
import { bestBeforeText, distanceText, windowText } from '../lib/listingText';
import { endsSoon } from '../lib/time';
import { ListingPhoto } from './ListingPhoto';

/**
 * One Find food ticket: the photo or category picture on a coloured stub with a dashed perforation, then the
 * food, the donor, the distance, the pick-up window (tangerine when it ends within 3 hours) and the best-before
 * time, which the food-safety rule asks for on every listing.
 */
export function Ticket({ listing, now }) {
  const soon = endsSoon(listing.pickupFrom, listing.pickupUntil, now);
  return (
    <Link className="ticket" to={`/listings/${listing.id}`}>
      <div className="art" style={{ background: stubColour(listing.category) }}>
        <span className="qty">{listing.quantity}</span>
        <ListingPhoto
          listingId={listing.id}
          hasPhoto={listing.hasPhoto}
          category={listing.category}
          alt=""
          pictureWidth={90}
        />
      </div>
      <div className="body">
        <span className="tag s-listed">{ticketTag(listing.category, listing.dietary)}</span>
        <h3>{listing.title}</h3>
        <div className="meta">
          <span>
            <IconBuildingStore size={15} aria-hidden="true" /> {listing.donor.businessName}
          </span>
          {listing.distanceKm != null && (
            <span>
              <IconMapPin size={15} aria-hidden="true" /> {distanceText(listing.distanceKm)}
            </span>
          )}
        </div>
        <div className={`window ${soon ? 'hot' : ''}`}>
          <IconClockHour4 size={16} aria-hidden="true" />
          <span>
            {windowText(listing, now)}
            {soon ? ` · ${soon}` : ''}
          </span>
        </div>
        <div className="muted xs best">
          <IconClockExclamation size={15} aria-hidden="true" className="shrink-0" />
          <span>
            Best before <span className="whitespace-nowrap">{bestBeforeText(listing, now)}</span>
          </span>
        </div>
      </div>
    </Link>
  );
}
